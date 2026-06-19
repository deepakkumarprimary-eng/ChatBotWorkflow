# Tech Stack

## Core
- Java 17
- Spring Boot 3.3.5
- Maven (build tool)

## Frameworks & Libraries
- Spring Web (REST controllers)
- Spring Data JPA (repository layer)
- Spring WebSocket + STOMP (active — real-time chat execution)
- Hibernate 6.3+ with PostgreSQL dialect
- Hypersistence Utils 3.7.3 (`JsonType` for JSONB column mapping)
- Jayway JsonPath 2.9.0 (API response extraction using JSONPath expressions)
- Spring RestClient (HTTP execution in API node processor)
- Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- PostgreSQL JDBC driver
- jqwik 1.8.2 (property-based testing)

## Database
- PostgreSQL (localhost:5432, schema `chatbot_db`)
- DDL managed via `schema.sql` (runs on startup); Hibernate ddl-auto is `none`
- JSONB columns for flexible workflow and payload template storage
- Tables: `workflow`, `chat_session`, `api_config`, `api_header`, `api_payload`, `api_response_mapping`
- Cascade deletes on child tables via FK ON DELETE CASCADE

## Common Commands

```shell
# Compile
mvn compile

# Run tests
mvn test

# Package (build JAR)
mvn package

# Run the application
mvn spring-boot:run

# Clean build
mvn clean install
```

## Conventions
- Constructor injection (no field injection with `@Autowired`)
- Lombok for boilerplate (getters, setters, constructors)
- ISO date serialization (Jackson `write-dates-as-timestamps=false`)
- SQL logging enabled (`spring.jpa.show-sql=true`)
