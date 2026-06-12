# Project Structure

Monorepo with two main modules: `backend/` (Java) and `frontend/` (TypeScript/React).

```
chatbot/
├── backend/                          # Spring Boot REST API
│   └── src/
│       ├── main/java/com/chatbot/workflow/
│       │   ├── controller/           # REST endpoints, request/response DTOs
│       │   ├── service/              # Business logic, validation engine
│       │   ├── engine/               # Workflow execution engine, state processors
│       │   ├── model/                # Immutable domain models (Jackson-annotated)
│       │   └── repository/           # JPA entities + Spring Data repositories
│       └── main/resources/
│           ├── application.yml       # App config (datasource, JPA, Flyway)
│           └── db/migration/         # Flyway SQL migrations
│
├── frontend/                         # Vite + React SPA
│   └── src/
│       ├── components/               # UI components by domain
│       │   ├── canvas/               # Workflow canvas (React Flow)
│       │   ├── palette/              # State type palette (drag source)
│       │   ├── panel/                # Property editor panel
│       │   ├── validation/           # Validation results display
│       │   ├── execution/            # Execution monitoring UI
│       │   └── workflow/             # Save/load/list dialogs
│       ├── hooks/                    # Custom React hooks
│       ├── services/                 # API client functions (axios)
│       ├── types/                    # TypeScript interfaces and type unions
│       ├── utils/                    # Pure utility/helper functions
│       └── __tests__/                # Property-based and unit tests
│
└── .kiro/
    ├── specs/                        # Feature specifications
    └── steering/                     # Steering documents (this directory)
```

## Architecture Patterns

### Backend

- **Layered**: Controller → Service → Repository
- **Constructor injection** throughout (no field injection, no Lombok)
- **Domain models** are immutable value objects with `@JsonCreator`
- **JPA entities** are mutable (setters), separate from domain models
- **Strategy pattern** in engine: one `StateProcessor` implementation per `StateType`
- **Soft-delete** via `deletedAt` timestamp on workflow entities
- **Versioning**: every workflow update creates a new `WorkflowVersionEntity`
- **Validation** returns result objects; `GlobalExceptionHandler` maps exceptions to HTTP errors

### Frontend

- **Functional components** with React hooks (no class components)
- **Discriminated unions** for state configs (discriminator field: `type`)
- **Barrel exports** via `index.ts` in each directory
- **Services layer** wraps all API calls and returns typed responses
- **Canvas state** managed with `Map<string, T>` collections
- **Undo/redo** stack via custom hook operating on `CanvasOperation` union type
