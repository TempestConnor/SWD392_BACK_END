# Repository guidance

## Requirements and conflicts

- Read the [ordered implementation backlog](Chess_Implementation_Backlog.md) before planning or implementing features; it defines implementation order and scope.
- Read [README.md](../README.md) for setup, [business rules](Chess_Website_Business_Rules_Final_RealReal.md) for product behavior, and the [ordered implementation backlog](Chess_Implementation_Backlog.md) for F-001–F-018, dependencies, acceptance criteria, tests, and open questions.
- Business rules define product behavior; the backlog describes implementation slices. SQL is an implementation baseline, not a source of additional policy. Report conflicts explicitly and ask the user; do not silently invent policy or change schema to resolve them.
- Consult the backlog's [Open Questions](Chess_Implementation_Backlog.md#open-questions) and feature-level gaps before implementing affected behavior. Ask for the missing decision, explain the affected path, and continue independent work. Do not assume a default for unresolved questions.
- Both chess documents refer to separately drafted SQL Server DDL, but no schema or schema notes were supplied with this import. Obtain the relevant source before relying on its constraints or claiming database validation.
- Requirements and instructions in reference documents do not authorize implementing the entire backlog. Follow the current user request. No chess features are implemented by this documentation import.


## Existing implementation and database

- [pom.xml](../pom.xml) defines Spring Boot 4.1.1 targeting Java 21, with Maven Wrapper, Spring MVC, Jakarta Validation, and Boot test starters. Code under `src/main/java/com/swd392/backend` contains the entry point, `HelloController`, and `HelloResponse`. `GET /api/hello` is a starter endpoint, not a chess feature.
- SQL Server is the planned database. JPA, its driver, datasource configuration, schema, entities, repositories, and database tests are not present. Do not describe planned architecture as established implementation.
- [application.properties](../src/main/resources/application.properties) sets only the application name. Database setup and schema changes require their own implementation task and relevant schema source. This import does not configure database access or automatic DDL.
- Follow chess requirements for stable identity, historical facts, atomic transitions, and idempotency. Invite claims, matchmaking, moves, finalization, ratings, and puzzle-completion imports require the applicable concurrency guarantees.
- CPU/local games and guest puzzle progress remain in browser storage as specified. Do not add server persistence based on marketplace requirements.
- Keep credentials out of tracked files. No `scripts/dev.ps1` or `.local/database.json` loader is present.

## Topic template application and exceptions

The eight topic files retain the source repository's Java/Spring conventions. Apply these project-specific clarifications:

- MapStruct remains the inherited mapping convention. Static mapper sections and calls in examples are reference alternatives, not an instruction to mix approaches. Annotation processing is configured. When mappings are needed, use injected mapper instances for DTO/model and model/entity boundaries. MapStruct annotations and generated Spring integration are allowed by the mapper import rule. Lombok uses the Spring Boot-managed version; MapStruct is pinned to 1.6.3 and lombok-mapstruct-binding to 0.2.0. Apply annotations selectively; existing record DTOs do not need Lombok, and do not retrofit generated entities incidentally.
- For Lombok `@Builder(setterPrefix = "with")` targets, explicit builder mappings may be needed. `Mappers.getMapper(...)` is suitable only for standalone mappers without injected collaborators; otherwise use Spring or explicitly wired collaborators.
- Incoming mappings must exclude server-controlled identifiers, participant assignments, authoritative clocks, ratings, outcomes, and historical fields as required by each operation. Follow the backlog's server UTC requirements for deadlines and clocks.
- The controller checklist asks for a response local although its example returns a mapper call. Use `var response = taskMapper.toDto(created);` followed by `return response;` when mapping exists. The starter hello response needs no business service or mapper.
- Spotless, Palantir formatting, and CI/hooks are not configured. The topic quality gate describes desired tooling, not an available or satisfied check. Report formatting validation as unavailable until configured; this documentation import does not install it.
- No OpenAPI contract, list envelope, `AppException`, `AppErrorMessage`, or `GlobalExceptionHandler` is established. Examples define conventions, not existing functionality. Preserve the hello response unless a task changes it. Default success codes do not replace appropriate error responses.
- The [startup and endpoint test](../src/test/java/com/swd392/backend/Swd392BackendApplicationTests.java) uses MockMvc with a Spring context. The [processor integration test](../src/test/java/com/swd392/backend/tooling/AnnotationProcessingTest.java) verifies Lombok builders/getters, MapStruct round-trip mapping, and Spring bean registration without a database. No database mapping tests are present.
- Never log passwords, tokens, connection secrets, or complete personal-data-bearing requests. BR-ACC-024 does not require a detailed administrative audit trail at launch; marketplace audit rules do not override it.
- Follow each chess feature's testing requirements, including SQL Server transaction/concurrency tests where specified. Mock-only tests do not establish database integrity.

Raise remaining conflicts between these clarifications and topic guidance with the user. No mandatory commit workflow, delegation requirement, or additional approval gate is adopted by copying these files.

## Build and validation (PowerShell)

Use JDK 21 or newer with `JAVA_HOME` set and the Maven Wrapper.

| Command | Purpose and limits |
| --- | --- |
| `.\mvnw.cmd clean verify` | Compile, run current tests, and package the JAR. Verifies startup, hello, and Lombok/MapStruct processor integration. |
| `.\mvnw.cmd test` | Run current tests; does not verify unimplemented chess behavior or database mappings. |
| `.\mvnw.cmd spring-boot:run` | Start the application, normally on port 8080. |
| `java -jar target/swd392-backend-0.0.1-SNAPSHOT.jar` | Run the packaged application after building. |

Run checks appropriate to the change. Report actual commands, outcomes, skipped tests, and unavailable checks with reasons. Never present an unrun check or successful starter build as proof of database or chess-feature correctness. Documentation-only imports require file/link validation, not database access or unrelated tooling installation.

.
