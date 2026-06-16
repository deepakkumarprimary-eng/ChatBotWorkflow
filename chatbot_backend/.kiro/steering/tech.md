# Tech Stack

## Core
- Java 17
- Spring Boot 3.3.5
- Maven (build tool)

## Frameworks & Libraries
- Spring Web (REST controllers)
- Spring Data JPA (repository layer)
- Spring WebSocket (included, not yet active — Phase 2)
- Hibernate 6.3+ with PostgreSQL dialect
- Hypersistence Utils 3.7.3 (`JsonType` for JSONB column mapping)
- Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- PostgreSQL JDBC driver

## Database
- PostgreSQL (localhost:5432, schema `chatbot_db`)
- DDL managed via `schema.sql` (runs on startup); Hibernate ddl-auto is `none`
- JSONB column for flexible workflow storage

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
