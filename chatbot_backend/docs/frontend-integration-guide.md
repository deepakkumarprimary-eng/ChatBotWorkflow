# Frontend Integration Guide — Chatbot Workflow Engine

## Overview

The chatbot backend communicates with the frontend over **WebSocket (STOMP over SockJS)**. This document covers the full communication protocol including all message handlers, node behavior types, navigation features, request/response JSON shapes, and error handling.

---

## Connection & Endpoints

| Purpose | Protocol | Endpoint |
|---------|----------|----------|
| WebSocket connection | SockJS | `ws://localhost:8080/ws` |
| Client sends messages | STOMP | Prefix: `/app` |
| Server pushes responses | STOMP | Prefix: `/topic` |

---

## STOMP Destinations — Complete Reference

| Action | Destination | Direction | Payload |
|--------|-------------|-----------|---------|
| Initialize session | `/app/chat.init` | Subscribe | — (no payload, returns sessionId + workflows) |
| Start workflow | `/app/chat.start` | Send | `{ sessionId, workflowId }` |
| Send user reply | `/app/chat.message` | Send | `{ sessionId, message }` |
| Go back one step | `/app/chat.back` | Send | `{ sessionId }` |
| Restart conversation | `/app/chat.restart` | Send | `{ sessionId }` |
| Receive all responses | `/topic/chat/{sessionId}` | Subscribe | Server pushes here |

---

## Session Lifecycle

```
1. Connect to ws://localhost:8080/ws (SockJS)
2. Subscribe to /app/chat.init
      → receive { sessionId, workflows[] }
3. Subscribe to /topic/chat/{sessionId}
      → all server messages arrive here
4. Send /app/chat.start with { sessionId, workflowId }
5. Receive messages from server (auto-advancing nodes)
6. When server pauses (input/selection/button) → user replies via /app/chat.message
7. User can navigate back via /app/chat.back (returns to previous input)
8. User can restart via /app/chat.restart (resets to beginning)
9. Loop steps 5-8 until workflow completes (response.completed == true)
```

---

## Request Payloads — All Message Handlers

### 1. Initialize Session (Subscribe)

```javascript
// Subscribe to: /app/chat.init
// No payload required — just subscribe
stompClient.subscribe('/app/chat.init', (frame) => {
    const data = JSON.parse(frame.body);
    // data = { sessionId: "uuid", workflows: [{id: 1, name: "..."}, ...] }
});
```

**Response:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "workflows": [
    { "id": 1, "name": "Order Tracking" },
    { "id": 2, "name": "Complaint Registration" }
  ]
}
```

---

### 2. Start Workflow

```json
// Send to: /app/chat.start
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "workflowId": 1
}
```

**What happens:** The server loads the workflow and begins processing from the first node. Responses arrive on `/topic/chat/{sessionId}`.

---

### 3. Send User Reply

```json
// Send to: /app/chat.message
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "user's exact reply here"
}
```

**When to use:** After the server pauses at an input node, interactive selection, or button node.

---

### 4. Go Back (Navigate to Previous Input)

```json
// Send to: /app/chat.back
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**What happens:**
- Server finds the most recent input/interactive node the user answered
- Removes that entry and everything after it from history
- Sends the prompt of that node back to the user (same as when it was first shown)
- User can now re-answer with a different value via `/app/chat.message`
- Works across workflow boundaries (navigates into parent workflow if needed)
- Moves exactly one input step per request (call multiple times to go back further)

**When to use:** When the user wants to correct a previous answer.

**Possible errors:**
- `"No active session found"` — invalid sessionId
- `"Session is already completed"` — workflow already finished
- `"No previous input to go back to"` — at the beginning, nothing to go back to

---

### 5. Restart Conversation

```json
// Send to: /app/chat.restart
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**What happens:**
- Server clears all user-entered data
- Resets to the root workflow's first node
- Re-runs the entire workflow from the beginning
- Same sessionId is retained (no need to reconnect or re-subscribe)
- Works even if the session was previously completed (reactivates it)
- After restart, the server pushes normal node responses as if `chat.start` was called fresh

**When to use:** When the user wants to start over from scratch.

**Possible errors:**
- `"No active session found"` — invalid sessionId
- `"Workflow not found"` — root workflow was deleted from DB
- `"Workflow has no starting node"` — workflow has no entry point

---

## Response Shapes

All server responses arrive on `/topic/chat/{sessionId}`. There are two response types:

### ChatResponse (Success)

```json
{
  "node": {
    "id": "node-3",
    "name": "Your shipment status is: delivered",
    "type": "state",
    "config": { "nodeType": "input" }
  },
  "response": "Your shipment status is: delivered",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "completed": null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `node` | Object | The workflow node that produced this response |
| `node.id` | String | Unique node identifier |
| `node.name` | String | Node label/prompt text |
| `node.type` | String | Node visual type: `"state"` or `"api"` |
| `node.config` | Object/null | Node configuration (contains `nodeType`, `apiConfigId`, etc.) |
| `node.displayVariable` | String/undefined | Present only on interactive API list nodes |
| `response` | String | The actual message text to display |
| `sessionId` | String | Session identifier |
| `completed` | Boolean/null | `true` when workflow is done, `null` otherwise |

### ChatErrorResponse (Error)

```json
{
  "error": "No active session found",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `error` | String | Human-readable error message |
| `sessionId` | String | Session identifier |

---

## Node Types & Frontend Rendering

### Message Node (Auto-advance)

The server sends a message and automatically moves to the next node. No user action needed.

**How to detect:** `node.type == "state"` AND `node.config.nodeType != "input"` (or `config` is null)

**Frontend receives:**
```json
{
  "node": { "id": "node-1", "name": "Welcome to Xpressbees!", "type": "state", "config": null },
  "response": "Welcome to Xpressbees!",
  "sessionId": "abc-123",
  "completed": null
}
```

**Frontend action:** Display the message. No reply needed. More messages may follow immediately.

---

### Input Node (Pause for Free-Text Input)

The server pauses and waits for the user to type a response.

**How to detect:** `node.type == "state"` AND `node.config.nodeType == "input"`

**Frontend receives:**
```json
{
  "node": {
    "id": "node-2",
    "name": "Please enter your AWB number",
    "type": "state",
    "config": { "nodeType": "input", "variableName": "awb_number" }
  },
  "response": "Please enter your AWB number",
  "sessionId": "abc-123",
  "completed": null
}
```

**Frontend action:**
1. Display the prompt text
2. Show a text input field
3. On submit, send the user's text via `/app/chat.message`

**Frontend sends:**
```json
{ "sessionId": "abc-123", "message": "XB12345678" }
```

---

### API Node — Auto-Advance (Transparent)

The API node calls an external service, stores values in context, and automatically moves to the next node. The frontend never sees this node directly.

**How to detect:** You won't see it — only the next node's response arrives.

**Frontend action:** Nothing. Just display whatever response comes next.

---

### API Node — Conditional Branching (Transparent)

The API node fetches data, evaluates conditions, and routes to the matching branch. The frontend never sees this node directly.

**How to detect:** Same as above — you only see the resolved branch message.

**Frontend action:** Nothing. Display the next response.

---

### API Node — Interactive Array Selection (Pause)

The API returns a list of options. The user picks one.

**How to detect:** `node.type == "api"` AND `node.displayVariable` exists AND `response` contains `\n`

**Frontend receives:**
```json
{
  "node": {
    "id": "node-5",
    "name": "Select your city",
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
1. Split `response` by `\n` → `["Mumbai", "Delhi", "Bangalore", "Chennai"]`
2. Display as selectable list (radio buttons, dropdown, clickable cards)
3. On selection, send the **exact value** (case-sensitive)

**Frontend sends:**
```json
{ "sessionId": "abc-123", "message": "Delhi" }
```

**Validation:** Must be an exact case-sensitive match. `"delhi"` or `"Del"` would fail.

---

### API Node — Button Options (Pause)

Multiple named paths are presented as buttons. The user clicks one.

**How to detect:** `node.type == "api"` AND `node.displayVariable` does NOT exist AND `response` contains `\n`

**Frontend receives:**
```json
{
  "node": {
    "id": "node-1",
    "name": "How can I help you?",
    "type": "api",
    "config": { "nodeType": "api", "apiConfigId": "4" }
  },
  "response": "Track Shipment\nRaise Complaint\nSchedule Pickup",
  "sessionId": "abc-123",
  "completed": null
}
```

**Frontend action:**
1. Split `response` by `\n` → `["Track Shipment", "Raise Complaint", "Schedule Pickup"]`
2. Display as clickable buttons
3. On click, send the **exact button label** (case-sensitive)

**Frontend sends:**
```json
{ "sessionId": "abc-123", "message": "Track Shipment" }
```

---

### Completion Response

**How to detect:** `response.completed == true`

```json
{
  "node": { "id": "node-10", "name": "Thank you!", "type": "state" },
  "response": "Thank you!",
  "sessionId": "abc-123",
  "completed": true
}
```

**Frontend action:** Display the final message. Disable input. Optionally show a "Start Over" button that calls `/app/chat.restart`.

---

## Response Type Detection Logic (Complete)

```javascript
function handleServerMessage(data) {
    if (data.error) {
        // ERROR: Show error notification/toast
        showError(data.error);
    }
    else if (data.completed === true) {
        // COMPLETED: Show final message, disable input, show restart option
        displayMessage(data.response);
        showRestartButton();
    }
    else if (data.node.type === "api" && data.node.displayVariable) {
        // INTERACTIVE LIST: Split by \n, show as selectable options
        const options = data.response.split("\n");
        showSelectionList(options);
    }
    else if (data.node.type === "api" && data.response.includes("\n")) {
        // BUTTON NODE: Split by \n, show as buttons
        const buttons = data.response.split("\n");
        showButtons(buttons);
    }
    else if (data.node.type === "state" && data.node.config?.nodeType === "input") {
        // INPUT NODE: Show text input field
        displayMessage(data.response);
        showTextInput();
    }
    else {
        // MESSAGE NODE: Just display, no interaction needed
        displayMessage(data.response);
    }
}
```

---

## Navigation Features (Back & Restart)

### Back Navigation — Detailed Behavior

| Aspect | Behavior |
|--------|----------|
| Granularity | Goes back exactly one user-input step per call |
| Scope | Skips message/auto-advance nodes — only stops at input or interactive nodes |
| Cross-workflow | Can navigate back into parent workflow if current child has no previous inputs |
| History | Server truncates navigation history (removes the target and everything after) |
| Context | User's previous answer for that node is NOT cleared until they provide a new one |
| Response | Server re-sends the prompt of the target input node |
| After back | Frontend should show the input/selection UI again for the user to re-answer |

**Typical flow:**
```
1. User answers "Delhi" at city selection
2. User answers "XB123" at AWB input
3. User clicks "Back" button → Frontend sends /app/chat.back
4. Server responds with the city selection prompt again (same as step 1)
5. User can now pick a different city
6. Workflow continues from there
```

### Restart — Detailed Behavior

| Aspect | Behavior |
|--------|----------|
| Context clearing | All user-entered values are wiped clean |
| Session | Same sessionId is kept (no reconnection needed) |
| Workflow | Resets to the root workflow's first node |
| Completed sessions | Reactivated to "active" status |
| Response | Server re-processes the entire workflow from node 1 |
| After restart | Frontend receives the same sequence of messages as a fresh start |

---

## Sample JavaScript Client (SockJS + STOMP)

```javascript
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

let stompClient = null;
let sessionId = null;

function connect() {
    const socket = new SockJS('http://localhost:8080/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, () => {
        // Step 1: Initialize session
        stompClient.subscribe('/app/chat.init', (frame) => {
            const data = JSON.parse(frame.body);
            sessionId = data.sessionId;

            // Step 2: Subscribe to responses
            stompClient.subscribe(`/topic/chat/${sessionId}`, (message) => {
                const response = JSON.parse(message.body);
                handleServerMessage(response);
            });

            // Display available workflows for user to choose
            showWorkflowList(data.workflows);
        });
    });
}

function startWorkflow(workflowId) {
    stompClient.send('/app/chat.start', {}, JSON.stringify({
        sessionId: sessionId,
        workflowId: workflowId
    }));
}

function sendMessage(text) {
    stompClient.send('/app/chat.message', {}, JSON.stringify({
        sessionId: sessionId,
        message: text
    }));
}

function goBack() {
    stompClient.send('/app/chat.back', {}, JSON.stringify({
        sessionId: sessionId
    }));
}

function restart() {
    stompClient.send('/app/chat.restart', {}, JSON.stringify({
        sessionId: sessionId
    }));
}
```

---

## Complete Error Messages Reference

### Navigation Errors

| Error Message | Trigger | When |
|---------------|---------|------|
| `No active session found` | `chat.back` or `chat.restart` with invalid sessionId | Always check sessionId validity |
| `Session is already completed` | `chat.back` on a finished session | Use `chat.restart` instead |
| `No previous input to go back to` | `chat.back` at the very start (no inputs answered yet) | Disable back button when at start |
| `Workflow not found` | `chat.restart` when root workflow was deleted | Rare — DB issue |
| `Workflow has no starting node` | `chat.restart` when workflow has no entry node | Rare — workflow config issue |

### Input Errors

| Error Message | Trigger | When |
|---------------|---------|------|
| `Non-empty message is required` | `chat.message` with empty/blank text | Validate before sending |
| `Session is not awaiting input` | `chat.message` when no input pause is active | Only send after pause |
| `Session is already completed` | `chat.message` after workflow ended | Start new session or restart |
| `'{value}' is not in the available options` | Invalid interactive list selection | Send exact option text |
| `'{value}' is not a valid selection` | Invalid button selection | Send exact button label |

### API/System Errors

| Error Message | Trigger | When |
|---------------|---------|------|
| `External API is unreachable (timeout)` | API call timed out after retries | Transient — retry later |
| `External API call failed with status: {code}` | API returned HTTP 4xx/5xx | Check API availability |
| `Invalid response format: body is not valid JSON` | API returned non-JSON body | Contact support |
| `No matching transition found for current context` | No condition matched on branch | Workflow config issue |
| `API configuration reference is missing from the node` | Node missing apiConfigId | Workflow config issue |
| `No API configuration found for ID: {id}` | ApiConfig record not in DB | Contact support |
| `API configuration identifier is invalid` | apiConfigId is not a number | Workflow config issue |
| `No options available` | API returned empty array for selection | Workflow auto-advances |
| `Failed to persist session state` | Database save failed | Transient — retry |

---

## UI Recommendations

### Back Button
- Show a "Back" button whenever the user is at an input/selection pause
- Disable it when there are no previous inputs (server will error, but better to prevent)
- After pressing back, clear the current input UI and wait for the server's prompt response
- Stack multiple back presses to step through history incrementally

### Restart Button
- Show a "Restart" or "Start Over" button at all times (or at least after completion)
- After restart, clear the entire chat history in the UI since the server replays from scratch
- No need to reconnect WebSocket or re-subscribe — same session continues

### Error Handling
- Display error messages in a non-blocking way (toast/snackbar)
- Don't clear the current UI state on error — the session remains at the same position
- For validation errors (wrong selection), re-show the options so user can try again

---

## Sample Full Conversation (with Back & Restart)

```
 1. Frontend subscribes to /app/chat.init
    ← { sessionId: "s1", workflows: [{id:1, name:"Order Tracking"}] }

 2. Frontend subscribes to /topic/chat/s1

 3. Frontend → /app/chat.start: { sessionId: "s1", workflowId: 1 }

 4. Server → /topic/chat/s1: (message node, auto-advance)
    { response: "Welcome to Order Tracking!", node: {type:"state", config:null}, completed: null }

 5. Server → /topic/chat/s1: (input node, PAUSED)
    { response: "Please enter your mobile number", node: {type:"state", config:{nodeType:"input"}}, completed: null }

 6. Frontend → /app/chat.message: { sessionId: "s1", message: "9876543210" }

 7. Server → /topic/chat/s1: (API node interactive, PAUSED)
    { response: "Order #1001\nOrder #1002\nOrder #1003", node: {type:"api", displayVariable:"orders"}, completed: null }

 8. User realizes wrong phone number! Clicks "Back" twice.
    Frontend → /app/chat.back: { sessionId: "s1" }

 9. Server → /topic/chat/s1: (re-shows the selection that was just answered)
    { response: "Order #1001\nOrder #1002\nOrder #1003", node: {type:"api", displayVariable:"orders"}, completed: null }
    Wait — user wants to go back further.

    Frontend → /app/chat.back: { sessionId: "s1" }

10. Server → /topic/chat/s1: (re-shows the phone input)
    { response: "Please enter your mobile number", node: {type:"state", config:{nodeType:"input"}}, completed: null }

11. Frontend → /app/chat.message: { sessionId: "s1", message: "9123456789" }

12. Server → /topic/chat/s1: (API processes, shows new orders)
    { response: "Order #2001\nOrder #2002", node: {type:"api", displayVariable:"orders"}, completed: null }

13. Frontend → /app/chat.message: { sessionId: "s1", message: "Order #2001" }

14. Server → /topic/chat/s1: (conditional branch resolves)
    { response: "Order #2001 is out for delivery!", node: {type:"state"}, completed: null }

15. Server → /topic/chat/s1: (workflow ends)
    { response: "Thank you!", completed: true }

16. User clicks "Start Over"
    Frontend → /app/chat.restart: { sessionId: "s1" }

17. Server → /topic/chat/s1: (replays from beginning)
    { response: "Welcome to Order Tracking!", node: {type:"state", config:null}, completed: null }

18. Server → /topic/chat/s1: (input node again)
    { response: "Please enter your mobile number", node: {type:"state", config:{nodeType:"input"}}, completed: null }

    ... (conversation continues fresh)
```

---

## Workflow JSON Structure (for reference)

Workflows are stored as JSONB in the database. Here's the structure:

```json
{
  "nodes": [
    {
      "id": "node-1",
      "name": "Welcome!",
      "type": "state",
      "config": null
    },
    {
      "id": "node-2",
      "name": "Enter your AWB number",
      "type": "state",
      "config": { "nodeType": "input", "variableName": "awb_number" }
    },
    {
      "id": "node-3",
      "name": "Fetch Status",
      "type": "api",
      "config": { "nodeType": "api", "apiConfigId": "1" }
    }
  ],
  "transitions": [
    { "sourceNodeId": "node-1", "targetNodeId": "node-2" },
    { "sourceNodeId": "node-2", "targetNodeId": "node-3" }
  ],
  "entryNodeId": "node-1"
}
```

### Node Config Shapes

**Message node (auto-advance):**
```json
{ "id": "node-1", "name": "Hello!", "type": "state", "config": null }
```

**Input node (pauses for text):**
```json
{
  "id": "node-2", "name": "Enter AWB", "type": "state",
  "config": { "nodeType": "input", "variableName": "awb_number" }
}
```

**API node (external call):**
```json
{
  "id": "node-3", "name": "Fetch Data", "type": "api",
  "config": { "nodeType": "api", "apiConfigId": "1" }
}
```

**API node with interactive display:**
```json
{
  "id": "node-4", "name": "Select City", "type": "api",
  "displayVariable": "available_cities",
  "config": { "nodeType": "api", "apiConfigId": "3" }
}
```

**Workflow node (enters child workflow):**
```json
{
  "id": "node-5", "name": "Sub-flow", "type": "state",
  "config": { "nodeType": "workflow", "workflowId": "2" }
}
```

---

## REST API Endpoints (Workflow & ApiConfig CRUD)

These are standard REST endpoints for managing workflow definitions and API configurations.

### Workflows

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/workflows` | List all workflows |
| GET | `/api/workflows/{id}` | Get workflow by ID |
| POST | `/api/workflows` | Create new workflow |
| PUT | `/api/workflows/{id}` | Update workflow |
| DELETE | `/api/workflows/{id}` | Delete workflow |

### API Configs

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/api-configs` | List all API configs |
| GET | `/api/api-configs/{id}` | Get API config by ID |
| POST | `/api/api-configs` | Create new API config |
| PUT | `/api/api-configs/{id}` | Update API config |
| DELETE | `/api/api-configs/{id}` | Delete API config |

---

## Quick Reference Card

| Want to... | Send to | Payload |
|------------|---------|---------|
| Get session + workflow list | Subscribe `/app/chat.init` | — |
| Start a workflow | `/app/chat.start` | `{ sessionId, workflowId }` |
| Reply to input/selection | `/app/chat.message` | `{ sessionId, message }` |
| Go back one step | `/app/chat.back` | `{ sessionId }` |
| Start over completely | `/app/chat.restart` | `{ sessionId }` |
| Listen for responses | Subscribe `/topic/chat/{sessionId}` | — |
