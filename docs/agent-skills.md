# Shared Java/Spring skills

The 33 Java/Spring skill folders in `.agents/skills/` are a versioned snapshot of
the team's installed skills, including their examples, templates, and metadata.
They travel with a normal clone or pull; no personal installation is needed.
Codex discovers this directory automatically. Other agents must support repository
skills or be configured to read the root `AGENTS.md`. Do not assume universal
discovery. See the [Codex skill documentation](https://learn.chatgpt.com/docs/build-skills).

## Project overrides

Read `AGENTS.md` and `docs/repository-guidance.md` from the repository root before
applying a skill. The following project conventions override generic examples,
templates, naming suggestions, and technology choices in all bundled skills:

- Compile for the Java version configured in `pom.xml`; do not upgrade the stack
  merely because an example targets a newer release.
- Follow feature packages with separate model and entity types. Services expose an
  interface and one implementation. Put class-level transactions on service
  implementations, with read-only semantics for read paths.
- Use injected MapStruct mappers at DTO/model and model/entity boundaries. Static
  DTO factories, entity factory methods with business logic, and handwritten
  mapping examples are illustrative alternatives, not this project's convention.
- Use `var` in controllers/tests and explicit local types elsewhere. No `final`
  on parameters or local variables. Follow the repository's test naming and
  given/when/then conventions; mock service interfaces and inject the implementation
  in its own unit tests. Generic test-percentage targets are not quotas.
- SQL Server is the planned database. PostgreSQL examples and containers do not
  authorize changing it. Obtain the relevant schema before relying on constraints;
  use SQL Server for database-specific integrity/concurrency validation.
- Preserve established HTTP contracts, including the hello response. Generic
  response envelopes, error formats, URL versioning, and security examples do not
  authorize changing the API or introducing authentication.
- Preserve the starter hello endpoint's exemption from unnecessary service/mapper
  layers. Apply architecture rules when the corresponding layers exist.
- DDD, hexagonal architecture, reactive programming, AI, messaging, caching,
  multi-tenancy, and other optional technologies apply only when the task and
  actual project architecture warrant them. Never install everything in the catalog.
- Skills are guidance for the current task, not authorization to implement backlog
  items, send messages, publish, or change remote settings.

For remaining conflicts between repository guidance and topic files, ask the user
as required by `AGENTS.md`. Do not silently resolve product or schema decisions.

## Routing

Read only the skills relevant to the requested change. Every name below maps to
`.agents/skills/<name>/SKILL.md`.

| Task | Skills |
| --- | --- |
| Spring Boot classes | `layered-architecture` |
| Tests | `testing-pyramid` |
| REST controllers and DTOs | `rest-api-conventions` |
| Exception mapping | `problem-details-rfc9457` |
| JPA entities, queries, repositories | `spring-data-jpa` |
| Transactions and commit behavior | `transactional-patterns` |
| Schema migrations | `flyway-migrations` |
| Retried commands and duplicate effects | `idempotency-patterns` |
| Grouped configuration | `configuration-properties` |
| First-party JWT / external issuer JWT | `spring-security-jwt` / `oauth2-resource-server` |
| Outbound HTTP / resilience | `http-interface-clients` / `resilience-retry` |
| API versions / contract generation / hypermedia | `api-versioning` / `openapi-first` / `hateoas` |
| Redis / messaging / batch processing | `spring-data-redis` / `event-driven-messaging` / `spring-batch` |
| Service telemetry / AI telemetry | `production-observability` / `ai-observability` |
| Spring AI / MCP tools | `spring-ai-integration` / `mcp-server` |
| Existing DDD / hexagonal / modular architecture | `domain-driven-design` / `hexagonal-architecture` / `spring-modulith` |
| Tenancy / nullability | `multi-tenancy` / `null-safety` |
| Reactive request paths / edge gateway | `webflux-reactive-patterns` / `spring-cloud-gateway` |
| Boot upgrade / Maven modules / containers | `spring-boot-migration` / `multi-module-maven` / `container-native-deployment` |

## Enforcement and maintenance

Instructions guide agent behavior; they cannot guarantee that an agent reads a
skill. The Maven `verify` lifecycle checks Palantir formatting through Spotless,
selected package boundaries through ArchUnit, and the current test suite. These
checks do not establish all architectural, database, or business invariants.

The GitHub Actions workflow runs the same command on pushes and pull requests.
After it has run on GitHub, a repository administrator must require the
`Java quality gate` status check in the target branch's ruleset/protection settings
and require pull requests to prevent ordinary direct-push bypass. Configure bypass
permissions according to the team's policy. Workflow files alone do not enable
branch protection.

Update the committed skill folders deliberately, preserving this project override
link and reviewing changed guidance. Do not auto-download replacements during
builds. Contributors with duplicate personal skills should use the repository paths
specified in `AGENTS.md`.
