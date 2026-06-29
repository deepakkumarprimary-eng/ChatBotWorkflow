# Production Readiness Changes — Frontend Impact Guide

## Overview

The backend has been updated with production-readiness enhancements. Most changes are **backend-only** (logging, health checks, graceful shutdown, session cleanup). This document covers what's new and what the frontend team needs to know.

---

## What Changed (Summary)

| Feature | Frontend Impact | Action Required |
|---------|----------------|-----------------|
| Health Monitoring (Actuator) | None — ops endpoints only | No |
| Structured Logging | None — backend internals | No |
| Environment Profiles (dev/staging/prod) | None — config management | No |
| Stale Session Cleanup | **Low** — idle sessions get expired | See below |
| Graceful Shutdown | **Low** — new error message possible | See below |
| OpenAPI Documentation (Swagger UI) | **Helpful** — interactive API docs | See below |

---

## 1. New: Swagger UI & OpenAPI Docs

You can now explore all REST endpoints interactively:

| Resource | URL | Available In |
|----------|-----|-------------|
| Swagger UI | `http://localhost:8080/swagger-ui.html` | dev, staging |
| OpenAPI JSON spec | `http://localhost:8080/v3/api-docs` | dev, staging |

- Swagger UI is **disabled in production** for security
- All Workflow and API Config endpoints are documented with request/response schemas
- Use Swagger UI to test API calls directly from the browser during development

---

## 2. Stale Session Cleanup (Affects Frontend)

### What happens
Sessions that are **inactive for 24 hours** are automatically expired by the backend. An expired session will reject further messages.

### Frontend impact
If a user leaves a chat idle for 24+ hours and then tries to resume:

**Error you'll receive:**
```json
{
  "error": "No active session found",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Recommended handling
```javascript
function handleServerMessage(data) {
    if (data.error === "No active session found") {
        // Session expired due to inactivity — start fresh
        showNotification("Your session expired due to inactivity. Starting a new session...");
        reconnect(); // Re-subscribe to /app/chat.init for a new sessionId
    }
}
```

### Key details
- Threshold: 24 hours of inactivity (no messages sent)
- Cleanup runs every 1 hour
- Active conversations are never affected
- If you detect this error, simply reinitialize the session via `/app/chat.init`

---

## 3. Graceful Shutdown (Affects Frontend)

### What happens
When the backend is restarting/deploying, it stops accepting new requests while allowing in-progress conversations to finish (30-second timeout).

### New error message during shutdown
```json
{
  "error": "Service is shutting down, please try again later",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### When you'll see this
- During deployments (rolling restarts)
- Brief window (30 seconds max) during backend updates

### Recommended handling
```javascript
function handleServerMessage(data) {
    if (data.error === "Service is shutting down, please try again later") {
        showNotification("Service is temporarily unavailable. Reconnecting...");
        // Wait a few seconds and reconnect
        setTimeout(() => reconnect(), 5000);
    }
}
```

### Key details
- In-progress conversations will complete normally
- Only NEW requests (chat.start, chat.message) get rejected during shutdown
- The WebSocket connection itself may drop — your existing reconnection logic handles this
- After reconnection, a new session is needed (via `/app/chat.init`)

---

## 4. Health Check Endpoint (For DevOps/Monitoring)

Not needed by frontend directly, but useful for debugging:

```
GET http://localhost:8080/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "diskSpace": { "status": "UP" },
    "webSocketConnections": {
      "status": "UP",
      "details": { "activeConnections": 42, "threshold": 800 }
    },
    "workflowEngine": { "status": "UP" }
  }
}
```

You can use this to show a "service status" indicator if desired:
- `"UP"` → green
- `"DEGRADED"` → yellow (high load)
- `"DOWN"` → red (backend issue)
- `"OUT_OF_SERVICE"` → backend is shutting down

---

## 5. Updated Error Messages Reference

### New errors added

| Error Message | Trigger | Handling |
|---------------|---------|----------|
| `Service is shutting down, please try again later` | Any request during backend shutdown | Retry after 5 seconds, then reconnect |

### Existing errors (unchanged)

All existing errors documented in the [frontend integration guide](./frontend-integration-guide.md) remain the same.

---

## 6. Environment-Specific Behavior

| Environment | Swagger UI | Logging | Session Cleanup |
|-------------|-----------|---------|-----------------|
| dev | ✅ Available | Text (console) | Enabled (can disable) |
| staging | ✅ Available | Text (console) | Enabled |
| prod | ❌ Disabled | JSON (structured) | Enabled |

No frontend code changes needed — this is purely backend configuration.

---

## No Changes Required For

These items are entirely backend concerns with zero frontend impact:

- **Structured JSON Logging** — Correlation IDs in logs for debugging (backend-only)
- **Logback Configuration** — Log formatting per environment
- **HikariCP Pool Tuning** — Database connection management
- **Micrometer Metrics** — JVM/HTTP/DB metrics at `/actuator/metrics`
- **Partial Database Index** — Query performance optimization

---

## Quick Checklist for Frontend Team

- [ ] Add handling for `"Service is shutting down, please try again later"` error
- [ ] Add handling for expired sessions (24h inactivity) → re-initialize via `/app/chat.init`
- [ ] (Optional) Bookmark `http://localhost:8080/swagger-ui.html` for API exploration
- [ ] (Optional) Use `/actuator/health` for a service status indicator

---

## Questions?

The WebSocket protocol, message formats, and all existing endpoints remain **100% unchanged**. The only frontend-visible additions are:
1. One new error message during shutdown
2. Sessions expiring after 24h of inactivity (returns existing "No active session found" error)
3. Swagger UI available for API exploration
