# Project Structure

```
chatbot_backend/
├── pom.xml                          # Maven config, dependency management
├── Dockerfile                       # Container image definition
├── docs/                            # Frontend integration guides
├── src/main/java/com/xpressbees/chatbot/
│   ├── ChatbotApplication.java      # Spring Boot entry point
│   ├── config/                      # App config (WebSocket, CORS, Auth, Health, OpenAPI)
│   ├── controller/                  # REST + WebSocket controllers
│   ├── dto/                         # Request/Response data transfer objects
│   ├── entity/                      # JPA entities (DB-mapped classes)
│   ├── exception/                   # Custom exceptions + global handler
│   ├── processor/                   # Node processors (message, input, api, decision, workflow)
│   ├── repository/                  # Spring Data JPA repositories
│   ├── service/                     # Business logic (interface + impl)
│   └── util/                        # Utility classes (JSON helpers)
├── src/main/resources/
│   ├── application.properties       # Shared config (base)
│   ├── application-dev.properties   # Dev profile overrides
│   ├── application-staging.properties # Staging profile overrides
│   ├── application-prod.properties  # Production profile (env vars, no defaults)
│   ├── logback-spring.xml           # Profile-based logging config
│   └── schema.sql                   # DDL executed on startup
├── src/test/                        # Unit + integration tests (jqwik, TestContainers)
└── target/                          # Build output (gitignored)
```

## Architecture Pattern

Layered architecture with clear separation:

1. **Controller** — Handles HTTP requests/responses, delegates to service layer
2. **Service** (interface + impl) — Contains business logic, entity ↔ DTO mapping
3. **Repository** — Data access via Spring Data JPA
4. **Entity** — Database table mapping
5. **DTO** — API contract objects (separate from entities)
6. **Processor** — Node type handlers (strategy pattern via `ProcessorRegistry`)
7. **Config** — Application configuration (WebSocket, CORS, auth, health indicators, OpenAPI)
8. **Exception** — Centralized error handling with `@RestControllerAdvice`
9. **Util** — Shared utility classes

## Naming Conventions
- Controllers: `{Domain}Controller.java`
- Services: `{Domain}Service.java` (interface), `{Domain}ServiceImpl.java` (implementation)
- DTOs: `{Domain}Request.java`, `{Domain}Response.java`
- Entities: `{Domain}.java`
- Repositories: `{Domain}Repository.java`
- Exceptions: `{Domain}NotFoundException.java`
- Processors: `{NodeType}NodeProcessor.java`

## API Base Path
All REST endpoints live under `/api/{resource}` (e.g., `/api/workflows`, `/api/api-configs`).

## WebSocket Endpoints
- Connection: `ws://localhost:8080/ws` (SockJS)
- Application prefix: `/app` (client sends here)
- Broker prefix: `/topic` (server pushes here)
- Subscribe: `/app/chat.init` → returns sessionId + workflow list
- Send: `/app/chat.start` → starts workflow execution
- Send: `/app/chat.message` → user replies during conversation
- Send: `/app/chat.back` → navigate back to previous node
- Receive: `/topic/chat/{sessionId}` → all server responses

## Observability Endpoints
- Health: `GET /actuator/health` (custom indicators: WebSocket, WorkflowEngine)
- Metrics: `GET /actuator/metrics`
- Info: `GET /actuator/info`
- Swagger UI: `GET /swagger-ui.html` (dev/staging only)
- OpenAPI spec: `GET /v3/api-docs`

## Key Files

| File | Purpose |
|------|---------|
| **Config** | |
| `WebSocketConfig.java` | STOMP broker config (endpoints, prefixes, thread pools) |
| `WebSocketAuthInterceptor.java` | API key authentication for WebSocket connections |
| `WebSocketResilienceProperties.java` | Configurable WebSocket resilience settings |
| `WebSocketHealthIndicator.java` | Custom health indicator for active WS connections |
| `WorkflowEngineHealthIndicator.java` | Custom health indicator for workflow engine status |
| `ConnectionLimitInterceptor.java` | Enforces max WebSocket connection limit |
| `CorsConfig.java` | CORS origin configuration |
| `OpenApiConfig.java` | SpringDoc/Swagger configuration |
| **Controllers** | |
| `ChatWebSocketHandler.java` | Handles `/chat.init` subscribe (creates session, returns workflows) |
| `ChatWebSocketController.java` | Handles `/chat.start`, `/chat.message`, `/chat.back` |
| `ReconnectionController.java` | REST endpoint for session reconnection |
| `WorkflowController.java` | CRUD REST API for workflow definitions |
| `ApiConfigController.java` | CRUD REST API for API configurations |
| **Processors** | |
| `NodeProcessor.java` | Interface for node type handlers |
| `ProcessorRegistry.java` | Dynamic processor lookup (strategy pattern) |
| `MessageNodeProcessor.java` | Processes message nodes (auto-advance) |
| `InputNodeProcessor.java` | Processes input nodes (pause for user reply) |
| `ApiNodeProcessor.java` | Processes API nodes (HTTP calls, extraction, branching) |
| `DecisionNodeProcessor.java` | Processes decision nodes (condition-only branching, no HTTP) |
| `WorkflowNodeProcessor.java` | Processes workflow nodes (child/sub-workflow invocation) |
| **Core Services** | |
| `WorkflowExecutionServiceImpl.java` | Workflow engine — processes nodes, manages session state |
| `SessionStateManager.java` | Session state lifecycle management |
| `NavigationService.java` | Back-navigation through workflow nodes |
| `ChildWorkflowService.java` | Sub-workflow execution |
| `InputValidationService.java` | Input validation for workflow nodes |
| **HTTP Execution** | |
| `HttpExecutor.java` | Executes HTTP requests with retry logic and timeout |
| `RestClientPool.java` | Connection-pooled HTTP client (Apache HC5) |
| `PlaceholderService.java` | Resolves `{{variable}}` placeholders in strings/payloads |
| `ResponseExtractor.java` | JSONPath-based response extraction into session context |
| `ConditionEvaluator.java` | Evaluates expressions against session context |
| `UrlValidator.java` | SSRF-safe URL validation |
| **Caching** | |
| `WorkflowCacheService.java` | Redis-backed workflow cache (TTL: 10min) |
| `ApiConfigCacheService.java` | Redis-backed ApiConfig cache (TTL: 10min) |
| `PendingSessionStore.java` | Temporary session storage pre-subscription (TTL: 5min) |
| **WebSocket Resilience** | |
| `ConnectionRegistry.java` / `InMemoryConnectionRegistry.java` | Track active WS connections |
| `BufferedMessageSender.java` / `BufferedMessageSenderImpl.java` | Buffered WS message sending |
| `SessionSendBuffer.java` | Per-session message buffer |
| `DisconnectListener.java` | WebSocket disconnect handling |
| `InactivityTimeoutScheduler.java` | Disconnects idle WS sessions |
| `BufferDrainTimeoutScheduler.java` | Timeout for draining send buffers |
| **Operational** | |
| `CorrelationIdManager.java` | MDC correlation ID management for logging |
| `EncryptionService.java` | AES-256 encryption for sensitive data |
| `ExecutionTracker.java` | Tracks in-flight executions (graceful shutdown) |
| `GracefulShutdownListener.java` | Drains in-flight work on shutdown |
| `StaleSessionCleanupService.java` | Scheduled cleanup of inactive sessions (24h) |
