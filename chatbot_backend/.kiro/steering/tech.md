# Tech Stack

## Core
- Java 17
- Spring Boot 3.3.5
- Maven (build tool)

## Frameworks & Libraries
- Spring Web (REST controllers)
- Spring Data JPA (repository layer)
- Spring WebSocket + STOMP (real-time chat execution)
- Spring Data Redis (caching layer — workflow, apiconfig, pending sessions)
- Spring Boot Actuator (health endpoints, metrics, graceful shutdown)
- Spring Boot Validation (Jakarta Bean Validation on DTOs)
- Hibernate 6.3+ with PostgreSQL dialect
- Hypersistence Utils 3.7.3 (`JsonType` for JSONB column mapping)
- Jayway JsonPath 2.9.0 (API response extraction using JSONPath expressions)
- Apache HttpClient 5 (connection-pooled `RestClient` for API node HTTP execution)
- SpringDoc OpenAPI 2.3.0 (Swagger UI + OpenAPI spec generation)
- Logstash Logback Encoder 7.4 (structured JSON logging in production)
- Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- PostgreSQL JDBC driver
- jqwik 1.8.2 (property-based testing)
- TestContainers 1.19.3 (PostgreSQL container for integration tests)
- Embedded Redis 1.4.3 (in-memory Redis for integration tests)

## Database
- PostgreSQL (localhost:5432, schema `chatbot_db`)
- Redis (localhost:6379 — workflow cache, apiconfig cache, pending session store)
- DDL managed via `schema.sql` (runs on startup); Hibernate ddl-auto is `none`
- JSONB columns for flexible workflow and payload template storage
- Tables: `workflow`, `chat_session`, `api_config`, `api_header`, `api_payload`, `api_response_mapping`
- Cascade deletes on child tables via FK ON DELETE CASCADE

## Profiles
- `dev` (default) — local Postgres + Redis, Swagger UI enabled, text logging
- `staging` — same as dev with staging DB credentials, Swagger UI enabled
- `prod` — all secrets via env vars, Swagger UI disabled, JSON structured logging

## Common Commands

```shell
# Compile
mvn compile

# Run tests
mvn test

# Package (build JAR)
mvn package

# Run the application (dev profile)
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=staging

# Clean build
mvn clean install
```

## Conventions
- Constructor injection (no field injection with `@Autowired`)
- Lombok for boilerplate (getters, setters, constructors)
- ISO date serialization (Jackson `write-dates-as-timestamps=false`)
- SQL logging enabled in dev (`spring.jpa.show-sql=true`), disabled in prod
- Correlation IDs in MDC for request tracing across logs
- AES-256 encryption for sensitive data (API keys, secrets)
- SSRF-safe URL validation before external HTTP calls
- WebSocket API key authentication for production
