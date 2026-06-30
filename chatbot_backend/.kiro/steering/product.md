# Product Overview

Chatbot Workflow Engine — a backend service for Xpressbees that manages chatbot conversation workflows. It provides:

1. **REST API** for CRUD operations on workflow definitions (stored as JSONB in PostgreSQL)
2. **WebSocket (STOMP)** for real-time chatbot conversation execution
3. **REST API** for CRUD operations on reusable API configurations (headers, payload templates, response mappings)
4. **Session reconnection** for resuming interrupted conversations
5. **Back-navigation** for users to return to previous workflow steps

Workflows represent conversational flows (nodes, edges, transitions) that drive a chatbot's behavior. The system processes nodes sequentially: message nodes auto-advance, input nodes pause and wait for user replies, decision nodes evaluate conditions and branch, workflow nodes invoke child sub-workflows.

## Domains

| Domain | Purpose | Endpoints |
|--------|---------|-----------|
| Workflow | Manage workflow definitions (nodes + transitions as JSONB) | `GET/POST/PUT/DELETE /api/workflows` |
| ApiConfig | Manage reusable API call configurations (URL, method, headers, payload, response mappings) | `GET/POST/PUT/DELETE /api/api-configs` |
| Chat (WebSocket) | Real-time conversation execution via STOMP | `/app/chat.init`, `/app/chat.start`, `/app/chat.message`, `/app/chat.back` |
| Reconnection (REST) | Resume interrupted sessions | `POST /api/reconnect` |

## WebSocket Flow

1. Client connects to `ws://localhost:8080/ws` (SockJS) with API key header (production)
2. Client subscribes to `/app/chat.init` → receives `{sessionId, workflows[]}`
3. Client sends to `/app/chat.start` with `{sessionId, workflowId}` → workflow begins processing
4. Server pushes responses to `/topic/chat/{sessionId}`
5. Client sends user input to `/app/chat.message` with `{sessionId, message}`
6. Client can send `/app/chat.back` with `{sessionId}` to navigate backward
7. Loop continues until workflow completes

## Node Types

- **message** — Sends a text message to the user, auto-advances to next node
- **input** — Pauses execution and waits for user reply via `chat.message`. Supports input validation.
- **api** — References an ApiConfig to make external HTTP calls during workflow execution. Supports three behaviors:
  - **Auto-advance** — Single outgoing transition, no user interaction required
  - **Conditional branching** — Multiple transitions with conditions evaluated against session context (first-match-wins)
  - **Interactive selection** — Pauses for user input when `displayVariable` is set (array options) or when multiple transitions exist without conditions (button options)
- **decision** — Evaluates conditions against session context and branches without making an HTTP call
- **workflow** — Invokes a child/sub-workflow, passing context and returning results to the parent

## API Node Features

- Placeholder resolution in URLs, headers, and payload templates (`{{variableName}}` syntax)
- HTTP execution via connection-pooled RestClient (Apache HC5) with configurable timeout
- Retry logic with exponential backoff (5xx/timeout retryable, 4xx not)
- JSONPath-based response extraction into session context
- Condition evaluation supporting `==`, `!=`, `<`, `>`, `<=`, `>=` operators with `and`/`or` logical connectors
- SSRF-safe URL validation before executing external calls
- Error handling with ChatErrorResponse pushed via WebSocket on failures

## Production Features

- **Caching** — Redis-backed caches for workflows (10min TTL) and API configs (10min TTL)
- **WebSocket Resilience** — Connection registry, buffered message sending, inactivity timeouts (30min), connection limits (1000 max), heartbeat (10s interval)
- **Authentication** — API key-based WebSocket auth in production
- **Encryption** — AES-256 encryption for sensitive stored data (API keys, secrets)
- **Observability** — Spring Actuator health checks (custom WebSocket + WorkflowEngine indicators), structured JSON logging (prod), correlation IDs for request tracing
- **Graceful Shutdown** — Drains in-flight workflow executions (30s timeout) before stopping
- **Stale Session Cleanup** — Automatically expires sessions inactive for 24 hours (runs hourly)
- **OpenAPI Documentation** — Swagger UI available in dev/staging at `/swagger-ui.html`
- **CORS** — Configurable allowed origins
- **Reconnection** — Clients can resume interrupted sessions via REST endpoint
