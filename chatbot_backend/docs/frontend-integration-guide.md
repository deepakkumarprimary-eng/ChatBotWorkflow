# Frontend Integration Guide — Chatbot Workflow Engine

## Overview

The chatbot backend communicates with the frontend over **WebSocket (STOMP over SockJS)**. This document covers the full communication protocol, including all API node behavior types.

---

## Connection & Endpoints

| Purpose | Protocol | Endpoint |
|---------|----------|----------|
| WebSocket connection | SockJS | `ws://localhost:8080/ws` |
| Client sends messages | STOMP | Prefix: `/app` |
| Server pushes responses | STOMP | Prefix: `/topic` |

### STOMP Destinations

| Action | Destination | Payload |
|--------|-------------|---------|
| Initialize session | Subscribe to `/app/chat.init` | — (no payload) |
| Start workflow | Send to `/app/chat.start` | `{ sessionId, workflowId }` |
| Send user reply | Send to `/app/chat.message` | `{ sessionId, message }` |
| Receive responses | Subscribe to `/topic/chat/{sessionId}` | Server pushes here |

---

## Session Lifecycle

```
1. Connect to ws://localhost:8080/ws (SockJS)
2. Subscribe to /app/chat.init → receive { sessionId, workflows[] }
3. Subscribe to /topic/chat/{sessionId} → all server messages arrive here
4. Send /app/chat.start with { sessionId, workflowId }
5. Receive messages from server (auto-advancing nodes)
6. When server pauses (input/selection/button) → user replies via /app/chat.message
7. Loop step 5-6 until workflow completes
```

---

## Request Payloads

### Start Workflow

```json
// Send to: /app/chat.start
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "workflowId": 1
}
```

### Send User Reply

```json
// Send to: /app/chat.message
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "user's exact reply here"
}
```

---

## Response Shapes

### Success Response (ChatResponse)

```json
{
  "node": {
    "id": "node-3",
    "name": "Your shipment status is: delivered",
    "type": "state",
    "config": null
  },
  "response": "Your shipment status is: delivered",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "completed": null
}
```

### Completion Response

```json
{
  "node": { "id": "node-5", "name": "Goodbye!", "type": "state" },
  "response": "Goodbye!",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "completed": true
}
```

### Error Response (ChatErrorResponse)

```json
{
  "error": "External API is unreachable (timeout)",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## API Node Behavior Types

### Type 1: Auto-Advance

The API node calls an external service, stores values in context, and automatically moves to the next node. No user interaction required.

**Flow:**
```
Server: [API node processes silently]
Server → Frontend: ChatResponse from the NEXT node (message/state node)
```

**Frontend receives:**
```json
{
  "node": { "id": "node-3", "name": "Your status is: active", "type": "state" },
  "response": "Your status is: active",
  "sessionId": "abc-123",
  "completed": null
}
```

**Frontend action:** Just display the message. No reply needed.

**How to detect:** The response has `node.type != "api"` — the API node was transparent; you only see the next node's output.

---

### Type 2: Conditional Branching

The API node fetches data, evaluates conditions on transitions, and routes to the matching branch. No user interaction required.

**Flow:**
```
Server: [API node processes, evaluates conditions, picks branch]
Server → Frontend: ChatResponse from the matched branch node
```

**Frontend receives:**
```json
{
  "node": { "id": "node-active", "name": "Your order is on the way!", "type": "state" },
  "response": "Your order is on the way!",
  "sessionId": "abc-123",
  "completed": null
}
```

**Frontend action:** Just display the message. No reply needed.

**How to detect:** Same as Type 1 — the frontend doesn't see the API node; only the resolved branch message.

---

### Type 3: Interactive Array Selection

The API returns a list of options. The frontend presents them and the user picks one.

**Flow:**
```
Server → Frontend: ChatResponse with newline-separated options (PAUSED)
Frontend: Display options as a selectable list
User: Picks one option
Frontend → Server: /app/chat.message with exact selected value
Server: Validates, stores selection, continues workflow
Server → Frontend: ChatResponse from the next node
```

**Frontend receives (paused state):**
```json
{
  "node": {
    "id": "node-1",
    "name": "Fetch Available Cities",
    "type": "api",
    "displayVariable": "available_cities",
    "config": { "nodeType": "api", "apiConfigId": "3" }
  },
  "response": "Mumbai\nDelhi\nBangalore\nChennai",
  "sessionId": "abc-123",
  "completed": null
}
```

**Frontend action:**
1. Split `response` by `\n` to get individual options: `["Mumbai", "Delhi", "Bangalore", "Chennai"]`
2. Display as a selectable list (radio buttons, dropdown, clickable items)
3. On user selection, send the **exact value** (case-sensitive)

**Frontend sends:**
```json
{
  "sessionId": "abc-123",
  "message": "Delhi"
}
```

**Validation rules:**
- Must be an **exact case-sensitive match** of one of the displayed options
- `"delhi"` would fail (wrong case)
- `"Del"` would fail (partial match)

**On invalid selection, server responds with error (stays paused):**
```json
{
  "error": "'delhi' is not in the available options",
  "sessionId": "abc-123"
}
```

**How to detect:** `node.type == "api"` AND `node.displayVariable` exists AND `response` contains `\n`.

---

### Button Node

The API node processes, then presents multiple named paths. The user clicks a button to choose their path.

**Flow:**
```
Server → Frontend: ChatResponse with newline-separated button labels (PAUSED)
Frontend: Display as buttons
User: Clicks a button
Frontend → Server: /app/chat.message with exact button label
Server: Validates, routes to matching target node, continues
Server → Frontend: ChatResponse from the chosen path
```

**Frontend receives (paused state):**
```json
{
  "node": {
    "id": "node-1",
    "name": "Welcome! How can I help you?",
    "type": "api",
    "config": { "nodeType": "api", "apiConfigId": "4" }
  },
  "response": "Track Shipment\nRaise Complaint\nSchedule Pickup",
  "sessionId": "abc-123",
  "completed": null
}
```

**Frontend action:**
1. Split `response` by `\n`: `["Track Shipment", "Raise Complaint", "Schedule Pickup"]`
2. Display as clickable buttons
3. On click, send the **exact button label** (case-sensitive)

**Frontend sends:**
```json
{
  "sessionId": "abc-123",
  "message": "Track Shipment"
}
```

**Validation rules:**
- Must be an **exact case-sensitive match** of one of the button labels
- These labels are the `name` fields of the target nodes in the workflow

**On invalid selection:**
```json
{
  "error": "'track shipment' is not a valid selection",
  "sessionId": "abc-123"
}
```

**How to detect:** `node.type == "api"` AND `node.displayVariable` does NOT exist AND `response` contains `\n`.

---

### Input Node (existing, non-API)

A standard input node that pauses for free-text user input.

**Frontend receives:**
```json
{
  "node": {
    "id": "node-2",
    "name": "Please enter your AWB number",
    "type": "state",
    "config": { "nodeType": "input" }
  },
  "response": "Please enter your AWB number",
  "sessionId": "abc-123",
  "completed": null
}
```

**Frontend sends:**
```json
{
  "sessionId": "abc-123",
  "message": "XB12345678"
}
```

**How to detect:** `node.type == "state"` AND `node.config.nodeType == "input"`.

---

## Response Type Detection Logic

Use this decision tree to determine how to render each server message:

```
if (response.error) {
    → Show error notification/toast
}
else if (response.completed == true) {
    → Show completion message, disable input
}
else if (response.node.type == "api" && response.node.displayVariable) {
    → INTERACTIVE LIST: split response by \n, show as selectable options
}
else if (response.node.type == "api" && response.response.includes("\n")) {
    → BUTTON NODE: split response by \n, show as buttons
}
else if (response.node.type == "state" && response.node.config?.nodeType == "input") {
    → INPUT NODE: show text input field
}
else {
    → MESSAGE NODE: just display the response text
}
```

---

## Error Messages Reference

| Error Message | Cause | User Action |
|---------------|-------|-------------|
| `External API is unreachable (timeout)` | API call timed out after retries | Retry or contact support |
| `External API call failed with status: {code}` | API returned HTTP error | Retry or contact support |
| `Invalid response format: body is not valid JSON` | API returned non-JSON | Contact support |
| `No matching transition found for current context` | No condition matched | Contact support (workflow config issue) |
| `'{value}' is not in the available options` | Invalid Type 3 selection | Pick a valid option from the list |
| `'{value}' is not a valid selection` | Invalid button selection | Click a valid button |
| `API configuration reference is missing from the node` | Workflow misconfigured | Contact support |
| `No API configuration found for ID: {id}` | ApiConfig not in DB | Contact support |
| `API configuration identifier is invalid` | apiConfigId not a number | Contact support |
| `No options available` | API returned empty array | Workflow auto-advances |
| `Non-empty message is required` | Empty message sent | Enter a value |
| `Session is not awaiting input` | Message sent at wrong time | Wait for a pause state |
| `Session is already completed` | Workflow finished | Start a new session |

---

## Sample Full Conversation (Type 3 + Conditional)

```
1. Frontend → /app/chat.start: { sessionId: "s1", workflowId: 1 }

2. Server → /topic/chat/s1:  (message node)
   { response: "Welcome! Please enter your mobile number", node.config.nodeType: "input" }

3. Frontend → /app/chat.message: { sessionId: "s1", message: "9876543210" }

4. Server → /topic/chat/s1:  (API node, Type 3 - interactive)
   { response: "Order #1001\nOrder #1002\nOrder #1003", node.type: "api", node.displayVariable: "orders" }

5. Frontend → /app/chat.message: { sessionId: "s1", message: "Order #1002" }

6. Server → /topic/chat/s1:  (next API node processes conditionally, Type 2)
   { response: "Your order #1002 is out for delivery!", node.type: "state" }

7. Server → /topic/chat/s1:  (workflow ends)
   { response: "Thank you!", completed: true }
```

---

## Workflow JSON Samples

See the companion document for sample workflow JSONs covering each node behavior type.
