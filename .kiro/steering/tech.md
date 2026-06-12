# Tech Stack & Build

## Backend

- **Language**: Java 11
- **Framework**: Spring Boot 2.7.18
- **Build**: Maven (`backend/pom.xml`)
- **Database**: PostgreSQL (schema: `chatbot`), Flyway migrations
- **ORM**: Spring Data JPA / Hibernate
- **Serialization**: Jackson
- **Validation**: Spring Boot Starter Validation (`javax.validation`)
- **Testing**: JUnit 5, jqwik 1.8.2 (property-based testing), H2 (in-memory for tests)

## Frontend

- **Language**: TypeScript (strict mode)
- **Framework**: React 18
- **Build**: Vite 5
- **Canvas Library**: React Flow 11
- **HTTP Client**: Axios
- **Testing**: Vitest, Testing Library, fast-check (property-based testing)

## Common Commands

### Backend (run from `backend/`)

```sh
# Compile
mvn compile

# Run tests (includes *Test.java, *Tests.java, *Properties.java)
mvn test

# Package
mvn package

# Run application
mvn spring-boot:run
```

### Frontend (run from `frontend/`)

```sh
# Install dependencies
npm install

# Development server
npm run dev

# Build (type-check + bundle)
npm run build

# Run tests (single run, no watch)
npm test

# Type-check only
npm run lint
```

## Testing Strategy

Property-based testing is the primary correctness verification approach:
- Backend: jqwik — test files named `*Properties.java`
- Frontend: fast-check — test files in `src/__tests__/*.properties.test.ts`

Standard unit/integration tests also exist alongside PBT files.
