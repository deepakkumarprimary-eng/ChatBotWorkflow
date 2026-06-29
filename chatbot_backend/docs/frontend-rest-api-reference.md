# Frontend REST API Reference — Complete Schemas & Validation

This document covers the REST API request/response schemas, validation rules, and error formats that are **not fully documented** in the main [frontend-integration-guide.md](./frontend-integration-guide.md). Use this alongside that guide.

---

## 1. Workflow CRUD — Full Schemas

### Create / Update Workflow

```
POST /api/workflows
PUT  /api/workflows/{id}
```

**Request Body:**
```json
{
  "name": "Order Tracking",
  "workflowJson": {
    "nodes": [...],
    "transitions": [...],
    "entryNodeId": "node-1"
  }
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `name` | String | Yes | Not blank, max 255 characters |
| `workflowJson` | Object | Yes | Not null, any valid JSON object |

**Response (201 Created / 200 OK):**
```json
{
  "id": 1,
  "name": "Order Tracking",
  "workflowJson": {
    "nodes": [...],
    "transitions": [...],
    "entryNodeId": "node-1"
  },
  "createdAt": "2025-01-15T10:30:00",
  "updatedAt": "2025-01-15T10:30:00"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Auto-generated workflow ID |
| `name` | String | Workflow display name |
| `workflowJson` | Object | Full workflow definition (nodes + transitions) |
| `createdAt` | ISO DateTime | When created |
| `updatedAt` | ISO DateTime | Last modification time |

### Get Workflow

```
GET /api/workflows/{id}
```

**Response (200 OK):** Same shape as create response above.

### List All Workflows

```
GET /api/workflows
```

**Response (200 OK):**
```json
[
  { "id": 1, "name": "Order Tracking", "workflowJson": {...}, "createdAt": "...", "updatedAt": "..." },
  { "id": 2, "name": "Complaint Registration", "workflowJson": {...}, "createdAt": "...", "updatedAt": "..." }
]
```

### Delete Workflow

```
DELETE /api/workflows/{id}
```

**Response:** 204 No Content (empty body)

---

## 2. API Config CRUD — Full Schemas

### Create / Update API Config

```
POST /api/api-configs
PUT  /api/api-configs/{id}
```

**Request Body:**
```json
{
  "name": "Fetch Shipment Status",
  "url": "https://api.logistics.com/v1/track/{{awb_number}}",
  "method": "GET",
  "timeoutMs": 5000,
  "retryCount": 3,
  "username": null,
  "password": null,
  "clientId": null,
  "headers": [
    { "headerName": "Authorization", "headerValue": "Bearer {{auth_token}}" },
    { "headerName": "Content-Type", "headerValue": "application/json" }
  ],
  "payloadTemplate": {
    "trackingId": "{{awb_number}}",
    "source": "chatbot"
  },
  "responseMappings": [
    { "responsePath": "$.data.status", "contextVariableName": "shipment_status" },
    { "responsePath": "$.data.eta", "contextVariableName": "delivery_eta" }
  ]
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `name` | String | Yes | Not blank, max 255 chars, must be unique |
| `url` | String | Yes | Not blank, max 1024 chars |
| `method` | String | Yes | Not blank, must be valid HTTP method (GET, POST, PUT, DELETE, PATCH) |
| `timeoutMs` | Integer | No | Timeout in milliseconds for the HTTP call |
| `retryCount` | Integer | No | Number of retries on 5xx/timeout errors |
| `username` | String | No | HTTP Basic Auth username |
| `password` | String | No | HTTP Basic Auth password (stored encrypted) |
| `clientId` | String | No | Client identifier passed to the API |
| `headers` | Array | No | List of HTTP headers to send |
| `payloadTemplate` | Object | No | JSON body template (supports `{{variable}}` placeholders) |
| `responseMappings` | Array | No | JSONPath expressions to extract values from API response |

#### Header Object

| Field | Type | Description |
|-------|------|-------------|
| `headerName` | String | HTTP header name (e.g., "Authorization") |
| `headerValue` | String | Header value, supports `{{variable}}` placeholders |

#### Response Mapping Object

| Field | Type | Description |
|-------|------|-------------|
| `responsePath` | String | JSONPath expression (e.g., `$.data.status`) |
| `contextVariableName` | String | Variable name to store the extracted value in session context |

**Response (201 Created / 200 OK):**
```json
{
  "id": 3,
  "name": "Fetch Shipment Status",
  "url": "https://api.logistics.com/v1/track/{{awb_number}}",
  "method": "GET",
  "timeoutMs": 5000,
  "retryCount": 3,
  "username": "api_user",
  "password": "api_pass",
  "clientId": null,
  "headers": [
    { "headerName": "Authorization", "headerValue": "Bearer {{auth_token}}" }
  ],
  "payloadTemplate": { "trackingId": "{{awb_number}}" },
  "responseMappings": [
    { "responsePath": "$.data.status", "contextVariableName": "shipment_status" }
  ],
  "createdAt": "2025-01-15T10:30:00",
  "updatedAt": "2025-01-15T10:30:00"
}
```

### Get API Config

```
GET /api/api-configs/{id}
```

**ID Validation:** Must be a positive integer. Non-numeric or negative IDs return 400.

### List All API Configs

```
GET /api/api-configs
```

### Delete API Config

```
DELETE /api/api-configs/{id}
```

**Response:** 204 No Content

---

## 3. REST Error Response Formats

### 404 Not Found

When a workflow or API config doesn't exist:

```json
{
  "error": "Workflow not found",
  "id": 123
}
```

```json
{
  "error": "ApiConfig not found",
  "id": 456
}
```

### 400 Validation Error (Field-level)

When request body fails `@Valid` constraints:

```json
{
  "error": "Validation failed",
  "violations": [
    { "field": "name", "message": "must not be blank" },
    { "field": "url", "message": "size must be between 0 and 1024" }
  ]
}
```

### 400 Invalid HTTP Method

When `method` field contains an unsupported HTTP method:

```json
{
  "error": "Validation failed",
  "message": "Invalid HTTP method: TRACE. Allowed: GET, POST, PUT, DELETE, PATCH"
}
```

### 400 Invalid ID Format

When path variable `{id}` is not a positive integer (e.g., `/api/api-configs/abc`):

```json
{
  "error": "Validation failed",
  "message": "id must be a positive integer"
}
```

### 409 Conflict (Duplicate Name)

When creating/updating an API config with a name that already exists:

```json
{
  "error": "Conflict",
  "message": "An API configuration with name 'Fetch Shipment Status' already exists"
}
```

### 400 Duplicate Response Mapping Variable

When two response mappings use the same `contextVariableName` within one API config:

```json
{
  "error": "Validation failed",
  "message": "Duplicate context_variable_name: 'shipment_status' for this API configuration"
}
```

---

## 4. Session State Machine

The frontend docs cover WebSocket messages, but the **session status lifecycle** is important for understanding reconnection and cleanup behavior:

```
                    ┌─────────────┐
    chat.init       │   pending   │  (Redis only, no DB row yet)
                    └──────┬──────┘
                           │ chat.start
                           ▼
                    ┌─────────────┐
                    │   active    │ ◄──── chat.reconnect (from disconnected)
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
    workflow ends    client drops    24h idle
              │            │            │
              ▼            ▼            ▼
       ┌───────────┐ ┌──────────────┐ ┌─────────┐
       │ completed │ │ disconnected │ │ expired │
       └───────────┘ └──────────────┘ └─────────┘
                           │
                    chat.reconnect
                           │
                    (back to active)
```

| Status | Meaning | What Frontend Can Do |
|--------|---------|---------------------|
| `pending` | Session created via `chat.init`, not yet started | Send `chat.start` |
| `active` | Workflow is running | Send `chat.message`, `chat.back`, `chat.restart` |
| `disconnected` | Client dropped connection | Send `chat.reconnect` to resume |
| `completed` | Workflow finished | Only `chat.restart` works |
| `expired` | Idle >24 hours, cleaned by scheduler | Nothing — start new session |

---

## 5. Pending Session TTL

When `chat.init` creates a session ID, it's stored in Redis as "pending":

- **TTL:** The pending session must be consumed by `chat.start` before it expires
- **Behavior:** If `chat.start` is called with an expired/consumed session ID, you get: `"No active session found"`
- **Recommendation:** Start the workflow promptly after receiving the session ID from `chat.init`

---

## 6. Message Buffer Limits

When a client disconnects and reconnects, the server buffers messages:

| Setting | Value | Meaning |
|---------|-------|---------|
| `sendBufferSize` | 50 | Max messages buffered per client while disconnected |
| `bufferDrainTimeoutSeconds` | 30 | Timeout to drain buffer on reconnect |

**Impact:** If the server sends more than 50 messages while the client is disconnected, older messages are lost. This typically isn't an issue (most workflow interactions are request-response), but rapid auto-advancing message nodes could theoretically overflow the buffer during a disconnect.

---

## 7. CORS Configuration

| Environment | Allowed Origins | Configuration |
|-------------|----------------|---------------|
| dev | `http://localhost:3000` | Hardcoded in `application-dev.properties` |
| staging | `https://staging.xpressbees.com` | Hardcoded in `application-staging.properties` |
| prod | Environment variable `CORS_ALLOWED_ORIGINS` | Set by ops team |

**Methods allowed:** GET, POST, PUT, DELETE, OPTIONS
**Credentials:** Allowed (`allowCredentials = true`)
**Headers:** All headers allowed

If your frontend runs on a different port/domain during development, you'll get CORS errors. Update `cors.allowed-origins` in `application-dev.properties` or pass it as an environment variable.

---

## 8. Placeholder Syntax in API Configs

API config fields (`url`, `headers`, `payloadTemplate`) support `{{variableName}}` placeholders that are resolved at runtime from the chat session context:

```
URL:     https://api.example.com/track/{{awb_number}}
Header:  Authorization: Bearer {{auth_token}}
Body:    { "phone": "{{mobile_number}}", "type": "tracking" }
```

Variables are populated by:
1. **Input nodes** — user's text reply is stored under the node's `variableName`
2. **Response mappings** — JSONPath extractions from previous API calls
3. **Selection nodes** — user's selected option value

---

## 9. Input Validation Behavior

### WebSocket Messages (STOMP)

| Field | DTO Class | Validation | Error if violated |
|-------|-----------|-----------|-------------------|
| `sessionId` in `chat.start` | `ChatStartRequest` | `@NotBlank` | STOMP error frame |
| `sessionId` in `chat.message` | `ChatMessageRequest` | `@NotBlank` | STOMP error frame |
| `message` in `chat.message` | `ChatMessageRequest` | Checked in service layer | `"Non-empty message is required"` |
| `workflowId` in `chat.start` | `ChatStartRequest` | Nullable, checked in service | `"Workflow ID is invalid"` |

### REST API Requests

| Endpoint | Field | Validation | Error |
|----------|-------|-----------|-------|
| `POST/PUT /api/workflows` | `name` | Not blank, max 255 | 400 with violations |
| `POST/PUT /api/workflows` | `workflowJson` | Not null | 400 with violations |
| `POST/PUT /api/api-configs` | `name` | Not blank, max 255, unique | 400 or 409 |
| `POST/PUT /api/api-configs` | `url` | Not blank, max 1024 | 400 with violations |
| `POST/PUT /api/api-configs` | `method` | Not blank, valid HTTP method | 400 |
| `GET/PUT/DELETE /api/api-configs/{id}` | `id` path var | Positive integer | 400 |

---

## 10. WebSocket Resilience Settings Summary

These server-side settings affect frontend behavior:

| Property | Default | Impact on Frontend |
|----------|---------|-------------------|
| `chatbot.websocket.max-connections` | 1000 | Connection rejected if at capacity |
| `chatbot.websocket.inactivity-timeout-minutes` | 30 | Connection closed after 30 min idle |
| `chatbot.websocket.heartbeat-interval-ms` | 10000 | Client must send heartbeats every 10s |
| `chatbot.websocket.send-buffer-size` | 50 | Max 50 messages buffered during disconnect |
| `chatbot.websocket.buffer-drain-timeout-seconds` | 30 | Buffer drain timeout on reconnect |
| `chatbot.cleanup.inactivity-threshold-hours` | 24 | Sessions expired after 24h inactivity |
| `chatbot.shutdown.timeout-seconds` | 30 | Graceful shutdown window |

---

## 11. Date/Time Format

All date fields in REST responses use ISO 8601 format **without timezone** (server local time):

```
"createdAt": "2025-01-15T10:30:00"
"updatedAt": "2025-06-29T14:22:15"
```

Jackson serialization is configured with `write-dates-as-timestamps=false`, so dates are always strings, never Unix timestamps.

---

## Related Documents

- [Frontend Integration Guide](./frontend-integration-guide.md) — WebSocket protocol, node types, message handling
- [WebSocket Resilience & Authentication](./frontend-websocket-resilience.md) — Auth, heartbeats, reconnection, timeouts
- [Production Readiness Changes](./production-readiness-changes.md) — Health checks, Swagger UI, shutdown, session cleanup
