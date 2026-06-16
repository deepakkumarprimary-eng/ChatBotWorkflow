# Requirements Document

## Introduction

When a browser client establishes a WebSocket connection to the backend, the server automatically sends a list of all available workflows (id + name) to the connected client. This is the first step of the real-time chatbot conversation feature (Phase 2). Protocol: STOMP over WebSocket. Single instance deployment.

## Glossary

- **STOMP**: Simple Text Oriented Messaging Protocol — a sub-protocol over WebSocket providing pub/sub semantics.
- **SockJS**: A browser JavaScript library that provides a WebSocket-like API with fallback transports for browsers that don't support WebSocket natively.
- **Topic**: A STOMP destination that clients subscribe to in order to receive messages.

## Requirements

### Requirement 1: WebSocket Endpoint

**User Story:** As a frontend developer, I want a WebSocket endpoint on the backend, so that my browser application can establish a real-time connection.

#### Acceptance Criteria

1. THE system SHALL expose a STOMP WebSocket endpoint at `/ws` with SockJS fallback enabled for browser compatibility.
2. THE WebSocket endpoint SHALL allow cross-origin connections to support the separate frontend web application.

### Requirement 2: Workflow List on Subscribe

**User Story:** As a frontend developer, I want to receive the list of all available workflows automatically when my browser connects via WebSocket, so that I can present workflow options to the end-user without a separate REST call.

#### Acceptance Criteria

1. WHEN a client subscribes to `/topic/workflows`, THE system SHALL immediately send a JSON array of all workflows (containing `id` and `name` fields only) to that client's session.
2. THE workflow list SHALL be sent only to the subscribing client session, not broadcast to all connected clients.
3. THE system SHALL use the existing `WorkflowRepository` to fetch the workflow data.

### Requirement 3: Response Format

**User Story:** As a frontend developer, I want a simple JSON response containing workflow identifiers and names, so that I can render a selection list without parsing complex structures.

#### Acceptance Criteria

1. THE response payload SHALL be a JSON array where each element contains an `id` (numeric) and `name` (string) field.
2. THE response SHALL NOT include the full workflow JSON payload — only `id` and `name`.

## Technical Decisions

| Decision | Choice |
|----------|--------|
| Protocol | STOMP over WebSocket |
| Endpoint | `/ws` with SockJS |
| Topic | `/topic/workflows` |
| Trigger | Automatic on client subscribe |
| Response | JSON array of `{id, name}` objects |
| Delivery | Per-session (not broadcast) |

## Out of Scope

- Workflow execution via WebSocket
- Conversation persistence
- Typing indicators / intermediate states
- Multi-instance scaling with message broker
- Authentication/authorization on WebSocket connection
