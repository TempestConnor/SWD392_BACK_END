# SWD392 Backend

Java Spring Boot 4.1.1 REST API starter generated with Spring Initializr.

## Requirements

- JDK 21 or newer (build and tests verified with JDK 22).
- Internet access for the first build to download Maven and dependencies.
- Maven is included through the Maven Wrapper; no separate installation is needed.

The Maven Wrapper uses `JAVA_HOME` when it is set. Point it to the JDK you want to use.

## Run

The application needs a SQL Server database with the chess schema (`ChessDB`) and a JWT signing secret.
Set these environment variables before starting (never commit them):

```powershell
$env:CHESS_DB_URL = "jdbc:sqlserver://localhost:<port>;databaseName=ChessDB;encrypt=true;trustServerCertificate=true"
$env:CHESS_DB_USERNAME = "<sql login>"
$env:CHESS_DB_PASSWORD = "<password>"
$env:JWT_SECRET = "<base64 key of at least 32 bytes>"
```

SQL Server Express usually listens on a dynamic TCP port; use the port shown in SQL Server
Configuration Manager (or enable SQL Server Browser). Optional: `JWT_ACCESS_TOKEN_TTL` (ISO-8601, default `PT1H`),
`PENDING_HEARTBEAT_TIMEOUT` (silence that cancels a game before start, default `PT30S`) and
`ONLINE_GAME_SWEEP_INTERVAL` (how often stale pending games are cancelled and timed-out active games are
completed, default `PT5S`).

CPU games need the Stockfish engine (GPL-3, run as a separate process). Download the official
build for your platform from https://github.com/official-stockfish/Stockfish/releases (verify the
SHA-256 shown on the release page), keep it outside this repository, and point the backend at it:

```powershell
$env:STOCKFISH_PATH = "C:\path\to\stockfish-windows-x86-64-universal.exe"
```

Optional: `ENGINE_POOL_SIZE` (concurrent engine processes, default 2) and `ENGINE_ACQUIRE_TIMEOUT`
(default `PT5S`). Without `STOCKFISH_PATH` the app still starts, but CPU-move requests return 503.

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux:

```sh
sh ./mvnw spring-boot:run
```

The application listens on port 8080. Try `GET http://localhost:8080/api/hello`:

```json
{"message":"SWD392 backend is running"}
```

To use a different port in PowerShell, set `$env:SERVER_PORT = "8081"` before starting.

## Build and test

```powershell
.\mvnw.cmd clean verify
java -jar target/swd392-backend-0.0.1-SNAPSHOT.jar
```

Use `sh ./mvnw clean verify` on macOS / Linux.

`verify` checks Java formatting with Spotless/Palantir, selected architecture
boundaries with ArchUnit, and the test suite. Fix formatting with
`.\mvnw.cmd spotless:apply` (or `sh ./mvnw spotless:apply`).

SQL Server integration tests (`AccountSqlServerIntegrationTest`, `InvitationSqlServerIntegrationTest`,
`OnlineGameReadySqlServerIntegrationTest`, `OnlineGameMoveSqlServerIntegrationTest`)
are skipped unless `CHESS_TEST_DB_URL`, `CHESS_TEST_DB_USERNAME` and `CHESS_TEST_DB_PASSWORD` point at a
schema-only test database (`ChessDB_Test`) whose `RatingMode` table contains `CLASSICAL` and which has
every script in [db/migrations](db/README.md) applied (apply them to `ChessDB` too).
They create and delete their own rows. Stockfish tests (`EngineServiceImplTest`,
`CpuGameStockfishIntegrationTest`) run only when `STOCKFISH_PATH` is set. CI currently runs
without either.

## Team agents and CI

Java/Spring skills are included in `.agents/skills/` and routed by [AGENTS.md](AGENTS.md).
Codex discovers them automatically after cloning/pulling; no skill installation is
required. Agents without native discovery should read `AGENTS.md` and its linked
skill files. See [shared skills](docs/agent-skills.md) for project overrides,
maintenance, and the limits of automatic discovery.

GitHub Actions runs `clean verify` on pushes and pull requests. To make it a merge
requirement, a repository administrator must enable the `Java quality gate`
required status check and require pull requests in the target branch's protection
settings/ruleset. Those remote settings are not configured by the workflow file.

## Structure

```text
src/main/java/com/swd392/backend/
  Swd392BackendApplication.java   Application entry point
  controller/                    HTTP endpoints
  dto/                           Request and response records
src/main/resources/
  application.properties         Application configuration
src/test/java/com/swd392/backend/ Automated tests
```

Add business logic under `service/` and persistence under `repository/` as features are introduced. Controllers should handle HTTP concerns and delegate business operations to services.

Features follow the per-feature package layout in [AGENTS.md](AGENTS.md) (`account/`, `rating/`, …). Accounts authenticate with `POST /api/accounts/signin` and send `Authorization: Bearer <token>` on protected endpoints.

## Lombok and MapStruct

Lombok uses Spring Boot's managed version. MapStruct 1.6.3 and
`lombok-mapstruct-binding` 0.2.0 are configured with explicit Maven annotation
processor paths. Lombok is a provided, optional dependency and is excluded from
the executable JAR.

Use specific Lombok annotations where needed; avoid `@Data`. Record DTOs need no
Lombok. Use `@Mapper(componentModel = "spring")` for Spring-managed mappers and
explicitly exclude server-controlled fields from incoming mappings. Do not
retrofit generated entities just to adopt these tools.

The processor integration test verifies Lombok builders/getters, mapping in both
directions, and Spring bean registration. Run it alone with:

```powershell
.\mvnw.cmd "-Dtest=AnnotationProcessingTest" test
```

Reload the Maven project in your IDE after changing dependencies. Maven generates
mapper implementations during compilation; do not edit generated source files.

RECOMMENDED PROMPTING STYLE:

Start implementing the release scope in docs/Chess_Implementation_Backlog.md.
Read AGENTS.md, repository guidance, implementation scope, and the topic documents and skills relevant to the selected feature. Follow the repository’s conflict-resolution requirements.
Inspect the existing implementation first. Identify the next incomplete feature according to the documented priorities and dependencies. If the document does not establish an implementation order, choose the smallest complete feature that enables subsequent work and explain the choice.
Implement that feature end to end, including persistence changes, business rules, API contracts, error handling, and meaningful tests where needed. Preserve existing work and keep changes focused on the selected feature and its necessary dependencies. Respect all documented deferrals.
Resolve routine technical choices using the repository conventions. Ask me about unresolved product decisions or instruction conflicts that materially affect behavior; do not invent business rules.
Run the required validation. Report what was implemented, the checks that passed or could not run, remaining limitations, and the next recommended feature. Complete one feature before stopping for review. 
