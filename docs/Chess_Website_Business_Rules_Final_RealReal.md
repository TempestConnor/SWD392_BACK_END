# Online Chess Website — Consolidated Business Rules

**Purpose:** Requirements for a future Microsoft SQL Server database design. Backend: Java Spring Boot. Frontend: React. Use freely available chess libraries or repositories subject to their licenses. This document states product behavior, not a SQL schema.

## 1. Accounts and identity

- **BR-ACC-001** — Registered accounts are required for online invite and matchmade games.
- **BR-ACC-002** — Guests may play CPU and local hot-seat games and solve puzzles.
- **BR-ACC-003** — Registration uses email and password.
- **BR-ACC-004** — Store secure password hashes, never plaintext.
- **BR-ACC-005** — JWT authenticates requests but does not replace account records or password verification.
- **BR-ACC-006** — Email addresses are unique without regard to case.
- **BR-ACC-007** — After choosing a display name, a player chooses a skill level that determines their starting rating.
- **BR-ACC-008** — Display names are unique **case-sensitively** (`Alex` and `alex` are different names).
- **BR-ACC-009** — A player cannot change their display name, so name reuse after a change does not arise under launch rules.
- **BR-ACC-010** — The signup skill choices mirror Chess.com's stated starting-rating choices: Beginner 400, Intermediate 800, Advanced 1200, Expert 1600.
- **BR-ACC-011** — Store the chosen starting rating for each enabled rating mode.
- **BR-ACC-012** — The site uses **simple Elo**, not Glicko.
- **BR-ACC-013** — The launch Elo K-factor is **20** for all players from their first rated game.
- **BR-ACC-014** — There is no provisional-player period.
- **BR-ACC-015** — Keep the K-factor configurable for future changes.
- **BR-ACC-016** — Record the value used for each historical rating application.
- **BR-ACC-017** — Game history displays the player's display name through their stable account identity.
- **BR-ACC-018** — Display names cannot change under launch rules.
- **BR-ACC-019** — Historical games retain stable player identities and historical rating values.
- **BR-ACC-020** — Players cannot delete their accounts under launch rules.
- **BR-ACC-021** — An Admin role exists; one account may have it at launch, but the role is not structurally restricted to one account.
- **BR-ACC-022** — Email verification, password reset, and account suspension/bans are not required for launch.
- **BR-ACC-023** — Account design should allow them to be added later.
- **BR-ACC-024** — Ordinary creation/update metadata suffices; a detailed administrative audit trail is not required at launch.

## 2. Game types and persistence

- **BR-GAME-001** — Support standard chess against Easy, Medium, or Hard CPU, local hot-seat chess, and online human-versus-human games created by invitation or matchmaking.
- **BR-GAME-002** — Additional variants may be enabled later; only standard chess is playable at launch.
- **BR-GAME-003** — CPU and local games are unrated and do not enter permanent match history.
- **BR-GAME-004** — Keep their active state in browser storage so a refresh can resume the game; cleared browser data or switching devices can lose it.
- **BR-GAME-005** — No SQL persistence for these games is required at launch.
- **BR-GAME-006** — Online games involve exactly two distinct registered players.
- **BR-GAME-007** — Assign one participant White and the other Black.
- **BR-GAME-008** — Matchmade games are rated.
- **BR-GAME-009** — Invite games using the default 30+10 clock are rated.
- **BR-GAME-010** — Changing either clock setting or disabling the clock makes the invite game unrated.
- **BR-GAME-011** — Started online games retain permanent moves and final results, including unrated invite games.
- **BR-GAME-012** — Spectator mode is not required.
- **BR-GAME-013** — The player-facing result is win, loss, or draw from each participant's perspective.
- **BR-GAME-014** — Record the game-level outcome and a separate termination reason, including checkmate, resignation, timeout, stalemate, repetition, move-count rule, insufficient material, agreement, disconnect, and mutual abandonment as applicable.
- **BR-GAME-015** — After a game starts, a disconnected player has a **three-minute reconnection grace period**.
- **BR-GAME-016** — If still disconnected when it ends, that player forfeits: they lose and the opponent wins.
- **BR-GAME-017** — This also applies to invite games with the clock disabled; those games are unrated.
- **BR-GAME-018** — Either player may propose abandoning a game.
- **BR-GAME-019** — If both agree, the game ends in a draw.
- **BR-GAME-020** — These terminal games remain in match history.
- **BR-GAME-021** — A game waiting for both players to become ready is not completed and has no win/loss/draw result.

## 3. Moves and clocks

- **BR-MOVE-001** — A chess engine/library in Spring Boot validates moves and chess outcomes.
- **BR-MOVE-002** — SQL Server records accepted moves and results.
- **BR-MOVE-003** — Assess the chosen library's maintenance and license before adoption.
- **BR-MOVE-004** — Persist each accepted online move in order, with a move representation and resulting position sufficient for recovery and eventual replay.
- **BR-MOVE-005** — Persist active position and both remaining clocks.
- **BR-MOVE-006** — Resulting FEN per move is an acceptable position snapshot; the application determines legality.
- **BR-MOVE-007** — Replay UI is outside launch scope.
- **BR-MOVE-008** — Accept a move at most once at a given game/sequence position.
- **BR-MOVE-009** — Server processing prevents duplicate or out-of-turn submissions and updates move history, position, clocks, and status consistently.
- **BR-MOVE-010** — CPU, local, and invite games default to **30 minutes initial time plus 10 seconds increment** per move.
- **BR-MOVE-011** — Their creator may configure the clock: initial time **5–120 minutes** and increment **0–60 seconds**, or disable the clock.
- **BR-MOVE-012** — When the clock is disabled, increment does not apply.
- **BR-MOVE-013** — Matchmade games use the predefined control for their queue and cannot customize it.
- **BR-MOVE-014** — Classical is the only enabled matchmaking mode at launch and always uses an enabled **30 minutes + 10 seconds** clock.
- **BR-MOVE-015** — Other modes may be added later.
- **BR-MOVE-016** — Preserve the effective clock settings, mode, and variant on each historical online game even when defaults/configuration later change.

## 4. Invitation and start flow

- **BR-INV-001** — A registered host creates an invite link.
- **BR-INV-002** — An unclaimed link expires **15 minutes** after creation.
- **BR-INV-003** — Any other registered player with a valid, unexpired link may claim the open seat.
- **BR-INV-004** — The host cannot join their own game.
- **BR-INV-005** — Claiming is atomic: exactly one joiner gets the seat.
- **BR-INV-006** — A filled link yields a clear “game already full” response.
- **BR-INV-007** — An expired link yields an expiration response.
- **BR-INV-008** — Claiming brings both participants to the board view.
- **BR-INV-009** — The game starts only after **both players press Ready**.
- **BR-INV-010** — The two participants and effective time control are fixed when the game starts.
- **BR-INV-011** — The host may cancel an unclaimed invitation.
- **BR-INV-012** — After a joiner claims an invitation, or matchmaking pairs two players, either participant may cancel while waiting at the Ready screen.
- **BR-INV-013** — Before both press Ready, leaving/closing the browser or a detected connection loss also cancels the pending game.
- **BR-INV-014** — An explicit Cancel acts immediately.
- **BR-INV-015** — Because a browser may close without notifying the server, use connection/heartbeat monitoring to detect an unannounced loss.
- **BR-INV-016** — The exact missed-heartbeat detection interval remains a configuration choice.
- **BR-INV-017** — If both players do not press Ready within **three minutes** after the invitation is claimed or matchmaking pairs them, cancel the pending game.
- **BR-INV-018** — A pre-start cancellation has no result or Elo effect and is absent from match history.
- **BR-INV-019** — Once both players press Ready and play starts, leaving is handled as a disconnect with the three-minute grace period and eventual forfeit, not as a pre-start cancellation.

## 5. Ratings and matchmaking

- **BR-RATE-001** — Each player has a separate simple Elo rating per enabled mode.
- **BR-RATE-002** — Classical is enabled at launch.
- **BR-RATE-003** — The selected signup skill level supplies the starting rating.
- **BR-RATE-004** — Use K = **20** from the first rated game, with no provisional period.
- **BR-RATE-005** — Future K-factor changes affect only future rating applications.
- **BR-RATE-006** — Completed **rated** online games update both players' mode ratings **exactly once**.
- **BR-RATE-007** — Unrated invite games do not change ratings or create rating applications.
- **BR-RATE-008** — Disconnect forfeits are wins/losses for rating purposes only when the game is rated.
- **BR-RATE-009** — **Any draw changes neither player's rating (+0 each),** including a mutually agreed abandonment draw.
- **BR-RATE-010** — Record each historical rating application with the game, player, mode, rating before, delta, and rating after.
- **BR-RATE-011** — Current ratings serve display and matchmaking.
- **BR-RATE-012** — Never recalculate historical rating events from current values.
- **BR-RATE-013** — Matchmaking pairs registered players seeking the same enabled variant and mode.
- **BR-RATE-014** — Pair players at approximately similar Elo.
- **BR-RATE-015** — A player has at most one active queue request.
- **BR-RATE-016** — Begin within a configurable rating gap, widen it with waiting time to a configurable maximum, and never exceed that maximum.
- **BR-RATE-017** — Exact thresholds are configuration decisions.
- **BR-RATE-018** — Explicit cancellation leaves the queue immediately.
- **BR-RATE-019** — A configurable heartbeat and short grace period remove disconnected queue requests.
- **BR-RATE-020** — Consuming both queue requests, assigning colors, and creating one game is atomic.
- **BR-RATE-021** — Competing workers cannot match either player twice.
- **BR-RATE-022** — Rating application is also atomic and idempotent.

## 6. Puzzles and guest progress

- **BR-PUZ-001** — An Admin creates/publishes premade puzzles with difficulty and theme/type (such as mate in one).
- **BR-PUZ-002** — A puzzle can require several correct moves.
- **BR-PUZ-003** — Persist account-level **completion**, without tracking attempts, solve time, hints, or puzzle Elo.
- **BR-PUZ-004** — Guest completions live in browser storage, without a server-side guest account.
- **BR-PUZ-005** — After registration, the browser submits completion claims.
- **BR-PUZ-006** — The server checks that puzzle identifiers exist and are eligible.
- **BR-PUZ-007** — The server merges completion claims idempotently so a player has at most one completion per puzzle.
- **BR-PUZ-008** — Strong proof of an offline solve is not required.
- **BR-PUZ-009** — Unpublishing or soft deleting a puzzle removes it from discovery without deleting existing registered-player completion records.
- **BR-PUZ-010** — Do not hard-delete puzzles with historical completions as a routine operation.

## 7. Historical integrity and lifecycle

- **BR-HIST-001** — Active online position/clocks/status, readiness and connection state, open invitations, queue requests, current ratings, and puzzle publication state are mutable.
- **BR-HIST-002** — Display names are immutable under launch rules.
- **BR-HIST-003** — Accepted moves, final results and reasons, effective game settings, rating events, and puzzle completions are historical facts.
- **BR-HIST-004** — Preserve references from online games and rating events to stable accounts.
- **BR-HIST-005** — History displays the current name rather than a name snapshot.
- **BR-HIST-006** — Routine account and puzzle changes must not erase historical games, moves, rating events, or completions.
- **BR-HIST-007** — Protect case-insensitive email uniqueness; unique display names; distinct online participants and colors; unique move sequence per game; one active queue entry per player; one invitation claim; one completion per player/puzzle; and one rating application per player/game.
- **BR-HIST-008** — Use transactions and concurrency controls for invite claims, matchmaking, move processing, game finalization, rating application, and guest-progress imports.

## Remaining configurable values and implementation notes

- **BR-CONFIG-001** — Matchmaking's initial rating gap, widening step and interval, maximum gap, queue heartbeat interval, and queue disconnect grace period are configuration values to set before enabling matchmaking.
- **BR-CONFIG-002** — The queue disconnect policy is separate from the **three-minute active-game reconnection grace period**.
- **BR-CONFIG-003** — Set the pending-game heartbeat detection interval so an unexpected browser closure is eventually recognized as a cancellation.
- **BR-CONFIG-004** — The Ready deadline is fixed at **three minutes** from claim/pairing, regardless of heartbeat status.
- The SQL Server data model and DDL have been drafted separately.
- This document remains the source of product behavior; database constraints and transactional processing should enforce the rules where feasible.
