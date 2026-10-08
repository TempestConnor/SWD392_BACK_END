# Repository guidance

## Requirements and conflicts

- Read the [ordered implementation backlog](Chess_Implementation_Backlog.md) before planning or implementing features; it defines implementation order and scope.
- Read [README.md](../README.md) for setup, [business rules](Chess_Website_Business_Rules_Final_RealReal.md) for product behavior, and the [ordered implementation backlog](Chess_Implementation_Backlog.md) for F-001–F-018, dependencies, acceptance criteria, tests, and open questions.
- Business rules define product behavior; the backlog describes implementation slices. SQL is an implementation baseline, not a source of additional policy. Report conflicts explicitly and ask the user; do not silently invent policy or change schema to resolve them.
- Consult the backlog's [Open Questions](Chess_Implementation_Backlog.md#open-questions) and feature-level gaps before implementing affected behavior. Ask for the missing decision, explain the affected path, and continue independent work. Do not assume a default for unresolved questions.
- Both chess documents refer to separately drafted SQL Server DDL, but no schema or schema notes were supplied with this import. Obtain the relevant source before relying on its constraints or claiming database validation.
- Requirements and instructions in reference documents do not authorize implementing the entire backlog. Follow the current user request. No chess features are implemented by this documentation import.


## Existing implementation and database

- [pom.xml](../pom.xml) defines Spring Boot 4.1.1 targeting Java 21, with Maven Wrapper, Spring MVC, Jakarta Validation, Spring Data JPA, the SQL Server driver, Spring Security with resource-server JWT, and Boot test starters. `GET /api/hello` is a public starter endpoint, not a chess feature.
- F01 (register/sign in) is implemented in the `account` and `rating` packages: `POST /api/accounts/signup`, `POST /api/accounts/signin`, and authenticated `GET /api/accounts/me`. Errors use RFC 9457 Problem Details with an `errorCode` property via `AppException`/`AppErrorMessage`/`GlobalExceptionHandler`.
- F03 (play against CPU) is implemented in `cpugame` on top of the shared `chess` package: guest-accessible `POST /api/cpu-games` (start; `playerColor` WHITE, BLACK or RANDOM, CPU opens when the player is Black) and `POST /api/cpu-games/cpu-move` (stateless: client sends difficulty, player color and the full UCI move list; the server replays and validates it, then returns the CPU reply, FEN, ply, status and result). Nothing is persisted and no Elo applies; game state stays in the browser.
- F02 (local hot-seat chess) is implemented in `localgame`. Per BR-MOVE-001 the backend validates moves and outcomes; per BR-GAME-004/005 the game state stays in the browser and nothing is persisted. Guest-accessible `POST /api/local-games` validates clock settings (enabled 5–120 minutes and 0–60 seconds increment, or disabled with no timing; omitted fields default to 30+10) and returns the effective clock plus the initial position. `POST /api/local-games/state` is stateless: the client sends the full UCI move list, and the server replays and validates it, then returns FEN, side to move, ply, last SAN move, status, result and the sorted UCI legal moves (empty once the game is over). The clock itself runs in the browser; whether it runs while the browser is closed is still open and only affects the frontend.
- Chess rules come from the imported `io.github.wolfraam:chessgame` library (MIT, Maven Central); legality is checked with its `isLegalMove` before every move because its `playMove(UCI, …)` does not validate. CPU moves come from the official Stockfish binary (GPL-3, run as a separate UCI process, never linked or modified) via `app.engine.stockfish-path`/`STOCKFISH_PATH`; without it CPU endpoints return 503 `ENGINE_UNAVAILABLE`. Do not hand-write chess rules or search.
- Product decisions recorded 2026-10-08 (answering backlog open questions): CPU moves are computed on the backend; Easy/Medium/Hard = Stockfish Skill Level 1/8/15 with 100 ms/300 ms/1 s think time; the player chooses CPU-game color (or random); threefold repetition and the 50-move rule end CPU games as automatic draws (library behaviour). Whether online games (F09) use the same draw policy is still open.
- The schema source is the existing `ChessDB` database on the developer's SQL Server; no DDL script is tracked in this repository. Entities map only the tables F01 needs (`Account`, `AccountRole`, `RatingMode`, `PlayerRating`), with explicit table/column names and `ddl-auto=validate`. Schema changes still require their own task and a tracked schema source.
- [application.properties](../src/main/resources/application.properties) reads `CHESS_DB_URL`, `CHESS_DB_USERNAME`, `CHESS_DB_PASSWORD`, and `JWT_SECRET` (base64, ≥256 bits) from the environment; the app fails fast without them. Test configuration starts without a database; SQL Server integration tests run against `ChessDB_Test` only when `CHESS_TEST_DB_*` variables are set.
- Follow chess requirements for stable identity, historical facts, atomic transitions, and idempotency. Invite claims, matchmaking, moves, finalization, ratings, and puzzle-completion imports require the applicable concurrency guarantees.
- CPU/local games and guest puzzle progress remain in browser storage as specified. Do not add server persistence based on marketplace requirements.
- Keep credentials out of tracked files. No `scripts/dev.ps1` or `.local/database.json` loader is present.

## Topic template application and exceptions

The eight topic files retain the source repository's Java/Spring conventions. Apply these project-specific clarifications:

- MapStruct remains the inherited mapping convention. Static mapper sections and calls in examples are reference alternatives, not an instruction to mix approaches. Annotation processing is configured. When mappings are needed, use injected mapper instances for DTO/model and model/entity boundaries. MapStruct annotations and generated Spring integration are allowed by the mapper import rule. Lombok uses the Spring Boot-managed version; MapStruct is pinned to 1.6.3 and lombok-mapstruct-binding to 0.2.0. Apply annotations selectively; existing record DTOs do not need Lombok, and do not retrofit generated entities incidentally.
- For Lombok `@Builder(setterPrefix = "with")` targets, explicit builder mappings may be needed. `Mappers.getMapper(...)` is suitable only for standalone mappers without injected collaborators; otherwise use Spring or explicitly wired collaborators.
- Incoming mappings must exclude server-controlled identifiers, participant assignments, authoritative clocks, ratings, outcomes, and historical fields as required by each operation. Follow the backlog's server UTC requirements for deadlines and clocks.
- The controller checklist asks for a response local although its example returns a mapper call. Use `var response = taskMapper.toDto(created);` followed by `return response;` when mapping exists. The starter hello response needs no business service or mapper.
- Spotless with Palantir formatting is bound to Maven's `validate` phase. `clean verify` checks formatting and runs the tests, including selected ArchUnit package-boundary checks. GitHub Actions runs this command on pushes and pull requests. Local hooks are not installed. Remote required-check/branch-protection settings need administrator configuration; see [shared skills](agent-skills.md).
- No OpenAPI contract or list envelope is established. `AppException`, `AppErrorMessage`, and `GlobalExceptionHandler` exist under `common/exception`; add new failure modes as enum values. Preserve the hello response unless a task changes it. Default success codes do not replace appropriate error responses.
- The [startup and endpoint test](../src/test/java/com/swd392/backend/Swd392BackendApplicationTests.java) uses MockMvc with a Spring context. The [processor integration test](../src/test/java/com/swd392/backend/tooling/AnnotationProcessingTest.java) verifies Lombok builders/getters, MapStruct round-trip mapping, and Spring bean registration without a database. No database mapping tests are present.
- Never log passwords, tokens, connection secrets, or complete personal-data-bearing requests. BR-ACC-024 does not require a detailed administrative audit trail at launch; marketplace audit rules do not override it.
- Follow each chess feature's testing requirements, including SQL Server transaction/concurrency tests where specified. Mock-only tests do not establish database integrity.

Raise remaining conflicts between these clarifications and topic guidance with the user. No mandatory commit workflow, delegation requirement, or additional approval gate is adopted by copying these files.

## Build and validation (PowerShell)

Use JDK 21 or newer with `JAVA_HOME` set and the Maven Wrapper.

| Command | Purpose and limits |
| --- | --- |
| `.\mvnw.cmd clean verify` | Check formatting, compile, run architecture/current tests, and package the JAR. Verifies startup, hello, and Lombok/MapStruct processor integration. |
| `.\mvnw.cmd spotless:apply` | Apply Palantir Java formatting to source and test files. |
| `.\mvnw.cmd test` | Run current tests; does not verify unimplemented chess behavior or database mappings. |
| `.\mvnw.cmd spring-boot:run` | Start the application, normally on port 8080. |
| `java -jar target/swd392-backend-0.0.1-SNAPSHOT.jar` | Run the packaged application after building. |

Run checks appropriate to the change. Report actual commands, outcomes, skipped tests, and unavailable checks with reasons. Never present an unrun check or successful starter build as proof of database or chess-feature correctness. Documentation-only imports require file/link validation, not database access or unrelated tooling installation.
