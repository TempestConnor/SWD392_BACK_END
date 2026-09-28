# Chess website — feature analysis

The business rules are the source of product behavior. The SQL Server schema is an implementation starting point; chess legality, connection handling, and exactly-once ratings also need application logic and transactions. Each feature below is a vertical slice across data access, Spring Boot, React, validation, and verification. PvP and CPU play precede puzzles.

## A. Feature List

| ID | Feature | Scope |
|---|---|---|
| F01 | Register and sign in | Identity, authentication, starting rating |
| F02 | Play local hot-seat chess | Browser-based guest or account game |
| F03 | Play against CPU | Easy, Medium, Hard; local game state |
| F04 | Create and claim invitation | Link, expiry, clock settings, atomic claim |
| F05 | Join and leave matchmaking | Rated classical queue |
| F06 | Pair matchmaking players | Rating gap and atomic pairing |
| F07 | Ready and start online game | Ready deadline and pre-start cancellation |
| F08 | Make online move | Legal move and durable state transition |
| F09 | Apply clocks and chess outcomes | Timeout and engine-detected endings |
| F10 | Resign or mutually abandon | Explicit game endings |
| F11 | Reconnect or forfeit | Active-game three-minute grace |
| F12 | Apply Elo ratings | Atomic, idempotent rating events |
| F13 | View match history | Started online games and results |
| F14 | Manage puzzles | Admin creation and publication |
| F15 | Solve puzzles | Account and guest completion |
| F16 | Import guest completions | Idempotent merge after registration |

## B. Feature Specifications

### F01 — Register and sign in

1. **Purpose:** Give online players stable identities and initial classical ratings.
2. **Rules:** BR-ACC-001, 003–024; BR-RATE-001–005; BR-HIST-002, 004–005, 007.
3. **Functional requirements:** The system must register an email and password, store a password hash, collect an immutable display name and skill choice, and initialize a rating for every enabled mode. It must verify credentials before issuing a JWT. The ADMIN role exists without a database restriction to one account. Account deletion and display-name editing are not launch features.
4. **Inputs → outputs:** Email, password, name, skill → Account, PlayerRating, authenticated token.
5. **Decision:** IF an email conflicts regardless of case or a name conflicts with case sensitivity, THEN reject; ELSE create account and ratings atomically.
6. **Validation:** Beginner/400, Intermediate/800, Advanced/1200, Expert/1600. Schema permits names of 3–40 trimmed characters and emails up to 320 characters. **Needs Clarification:** Password policy, email validation, and whitespace normalization of names.
7. **Edges:** Concurrent duplicate signups, partial rating creation, repeated registration, `Alex` versus `alex`, invalid token or missing account.
8. **Acceptance:** Given a new Intermediate signup, when completed, then classical rating is 800. Given `Alex` exists, `alex` may register with a distinct email. Given an existing email in different case, registration fails.
9. **Implementation:** Account, AccountRole, RatingMode, PlayerRating; signup/signin endpoints; hashing, JWT verification, transactional account and rating creation.

### F02 — Play local hot-seat chess

1. **Purpose:** Let two people play on one browser without accounts.
2. **Rules:** BR-ACC-002; BR-GAME-001–005; BR-MOVE-001, 010–012.
3. **Functional requirements:** Alternate White and Black, validate standard chess, provide the default 30+10 clock and configurable clock, and restore active state from browser storage. Do not create SQL game history or rating events.
4. **Inputs → outputs:** Settings and moves → local position, clocks, result.
5. **Decision:** IF a move is legal for the side to move, THEN update local state; ELSE preserve it and show an error.
6. **Validation:** Enabled clock: 5–120 minutes and 0–60 seconds increment; disabled clock has no increment. Standard variant only.
7. **Edges:** Refresh, cleared/corrupt storage, repeated move, promotion, terminal position. **Needs Clarification:** Whether time continues while browser is closed.
8. **Acceptance:** Given an active stored game, refresh restores it. Given an illegal move, position stays unchanged. A finished local game never appears in online history.
9. **Implementation:** Chess library, browser storage, local board and clock UI; no launch SQL persistence.

### F03 — Play against CPU

1. **Purpose:** Provide unrated solo chess for guests and accounts.
2. **Rules:** BR-ACC-002; BR-GAME-001–005; BR-MOVE-001, 003, 010–012.
3. **Functional requirements:** Choose Easy, Medium, or Hard; validate player and CPU moves; configure clock; resume from browser storage. CPU games do not affect Elo or permanent history.
4. **Inputs → outputs:** Difficulty, clock, moves → CPU moves, position, clocks, result.
5. **Decision:** IF a valid player move leaves an active game, THEN calculate a CPU move; ELSE do not advance CPU.
6. **Validation:** Three stated difficulties; standard chess; F02 clock ranges.
7. **Edges:** Refresh during calculation, stale asynchronous CPU response, promotion, missing local state. **Needs Clarification:** Player color and whether CPU calculation runs in browser or backend.
8. **Acceptance:** Every difficulty produces legal CPU moves; refresh restores intact local state; completion creates no OnlineGame or RatingEvent.
9. **Implementation:** Assess free engine/library maintenance and license; browser state; reject stale CPU responses.

### F04 — Create and claim invitation

1. **Purpose:** Allow a registered host to share an online game link.
2. **Rules:** BR-INV-001–008, 011; BR-GAME-006–010; BR-MOVE-010–012; BR-HIST-007–008.
3. **Functional requirements:** Generate a link expiring in 15 minutes. Another registered player can claim the single seat; the host cannot claim it. Host may cancel before claim. Successful claim makes one pending game and opens the board for both.
4. **Inputs → outputs:** Host/settings → link; token/joiner → pending game or full/expired/cancelled error.
5. **Decision:** IF invitation is open, unexpired, and joiner differs from host, THEN atomically claim and create game; ELSE reject with applicable reason.
6. **Validation:** Standard chess, 30+10 default, enabled clock 5–120 minutes and 0–60 seconds, or disabled. Effective default 30+10 is rated; changed/disabled clock is unrated.
7. **Edges:** Concurrent claims, exact expiry boundary, claim/cancel race, malformed token, retry.
8. **Acceptance:** Two simultaneous claims yield exactly one joiner. Expired token returns expiration. Changed clock produces unrated game.
9. **Implementation:** Invitation, OnlineGame; unpredictable token stored as hash; transaction and row lock or compare-and-set. **Needs Clarification:** Color assignment and whether expiry is materialized by a worker.

### F05 — Join and leave matchmaking

1. **Purpose:** Request a rated classical opponent.
2. **Rules:** BR-RATE-011, 013–019; BR-MOVE-013–014; BR-HIST-001, 007.
3. **Functional requirements:** One active queue request per account; capture rating on entry, update heartbeat, allow immediate cancellation, and close disconnected entries after configured grace.
4. **Inputs → outputs:** Account, mode/variant, join/cancel/heartbeat → active or closed QueueRequest.
5. **Decision:** IF already queued, THEN do not create another entry; ELSE create one. IF heartbeat grace expires, THEN close it as timed out.
6. **Validation:** Authenticated account, enabled CLASSICAL/STANDARD at launch, fixed 30+10 clock.
7. **Edges:** Duplicate join/cancel, cancellation racing pairing, late heartbeat, rating changed after entry, multiple tabs.
8. **Acceptance:** Repeated join leaves one active request; cancel removes it immediately; stale request becomes unavailable after grace.
9. **Implementation:** QueueRequest, PlayerRating, MatchmakingConfig; filtered unique index, endpoints and expiration worker. Configure thresholds before enabling.

### F06 — Pair matchmaking players

1. **Purpose:** Form exactly one rated game from compatible queue entries.
2. **Rules:** BR-RATE-013–017, 020–021; BR-GAME-006–008; BR-INV-012, 017; BR-HIST-007–008.
3. **Functional requirements:** Match same enabled mode/variant using configured widening rating gap up to maximum; assign opposite colors; consume both requests and create a pending rated game atomically.
4. **Inputs → outputs:** Eligible entries, entry ratings, wait times, configuration → one pending game and two MATCHED requests.
5. **Decision:** IF both requests remain open and satisfy current gap, THEN consume both and create one game in a transaction; ELSE leave them queued.
6. **Validation:** Classical/standard; configured gap, step, interval, maximum, heartbeat values.
7. **Edges:** Competing workers, concurrent cancellation, stale heartbeat, gap boundary, retry.
8. **Acceptance:** Compatible entries yield one game referencing distinct accounts; neither can match twice; differences above maximum never match.
9. **Implementation:** QueueRequest, MatchmakingConfig, OnlineGame; transactional locks and real-time pairing notice. **Needs Clarification:** Exact thresholds and color policy.

### F07 — Ready and start online game

1. **Purpose:** Start only when both players are present.
2. **Rules:** BR-INV-008–010, 012–019; BR-GAME-021; BR-HIST-001, 003.
3. **Functional requirements:** Present pending board; record each participant's Ready; start once if both ready within three minutes of claim/pairing. Either can explicitly cancel before start. Detected pre-start connection loss and deadline expiry cancel without result, Elo, or history. Fix participants/settings on start.
4. **Inputs → outputs:** Ready/cancel/connection events and deadline → ACTIVE game or CANCELLED pending game.
5. **Decision:** IF both ready before cancellation/deadline, THEN start once; ELSE remain pending or cancel under the applicable rule.
6. **Validation:** Actions only by participants; three-minute deadline.
7. **Edges:** Duplicate Ready, second Ready racing Cancel, browser close without clean notification, deadline race.
8. **Acceptance:** Two timely Ready actions start once. Cancellation before start does not enter history. One Ready at deadline causes cancellation.
9. **Implementation:** OnlineGame readiness/status; heartbeat monitor and deadline worker; transactional state transition and real-time UI. Missed-heartbeat interval is a configuration choice.

### F08 — Make online move

1. **Purpose:** Maintain one authoritative and recoverable game position.
2. **Rules:** BR-MOVE-001–009; BR-GAME-011; BR-HIST-003, 007–008.
3. **Functional requirements:** Spring Boot validates moves through chess library. An accepted move updates move row, position, sequence, clocks, and status consistently. Reject illegal, duplicate, stale, out-of-turn, and post-game moves. Refresh reloads persisted state.
4. **Inputs → outputs:** Game, authenticated player, move, expected sequence → accepted move and state or unchanged rejection.
5. **Decision:** IF game ACTIVE, caller is side to move, sequence current, clock valid, and move legal, THEN persist once; ELSE reject.
6. **Validation:** Standard chess and promotion; unique game/ply; player is participant; store UCI, optional SAN, resulting FEN.
7. **Edges:** Rapid double request, retry after timeout, stale client, move versus timeout race, checkmating move.
8. **Acceptance:** Illegal move changes no rows; two requests at same ply accept at most one; refresh returns authoritative FEN and move order.
9. **Implementation:** OnlineGame and GameMove, rowversion/locks, transaction, server-side chess library and license review.

### F09 — Apply clocks and chess outcomes

1. **Purpose:** Finish games consistently for time and chess rules.
2. **Rules:** BR-GAME-013–014; BR-MOVE-001, 005–006, 010–016; BR-HIST-003.
3. **Functional requirements:** Track server-authoritative remaining time and turn start, apply increment after accepted move, detect timeout, and use chess library for applicable checkmate/draw. Store result and compatible termination reason once. Clock-disabled games cannot time out.
4. **Inputs → outputs:** Position, clock state, authoritative time, move/timer event → clocks or final result.
5. **Decision:** IF terminal condition or valid timeout occurs in ACTIVE game, THEN complete once; ELSE continue.
6. **Validation:** Matchmaking 1800+10; invite ranges; outcome WHITE_WIN, BLACK_WIN, DRAW; schema-compatible reasons.
7. **Edges:** Move near zero, concurrent timer, server restart, disabled clock. **Needs Clarification:** Draw claims and timeout when opponent has insufficient mating material.
8. **Acceptance:** Valid expiration produces one TIMEOUT result; checkmating move stores CHECKMATE; no-clock game does not time out.
9. **Implementation:** OnlineGame and GameMove clock snapshots; server time, due-time worker, shared terminal transition.

### F10 — Resign or mutually abandon

1. **Purpose:** Provide explicit player-driven endings.
2. **Rules:** BR-GAME-013–014, 018–020; BR-RATE-008–009; BR-HIST-003.
3. **Functional requirements:** Active player may resign for loss. Either may vote to abandon; both votes yield DRAW/MUTUAL_ABANDONMENT. Both endings remain in history; rating rules depend on rated flag and draw outcome.
4. **Inputs → outputs:** Participant/game and action → pending vote or completed game.
5. **Decision:** IF resign, THEN complete as caller loss. IF both votes exist while ACTIVE, THEN complete as draw; ELSE retain first vote.
6. **Validation:** Participant only; ACTIVE only; one vote per player/game.
7. **Edges:** Duplicate vote, simultaneous votes, resignation racing vote/move, late action. **Needs Clarification:** Vote withdrawal and expiry.
8. **Acceptance:** One vote leaves game active; second makes one draw; White resignation yields Black win and RESIGNATION.
9. **Implementation:** GameAbandonVote and OnlineGame, authenticated endpoints, transactional finalization.

### F11 — Reconnect or forfeit

1. **Purpose:** Survive brief active-game disconnects.
2. **Rules:** BR-GAME-015–017, 020; BR-INV-019; BR-CONFIG-002; BR-HIST-001, 003.
3. **Functional requirements:** Record active participant disconnect, accept reconnect within three minutes, forfeit if still disconnected at expiry. Applies to invite games without clock and to rated games. Terminal result remains in history.
4. **Inputs → outputs:** Connection/heartbeat and time → connection state, restored game, or DISCONNECT result.
5. **Decision:** IF ACTIVE and player remains disconnected after three minutes, THEN complete as their loss; ELSE allow eligible reconnect.
6. **Validation:** Same account as participant; separate queue and pending-game policies.
7. **Edges:** Reconnect at deadline, multiple tabs, both disconnected, clock timeout during grace, stale heartbeat, worker race. **Needs Clarification:** Outcome of dual disconnect and priority over timeout.
8. **Acceptance:** Timely reconnect preserves game; one player still absent after grace loses; pre-start loss cancels instead of forfeiting.
9. **Implementation:** OnlineGame disconnect/last-seen fields, monitor, deadline worker, atomic terminal transition.

### F12 — Apply Elo ratings

1. **Purpose:** Apply and preserve exactly-once mode ratings.
2. **Rules:** BR-ACC-007, 010–016, 019; BR-RATE-001–012, 022; BR-HIST-003, 007–008.
3. **Functional requirements:** Completed rated game updates both current mode ratings once using simple Elo and configured K (initially 20). Store each player's before, delta, after, mode, game and K. Every draw has zero delta. Unrated/cancelled games create no rating events.
4. **Inputs → outputs:** Completed rated game, current ratings, K → two updated PlayerRating and two RatingEvent rows.
5. **Decision:** IF completed/rated and not already applied, THEN atomically apply both; ELSE do nothing further.
6. **Validation:** Both player/mode ratings exist; one event per game/player; before + delta = after.
7. **Edges:** Duplicate finalization, two simultaneous games for one player, partial failure, future K change. **Needs Clarification:** Elo rounding and rating floor.
8. **Acceptance:** Retrying a rated win changes each rating once; rated draw produces two zero-delta events; unrated invite produces none.
9. **Implementation:** PlayerRating, RatingEvent, RatingMode, OnlineGame; lock/serialize overlapping player updates; store K used.

### F13 — View match history

1. **Purpose:** Show durable records of started online games.
2. **Rules:** BR-ACC-017–019; BR-GAME-011, 013, 020–021; BR-INV-018; BR-MOVE-004, 007, 016; BR-HIST-003–006.
3. **Functional requirements:** Show participant's started online games, including unrated invites and disconnect/abandonment endings, with current display names, effective settings, moves, perspective-specific result, and available rating events. Exclude pending/cancelled pre-start, CPU and local games. Replay UI is out of scope.
4. **Inputs → outputs:** Authenticated player and query → their history and detail.
5. **Decision:** IF game started and caller participates, THEN it is eligible; ELSE omit.
6. **Validation:** Participant access only; use stored effective settings and historical rating data.
7. **Edges:** Active game in list, unrated game without events, pagination, cancelled SQL row. **Needs Clarification:** Active games in history or separate resume view.
8. **Acceptance:** Cancelled game absent; completed unrated invite shown without rating delta; forfeit shows perspective-specific win/loss and reason.
9. **Implementation:** OnlineGame, GameMove, RatingEvent, Account; indexed queries, authorized detail endpoints.

### F14 — Manage puzzles

1. **Purpose:** Let an admin curate puzzles after PvP and CPU delivery.
2. **Rules:** BR-PUZ-001–002, 009–010; BR-ACC-021; BR-HIST-001, 006.
3. **Functional requirements:** Admin creates/publishes initial position, solution, difficulty, theme; unpublish/soft delete hides discovery but retains completions.
4. **Inputs → outputs:** Admin and content/publication action → Puzzle state.
5. **Decision:** IF published and not deleted, THEN discoverable; ELSE hidden.
6. **Validation:** ADMIN role; EASY/MEDIUM/HARD; nonempty solution/theme; valid chess position and solution.
7. **Edges:** Invalid FEN/solution, unpublish with completions, repeat publication, deleted referenced puzzle.
8. **Acceptance:** Published puzzle discoverable; unpublish hides without deleting completions; non-admin publication denied.
9. **Implementation:** Puzzle, AccountRole; admin endpoints, chess validation, filtered discovery.

### F15 — Solve puzzles and save completion

1. **Purpose:** Support multi-move puzzles for guests and accounts.
2. **Rules:** BR-ACC-002; BR-PUZ-001–004, 007–009; BR-HIST-003, 007.
3. **Functional requirements:** Show eligible puzzle, check moves in sequence; signed-in completion stored once, guest completion in browser storage. No attempts, solve time, hints, or puzzle Elo persistence.
4. **Inputs → outputs:** Puzzle ID, moves, optional account → progress and completion.
5. **Decision:** IF complete correct sequence, THEN record completion once; ELSE do not record.
6. **Validation:** Existing eligible puzzle, correct sequence, unique account/puzzle completion.
7. **Edges:** Duplicate solve, unpublish mid-solve, cleared storage, multi-move mismatch. **Needs Clarification:** Server proof required for signed-in completion.
8. **Acceptance:** Partial solution creates no completion; full solution creates one; guest solve remains local until import.
9. **Implementation:** Puzzle, PuzzleCompletion, browser storage, chess library, puzzle UI.

### F16 — Import guest puzzle completions

1. **Purpose:** Transfer guest progress after registration.
2. **Rules:** BR-PUZ-004–008; BR-HIST-007–008.
3. **Functional requirements:** Browser submits saved puzzle IDs; server verifies existence/eligibility and merges into account completions idempotently. Strong offline proof is not required.
4. **Inputs → outputs:** Authenticated account and ID list → accepted completions and invalid-ID results.
5. **Decision:** IF ID exists, is eligible, and completion absent, THEN insert GUEST_IMPORT; ELSE preserve existing completion or reject ID.
6. **Validation:** Authenticated account, valid IDs, one row per account/puzzle.
7. **Edges:** Duplicate IDs, repeated import, unpublish before import, partially invalid batch, cleared local storage. **Needs Clarification:** Eligibility at import and partial batch success.
8. **Acceptance:** Repeated IDs yield one row; unknown ID creates none; existing completion remains intact.
9. **Implementation:** PuzzleCompletion, Puzzle; transactional merge/upsert with unique-key handling.

## C. Cross-Feature Rules

| Rule | Consequence |
|---|---|
| Standard chess only at launch | Reject other variants, while retaining variant fields for future expansion. |
| Registered identity for online PvP | Authenticate and authorize every online game action. |
| CPU/local browser state | Refresh recovery depends on local data; no SQL history or Elo. |
| Pre-start versus started lifecycle | Before Ready/start, cancel without result/history/Elo; after start, use game outcomes and disconnect policy. |
| Historical settings | Persist effective mode, variant, clock and rated flag on each started game. |
| Draw rating policy | Every rated draw records zero delta for both players; unrated draws create no events. |
| Historical integrity | Preserve moves, final results, rating events and completions through routine changes. |
| Distinct heartbeat policies | Queue grace, pending loss detection, and active-game three-minute grace are separate. |

The SQL schema supports these slices with Account, PlayerRating, OnlineGame, GameMove, Invitation, QueueRequest, RatingEvent, Puzzle, PuzzleCompletion and GameAbandonVote. Service logic must still enforce relationships not proven by foreign keys: move/vote actor is a participant; rating event player and mode match the game; paired requests belong to the game; move sequence, position, clocks and status agree. MatchmakingConfig has no seeded thresholds until those values are chosen.

## D. Missing / Ambiguous Requirements

| Area | Question before implementation |
|---|---|
| Signup | Password policy, email format/normalization, and display-name whitespace normalization? |
| Colors | How are colors assigned for invite, matchmade, CPU, and local games? |
| Local clocks | Do they run while the browser is closed? |
| CPU | Browser or backend computation? How do three levels map to engine settings? |
| Matchmaking | Initial gap, widening step/interval, maximum gap, heartbeat and queue grace values? |
| Pending connection | Missed-heartbeat interval for detected pre-start loss? |
| Disconnect conflicts | Both players disconnected; reconnect exactly at deadline; timeout during grace? |
| Chess outcomes | Which repetition/move-count outcomes require claims? Timeout with insufficient mating material? |
| Abandonment | Can a vote be withdrawn or expire? |
| Elo | Rounding convention and whether to clamp at zero? |
| History | Are active games listed there or only in a separate resume view? |
| Puzzles | Import eligibility after unpublish, signed-in solve verification, and partial batch success? |

## E. Suggested Implementation Order

| Group | Ordered slices |
|---|---|
| Foundation/domain model | 1 F01 Registration; 2 F02 Local chess; 3 F03 CPU chess. Assess library licenses while building these slices. |
| Core business logic | 4 F04 Invitation; 5 F07 Ready/start for invites; 6 F08 Moves; 7 F09 Clocks/outcomes; 8 F10 Resign/abandon; 9 F11 Reconnect/forfeit; 10 F12 Elo; 11 F13 History. This completes invite PvP first. |
| Supporting features | 12 F05 Queue; 13 F06 Pairing. Reuse the established online game path. |
| Integrations | Integrate chess/CPU libraries, real-time events and connection monitoring within their corresponding vertical slices rather than as detached implementation phases. |
| Operational/admin features | 14 F14 Puzzle management; 15 F15 Puzzle solving; 16 F16 Guest import. Set queue and monitoring configuration before enabling their features. |

Replay UI, spectator mode, email verification, password reset, bans, and account deletion are outside the stated launch scope.
