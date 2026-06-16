# Project Structure

```
chatbot_backend/
├── pom.xml                          # Maven config, dependency management
├── src/main/java/com/xpressbees/chatbot/
│   ├── ChatbotApplication.java      # Spring Boot entry point
│   ├── controller/                  # REST API endpoints (@RestController)
│   ├── dto/                         # Request/Response data transfer objects
│   ├── entity/                      # JPA entities (DB-mapped classes)
│   ├── exception/                   # Custom exceptions + global handler
│   ├── repository/                  # Spring Data JPA repositories
│   └── service/                     # Business logic (interface + impl)
├── src/main/resources/
│   ├── application.properties       # App configuration
│   └── schema.sql                   # DDL executed on startup
└── target/                          # Build output (gitignored)
```

## Architecture Pattern

Layered architecture with clear separation:

1. **Controller** — Handles HTTP requests/responses, delegates to service layer
2. **Service** (interface + impl) — Contains business logic, entity ↔ DTO mapping
3. **Repository** — Data access via Spring Data JPA
4. **Entity** — Database table mapping
5. **DTO** — API contract objects (separate from entities)
6. **Exception** — Centralized error handling with `@RestControllerAdvice`

## Naming Conventions
- Controllers: `{Domain}Controller.java`
- Services: `{Domain}Service.java` (interface), `{Domain}ServiceImpl.java` (implementation)
- DTOs: `{Domain}Request.java`, `{Domain}Response.java`
- Entities: `{Domain}.java`
- Repositories: `{Domain}Repository.java`
- Exceptions: `{Domain}NotFoundException.java`

## API Base Path
All REST endpoints live under `/api/{resource}` (e.g., `/api/workflows`).
