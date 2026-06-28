# Requirements Document

## Introduction

This feature adds resilience mechanisms to the WebSocket layer of the chatbot workflow engine. Currently, the system has no handling for client disconnections, no heartbeat configuration, no connection limits, no back-pressure controls, and no reconnection protocol. This leads to orphaned sessions in the database, undetected dead connections, potential resource exhaustion from unlimited connections, client flooding during rapid node processing, and inability to resume interrupted conversations.

## Glossary

- **WebSocket_Layer**: The Spring WebSocket + STOMP messaging infrastructure responsible for real-time communication between clients and the chatbot workflow engine.
- **Chat_Session**: A JPA entity persisted in PostgreSQL representing an active conversation, including session state, current node, and context data.
- **STOMP_Session**: The underlying STOMP protocol session established when a client connects via SockJS to the `/ws` endpoint.
- **Disconnect_Listener**: A Spring ApplicationListener that detects STOMP SessionDisconnectEvent occurrences and triggers session status updates.
- **Heartbeat**: A periodic STOMP-level ping/pong mechanism used to detect dead connections between server and client.
- **Connection_Registry**: An in-memory registry tracking active WebSocket connections and enforcing concurrency limits.
- **Send_Buffer**: A per-session message queue that holds outbound messages destined for a client, providing back-pressure when the client cannot consume messages fast enough.
- **Back_Pressure_Controller**: The component responsible for pausing workflow processing when a session's Send_Buffer reaches capacity and resuming when buffer space becomes available.
- **Reconnection_Handler**: The component responsible for validating reconnection requests and resuming previously disconnected sessions.
- **Inactivity_Timeout**: The maximum duration a WebSocket connection may remain idle (no messages sent or received) before the server actively closes it.
- **Buffer_Drain_Timeout**: The maximum duration the server waits for a full Send_Buffer to drain before forcibly disconnecting the client.

## Requirements

### Requirement 1: Disconnect Event Detection

**User Story:** As a system operator, I want the server to detect client disconnections and update session status, so that orphaned sessions do not remain "active" indefinitely in the database.

#### Acceptance Criteria

1. WHEN a STOMP SessionDisconnectEvent occurs, THE Disconnect_Listener SHALL identify the associated Chat_Session by mapping the STOMP session ID to the application session ID.
2. WHEN a STOMP SessionDisconnectEvent occurs for a session in "active" status, THE Disconnect_Listener SHALL update the Chat_Session status to "disconnected" in the database.
3. WHEN a STOMP SessionDisconnectEvent occurs, THE Disconnect_Listener SHALL log the event at INFO level including the application session ID, STOMP session ID, and timestamp.
4. IF the Chat_Session cannot be found for a disconnect event, THEN THE Disconnect_Listener SHALL log a warning and take no further action.
5. IF a database error occurs while updating the Chat_Session status, THEN THE Disconnect_Listener SHALL log the error at ERROR level and not propagate the exception.

### Requirement 2: STOMP Heartbeat Configuration

**User Story:** As a system operator, I want heartbeats configured on WebSocket connections, so that dead connections are detected promptly rather than lingering indefinitely.

#### Acceptance Criteria

1. THE WebSocket_Layer SHALL configure a server-to-client heartbeat interval of 10000 milliseconds on the STOMP message broker.
2. THE WebSocket_Layer SHALL configure a client-to-server heartbeat interval of 10000 milliseconds on the STOMP message broker.
3. WHEN a client fails to send a heartbeat within the configured interval plus a tolerance period, THE WebSocket_Layer SHALL treat the connection as dead and trigger a SessionDisconnectEvent.

### Requirement 3: Maximum Concurrent Connections

**User Story:** As a system operator, I want to limit concurrent WebSocket connections, so that the server is protected from resource exhaustion under high load.

#### Acceptance Criteria

1. THE Connection_Registry SHALL enforce a maximum concurrent WebSocket connection limit, configurable via application properties with a default value of 1000.
2. WHEN a new WebSocket connection attempt occurs and the current connection count equals the configured maximum, THE Connection_Registry SHALL reject the connection with a STOMP ERROR frame containing the message "Maximum connections reached".
3. WHEN a WebSocket connection is established, THE Connection_Registry SHALL increment the active connection count.
4. WHEN a WebSocket connection is closed, THE Connection_Registry SHALL decrement the active connection count.
5. THE Connection_Registry SHALL maintain an accurate count under concurrent connection and disconnection events.

### Requirement 4: Per-Session Inactivity Timeout

**User Story:** As a system operator, I want idle WebSocket connections to be closed automatically, so that server resources are reclaimed from abandoned sessions.

#### Acceptance Criteria

1. THE WebSocket_Layer SHALL enforce a per-session inactivity timeout, configurable via application properties with a default value of 30 minutes.
2. WHILE a WebSocket connection has received no application-level messages (excluding heartbeats) for a duration exceeding the configured inactivity timeout, THE WebSocket_Layer SHALL close the WebSocket connection.
3. WHEN the WebSocket_Layer closes a connection due to inactivity timeout, THE WebSocket_Layer SHALL send a STOMP ERROR frame with the message "Session timed out due to inactivity" before closing.
4. WHEN a connection is closed due to inactivity timeout, THE Disconnect_Listener SHALL update the Chat_Session status to "disconnected".
5. WHEN an application-level message is sent or received on a session, THE WebSocket_Layer SHALL reset the inactivity timer for that session.

### Requirement 5: Send Buffer and Back-Pressure

**User Story:** As a developer, I want the server to apply back-pressure when sending messages faster than the client can consume, so that clients are not overwhelmed and the system remains stable.

#### Acceptance Criteria

1. THE Send_Buffer SHALL have a configurable maximum size per session, defined via application properties with a default value of 50 messages.
2. WHEN the Send_Buffer for a session reaches its configured maximum size, THE Back_Pressure_Controller SHALL pause workflow processing for that session until buffer space becomes available.
3. WHEN buffer space becomes available after a pause, THE Back_Pressure_Controller SHALL resume workflow processing for the paused session.
4. THE Back_Pressure_Controller SHALL enforce a buffer drain timeout, configurable via application properties with a default value of 30 seconds.
5. IF the Send_Buffer remains full for a duration exceeding the configured buffer drain timeout, THEN THE Back_Pressure_Controller SHALL close the WebSocket connection for that session.
6. WHEN the Back_Pressure_Controller closes a connection due to buffer drain timeout, THE Back_Pressure_Controller SHALL log the event at WARN level including the session ID and timeout duration.
7. WHEN a message is successfully delivered to the client and removed from the Send_Buffer, THE Send_Buffer SHALL decrement its current size count.

### Requirement 6: Reconnection Protocol

**User Story:** As a chatbot user, I want to reconnect after a network drop and resume my conversation from where I left off, so that I do not lose progress in my workflow.

#### Acceptance Criteria

1. WHEN a client sends a reconnection request with a session ID, THE Reconnection_Handler SHALL validate that a Chat_Session with that session ID exists in the database.
2. WHEN a reconnection request references a Chat_Session in "disconnected" status, THE Reconnection_Handler SHALL transition the Chat_Session status to "active".
3. WHEN a session is successfully reconnected, THE Reconnection_Handler SHALL re-send the last prompt or question that was pending at the time of disconnection.
4. WHEN a session is successfully reconnected, THE Connection_Registry SHALL register the new WebSocket connection for the resumed session.
5. IF a reconnection request references a Chat_Session that does not exist, THEN THE Reconnection_Handler SHALL return a STOMP ERROR frame with the message "Session not found".
6. IF a reconnection request references a Chat_Session in "completed" status, THEN THE Reconnection_Handler SHALL return a STOMP ERROR frame with the message "Session has already completed".
7. IF a reconnection request references a Chat_Session in "active" status, THEN THE Reconnection_Handler SHALL return a STOMP ERROR frame with the message "Session is already active on another connection".

### Requirement 7: Configuration Properties

**User Story:** As a system operator, I want all resilience parameters to be externally configurable, so that I can tune behavior per environment without code changes.

#### Acceptance Criteria

1. THE WebSocket_Layer SHALL read configuration from application properties using the prefix `chatbot.websocket`.
2. THE WebSocket_Layer SHALL support the following configurable properties with defaults:
   - `chatbot.websocket.max-connections` (default: 1000)
   - `chatbot.websocket.inactivity-timeout-minutes` (default: 30)
   - `chatbot.websocket.heartbeat-interval-ms` (default: 10000)
   - `chatbot.websocket.send-buffer-size` (default: 50)
   - `chatbot.websocket.buffer-drain-timeout-seconds` (default: 30)
3. WHEN a configuration property is not specified, THE WebSocket_Layer SHALL use the documented default value.
