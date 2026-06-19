# Project Structure

```
chatbot_backend/
├── pom.xml                          # Maven config, dependency management
├── src/main/java/com/xpressbees/chatbot/
│   ├── ChatbotApplication.java      # Spring Boot entry point
│   ├── config/                      # WebSocket configuration
│   ├── controller/                  # REST + WebSocket controllers
│   ├── dto/                         # Request/Response data transfer objects
│   ├── entity/                      # JPA entities (DB-mapped classes)
│   ├── exception/                   # Custom exceptions + global handler
│   ├── processor/                   # Node processors (message, input, api)
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
All REST endpoints live under `/api/{resource}` (e.g., `/api/workflows`, `/api/api-configs`).

## WebSocket Endpoints
- Connection: `ws://localhost:8080/ws` (SockJS)
- Application prefix: `/app` (client sends here)
- Broker prefix: `/topic` (server pushes here)
- Subscribe: `/app/chat.init` → returns sessionId + workflow list
- Send: `/app/chat.start` → starts workflow execution
- Send: `/app/chat.message` → user replies during conversation
- Receive: `/topic/chat/{sessionId}` → all server responses

## Key Files

| File | Purpose |
|------|---------|
| `WebSocketConfig.java` | STOMP broker config (endpoints, prefixes) |
| `ChatWebSocketHandler.java` | Handles `/chat.init` subscribe (creates session, returns workflows) |
| `ChatWebSocketController.java` | Handles `/chat.start` and `/chat.message` |
| `WorkflowExecutionServiceImpl.java` | Workflow engine — processes nodes, manages session state, sends responses via SimpMessagingTemplate |
| `ApiConfigController.java` | CRUD REST API for API configurations |
| `ApiConfigServiceImpl.java` | Business logic for API config management |
| `NodeProcessor.java` | Interface for node type handlers |
| `MessageNodeProcessor.java` | Processes message nodes (auto-advance) |
| `InputNodeProcessor.java` | Processes input nodes (pause for user reply) |
| `ApiNodeProcessor.java` | Processes API nodes (HTTP calls, response extraction, conditional branching, interactive selection) |
| `HttpExecutor.java` | Executes HTTP requests with retry logic and timeout configuration |
| `PlaceholderService.java` | Resolves `{{variable}}` placeholders in strings and payload maps using session context |
| `ResponseExtractor.java` | Extracts values from JSON responses using JSONPath and stores them in session context |
| `ConditionEvaluator.java` | Evaluates simple expressions (`var == value`, `and`/`or`) against session context for conditional branching |
