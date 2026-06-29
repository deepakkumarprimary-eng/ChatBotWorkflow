# Frontend Integration: WebSocket Resilience & Authentication

This document covers the frontend changes required for the WebSocket resilience and authentication features added to the chatbot backend. It supplements the existing [frontend-integration-guide.md](./frontend-integration-guide.md).

---

## 1. WebSocket Authentication (API Key)

All WebSocket connections now require an API key. Connections without a valid key are rejected immediately with a STOMP ERROR frame.

### How to Authenticate

Pass the API key as a STOMP native header during the CONNECT frame:

```javascript
stompClient.connect(
  { 'X-API-Key': 'your-api-key-here' },
  onConnectSuccess,
  onConnectError
);
```

**Alternative:** Pass as `apiKey` header (for clients that don't support custom header names):

```javascript
stompClient.connect(
  { 'apiKey': 'your-api-key-here' },
  onConnectSuccess,
  onConnectError
);
```

### API Key Values by Environment

| Environment | Key Source | Example |
|-------------|-----------|---------|
| Development | Hardcoded in `application-dev.properties` | `dev-api-key-12345` |
| Staging | Hardcoded in `application-staging.properties` | `staging-api-key-67890` |
| Production | Environment variable `WEBSOCKET_API_KEY` | Set by ops team |

### Authentication Errors

| Error Message | Cause | Frontend Action |
|---|---|---|
| `WebSocket authentication failed: API key is missing.` | No `X-API-Key` or `apiKey` header sent | Add the header to CONNECT frame |
| `WebSocket authentication failed: Invalid API key.` | Wrong key value | Check key matches the environment |

---

## 2. Heartbeat Configuration

The server now enforces STOMP heartbeats at 10-second intervals. Clients must respond to server pings or the connection will be considered dead and closed.

### Client Configuration

```javascript
// Using @stomp/stompjs
const stompClient = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  heartbeatIncoming: 10000,  // expect server ping every 10s
  heartbeatOutgoing: 10000,  // send client ping every 10s
  connectHeaders: { 'X-API-Key': 'your-api-key-here' }
});
```

```javascript
// Using stomp.js over SockJS
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);
stompClient.heartbeat.outgoing = 10000;
stompClient.heartbeat.incoming = 10000;
```

**Do NOT** disable heartbeats (`{ incoming: 0, outgoing: 0 }`). If the client stops sending heartbeats, the server will close the connection after the tolerance period.

---

## 3. Reconnection Protocol (New Endpoint)

When a client disconnects unexpectedly (network drop, page refresh, etc.), it can resume the conversation without losing progress.

### New STOMP Destination

| Action | Destination | Direction | Payload |
|--------|-------------|-----------|---------|
| Reconnect session | `/app/chat.reconnect` | Send | `{ "sessionId": "..." }` |

### Reconnection Flow

```
1. Client detects WebSocket disconnection
2. Client reconnects to ws://localhost:8080/ws (with API key)
3. Client subscribes to /topic/chat/{storedSessionId}
4. Client sends /app/chat.reconnect with { sessionId: storedSessionId }
5. If successful: server re-sends the last prompt/question
6. Client renders the prompt and waits for user input (as normal)
7. Conversation continues from where it left off
```

### Implementation Example

```javascript
let sessionId = null;

function connect() {
  const socket = new SockJS('http://localhost:8080/ws');
  stompClient = Stomp.over(socket);
  stompClient.heartbeat.outgoing = 10000;
  stompClient.heartbeat.incoming = 10000;

  stompClient.connect({ 'X-API-Key': API_KEY }, () => {
    // Check if we have a stored session to reconnect
    const storedSessionId = localStorage.getItem('chatSessionId');

    if (storedSessionId) {
      reconnect(storedSessionId);
    } else {
      initNewSession();
    }
  }, onDisconnect);
}

function initNewSession() {
  stompClient.subscribe('/app/chat.init', (frame) => {
    const data = JSON.parse(frame.body);
    sessionId = data.sessionId;
    localStorage.setItem('chatSessionId', sessionId);

    stompClient.subscribe(`/topic/chat/${sessionId}`, handleMessage);
    showWorkflowList(data.workflows);
  });
}

function reconnect(storedSessionId) {
  sessionId = storedSessionId;

  stompClient.subscribe(`/topic/chat/${sessionId}`, handleMessage);

  stompClient.send('/app/chat.reconnect', {}, JSON.stringify({
    sessionId: storedSessionId
  }));
}

function onDisconnect() {
  // Auto-reconnect with exponential backoff
  let delay = 1000;
  const maxDelay = 30000;

  function attempt() {
    setTimeout(() => {
      try {
        connect();
      } catch (e) {
        delay = Math.min(delay * 2, maxDelay);
        attempt();
      }
    }, delay);
  }

  attempt();
}
```

### Reconnection Responses

**On success:** The server sends the last prompt/question to `/topic/chat/{sessionId}` — same format as a normal `ChatResponse`. The frontend should render it and show the appropriate input UI.

**On failure:** The server sends a `ChatErrorResponse` to `/topic/chat/{sessionId}`:

| Error Message | Meaning | Frontend Action |
|---|---|---|
| `Session not found` | Session ID doesn't exist in DB | Clear stored sessionId, start fresh |
| `Session has already completed` | Workflow finished before reconnect | Clear stored sessionId, offer new session |
| `Session is already active on another connection` | Duplicate tab/device | Show "active elsewhere" notice |
| `Reconnection failed` | Server couldn't re-send the prompt | Clear stored sessionId, start fresh |

### When to Clear Stored Session

```javascript
function handleMessage(frame) {
  const data = JSON.parse(frame.body);

  if (data.error) {
    if (['Session not found', 'Session has already completed', 'Reconnection failed']
        .includes(data.error)) {
      localStorage.removeItem('chatSessionId');
      initNewSession();
      return;
    }
  }

  if (data.completed === true) {
    localStorage.removeItem('chatSessionId');
  }

  // ... normal message handling
}
```

---

## 4. Connection Limit Handling

The server enforces a maximum of 1000 concurrent WebSocket connections (configurable). If the limit is reached, new connections are rejected.

### Error on CONNECT

The STOMP ERROR frame arrives immediately on connect attempt:

```
ERROR
message: Maximum connections reached
```

### Frontend Handling

```javascript
stompClient.connect({ 'X-API-Key': API_KEY }, onSuccess, (error) => {
  if (error.headers && error.headers.message === 'Maximum connections reached') {
    showNotification('Server is busy. Please try again in a moment.');
    // Retry after delay
    setTimeout(connect, 5000);
  }
});
```

---

## 5. Inactivity Timeout

Connections idle for 30 minutes (no `chat.start`, `chat.message`, `chat.back`, or `chat.restart` sent) are automatically closed by the server.

### Timeout Notification

Before closing, the server sends a message to `/topic/chat/{sessionId}`:

```
"Session timed out due to inactivity"
```

**Note:** This arrives as a plain string, not the usual JSON `ChatResponse` format. Handle it as a special case.

### Frontend Handling

```javascript
function handleMessage(frame) {
  const body = frame.body;

  // Check for plain-text timeout message
  if (body === '"Session timed out due to inactivity"' || 
      body === 'Session timed out due to inactivity') {
    showNotification('Your session timed out due to inactivity.');
    localStorage.removeItem('chatSessionId');
    showReconnectOption();
    return;
  }

  // Normal JSON parsing
  const data = JSON.parse(body);
  // ... handle as usual
}
```

### Preventing Timeout

If you want to keep a session alive during long user idle periods:
- Sending any application message (`chat.message`, `chat.back`, `chat.restart`) resets the timer
- STOMP heartbeats do NOT reset the timer — only real user actions count

---

## 6. Updated Quick Reference

| Want to... | Send to | Payload | Headers |
|---|---|---|---|
| Connect (authenticate) | SockJS `/ws` | — | `X-API-Key: {key}` |
| Get session + workflows | Subscribe `/app/chat.init` | — | — |
| Start a workflow | `/app/chat.start` | `{ sessionId, workflowId }` | — |
| Reply to input/selection | `/app/chat.message` | `{ sessionId, message }` | — |
| Go back one step | `/app/chat.back` | `{ sessionId }` | — |
| Start over completely | `/app/chat.restart` | `{ sessionId }` | — |
| **Reconnect session** | `/app/chat.reconnect` | `{ sessionId }` | — |
| Listen for responses | Subscribe `/topic/chat/{sessionId}` | — | — |

---

## 7. New Error Messages (Complete List)

These are new errors introduced by the resilience features:

| Error Message | Source | When |
|---|---|---|
| `WebSocket authentication failed: API key is missing.` | CONNECT | No API key header |
| `WebSocket authentication failed: Invalid API key.` | CONNECT | Wrong API key |
| `Maximum connections reached` | CONNECT | Server at capacity |
| `Session timed out due to inactivity` | Server push | 30 min idle |
| `Session not found` | `chat.reconnect` | Invalid session ID |
| `Session has already completed` | `chat.reconnect` | Workflow already done |
| `Session is already active on another connection` | `chat.reconnect` | Duplicate connection |
| `Reconnection failed` | `chat.reconnect` | Server couldn't resume |

---

## 8. Full Example: Connect with Auth + Auto-Reconnect

```javascript
import SockJS from 'sockjs-client';
import { Stomp } from '@stomp/stompjs';

const API_KEY = 'dev-api-key-12345'; // Use env-appropriate key
const WS_URL = 'http://localhost:8080/ws';

let stompClient = null;
let sessionId = null;
let reconnectDelay = 1000;

function connect() {
  const socket = new SockJS(WS_URL);
  stompClient = Stomp.over(socket);
  stompClient.heartbeat.outgoing = 10000;
  stompClient.heartbeat.incoming = 10000;

  stompClient.connect(
    { 'X-API-Key': API_KEY },
    onConnected,
    onError
  );
}

function onConnected() {
  reconnectDelay = 1000; // Reset backoff on success

  const stored = localStorage.getItem('chatSessionId');
  if (stored) {
    // Resume existing session
    sessionId = stored;
    stompClient.subscribe(`/topic/chat/${sessionId}`, onMessage);
    stompClient.send('/app/chat.reconnect', {}, JSON.stringify({ sessionId }));
  } else {
    // Start fresh
    stompClient.subscribe('/app/chat.init', (frame) => {
      const data = JSON.parse(frame.body);
      sessionId = data.sessionId;
      localStorage.setItem('chatSessionId', sessionId);
      stompClient.subscribe(`/topic/chat/${sessionId}`, onMessage);
      showWorkflowList(data.workflows);
    });
  }
}

function onMessage(frame) {
  const body = frame.body;

  // Handle plain-text timeout message
  if (body === '"Session timed out due to inactivity"') {
    handleTimeout();
    return;
  }

  const data = JSON.parse(body);

  // Handle reconnection errors
  if (data.error) {
    if (['Session not found', 'Session has already completed', 'Reconnection failed']
        .includes(data.error)) {
      localStorage.removeItem('chatSessionId');
      sessionId = null;
      initNewSession();
      return;
    }
    showError(data.error);
    return;
  }

  // Clear session on completion
  if (data.completed === true) {
    localStorage.removeItem('chatSessionId');
  }

  handleServerMessage(data);
}

function onError(error) {
  const msg = error?.headers?.message || error?.body || '';

  if (msg.includes('Maximum connections reached')) {
    showNotification('Server is busy. Retrying...');
  } else if (msg.includes('authentication failed')) {
    showNotification('Authentication failed. Check API key.');
    return; // Don't retry auth failures
  }

  // Exponential backoff reconnect
  setTimeout(() => {
    reconnectDelay = Math.min(reconnectDelay * 2, 30000);
    connect();
  }, reconnectDelay);
}

function handleTimeout() {
  localStorage.removeItem('chatSessionId');
  sessionId = null;
  showNotification('Session timed out. Please start a new conversation.');
}

// Public API
function startWorkflow(workflowId) {
  stompClient.send('/app/chat.start', {}, JSON.stringify({ sessionId, workflowId }));
}

function sendMessage(text) {
  stompClient.send('/app/chat.message', {}, JSON.stringify({ sessionId, message: text }));
}

function goBack() {
  stompClient.send('/app/chat.back', {}, JSON.stringify({ sessionId }));
}

function restart() {
  stompClient.send('/app/chat.restart', {}, JSON.stringify({ sessionId }));
}

// Initialize
connect();
```

---

## 9. Migration Checklist

Frontend teams should verify these changes before deploying:

- [ ] Pass `X-API-Key` header on STOMP CONNECT
- [ ] Configure heartbeats (10s outgoing, 10s incoming)
- [ ] Store `sessionId` in localStorage/sessionStorage after `chat.init`
- [ ] Implement auto-reconnect with exponential backoff on disconnect
- [ ] Send `/app/chat.reconnect` with stored sessionId after reconnecting
- [ ] Handle new error messages (see table in section 7)
- [ ] Handle inactivity timeout plain-text message
- [ ] Handle "Maximum connections reached" on CONNECT failure
- [ ] Clear stored sessionId on completion or unrecoverable errors
- [ ] Test with dev API key: `dev-api-key-12345`
