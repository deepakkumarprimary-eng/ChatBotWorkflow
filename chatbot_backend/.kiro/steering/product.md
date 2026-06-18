# Product Overview

Chatbot Workflow Engine — a backend service for Xpressbees that manages chatbot conversation workflows. It provides:

1. **REST API** for CRUD operations on workflow definitions (stored as JSONB in PostgreSQL)
2. **WebSocket (STOMP)** for real-time chatbot conversation execution
3. **REST API** for CRUD operations on reusable API configurations (headers, payload templates, response mappings)

Workflows represent conversational flows (nodes, edges, transitions) that drive a chatbot's behavior. The system processes nodes sequentially: message nodes auto-advance, input nodes pause and wait for user replies.

## Domains

| Domain | Purpose | Endpoints |
|--------|---------|-----------|
| Workflow | Manage workflow definitions (nodes + transitions as JSONB) | `GET/POST/PUT/DELETE /api/workflows` |
| ApiConfig | Manage reusable API call configurations (URL, method, headers, payload, response mappings) | `GET/POST/PUT/DELETE /api/api-configs` |
| Chat (WebSocket) | Real-time conversation execution via STOMP | `/app/chat.init`, `/app/chat.start`, `/app/chat.message` |

## WebSocket Flow

1. Client connects to `ws://localhost:8080/ws` (SockJS)
2. Client subscribes to `/app/chat.init` → receives `{sessionId, workflows[]}`
3. Client sends to `/app/chat.start` with `{sessionId, workflowId}` → workflow begins processing
4. Server pushes responses to `/topic/chat/{sessionId}`
5. Client sends user input to `/app/chat.message` with `{sessionId, message}`
6. Loop continues until workflow completes

## Node Types (current)

- **message** — Sends a text message to the user, auto-advances to next node
- **input** — Pauses execution and waits for user reply via `chat.message`

## Future (not yet implemented)

- **API node** — Will reference an ApiConfig to make external HTTP calls during workflow execution
