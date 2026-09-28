# SWD392 Backend

Java Spring Boot 4.1.1 REST API starter generated with Spring Initializr.

## Requirements

- JDK 21 or newer (build and tests verified with JDK 22).
- Internet access for the first build to download Maven and dependencies.
- Maven is included through the Maven Wrapper; no separate installation is needed.

The Maven Wrapper uses `JAVA_HOME` when it is set. Point it to the JDK you want to use.

## Run

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

The starter includes Spring MVC, Jakarta Validation, and testing support. Database and authentication configuration can be added once the application's requirements are defined.

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
