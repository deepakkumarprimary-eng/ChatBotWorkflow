# Implementation Plan: WebSocket Resilience

## Overview

This plan implements resilience mechanisms for the WebSocket layer: disconnect detection, heartbeat configuration, connection limits, inactivity timeout, send buffer with back-pressure, and a reconnection protocol. Each task builds incrementally on the prior, wiring everything together at the end.

## Tasks

- [x] 1. Configuration and data model foundations
  - [x] 1.1 Create WebSocketResilienceProperties configuration class
    - Create `com.xpressbees.chatbot.config.WebSocketResilienceProperties` with `@ConfigurationProperties(prefix = "chatbot.websocket")`
    - Fields: `maxConnections` (default 1000), `inactivityTimeoutMinutes` (default 30), `heartbeatIntervalMs` (default 10000), `sendBufferSize` (default 50), `bufferDrainTimeoutSeconds` (default 30)
    - Use `@Validated` with `@Min` constraints on each field
    - Add `@EnableConfigurationProperties(WebSocketResilienceProperties.class)` to the main application class or a config class
    - Add default properties to `application.properties`
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 1.2 Add `lastPromptPayload` column to ChatSession entity and schema
    - Add `last_prompt_payload JSONB` column to `chat_session` table in `schema.sql`
    - Add `lastPromptPayload` field (type `Map<String, Object>`) with `@Type(JsonType.class)` annotation to `ChatSession` entity
    - _Requirements: 6.3_

  - [x] 1.3 Create ConnectionEntry record and ConnectionRegistry interface
    - Create `com.xpressbees.chatbot.service.ConnectionEntry` record with fields: `stompSessionId`, `applicationSessionId`, `connectedAt` (Instant), `lastActivityAt` (Instant)
    - Create `com.xpressbees.chatbot.service.ConnectionRegistry` interface with methods: `register`, `unregister`, `getActiveCount`, `getApplicationSessionId`, `getStompSessionId`, `recordActivity`, `getInactiveSessions`
    - _Requirements: 3.3, 3.4, 3.5, 4.2, 4.5_

  - [x] 1.4 Implement InMemoryConnectionRegistry
    - Create `com.xpressbees.chatbot.service.InMemoryConnectionRegistry` implementing `ConnectionRegistry`
    - Use `ConcurrentHashMap` for O(1) lookup by STOMP session ID and application session ID
    - Inject `WebSocketResilienceProperties` for max connection limit
    - `register()` returns false and does not add entry when `getActiveCount() >= maxConnections`
    - `getInactiveSessions()` compares `lastActivityAt` against provided duration
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.2_

  - [x]* 1.5 Write property tests for ConnectionRegistry
    - **Property 1: Session ID Mapping Lookup** — Generate random (stompId, appId) pairs, register them, verify bidirectional lookup correctness
    - **Property 3: Connection Count Invariant** — Generate random sequences of register/unregister, verify count equals registrations minus unregistrations (clamped ≥ 0)
    - **Property 4: Connection Rejection at Capacity** — For random capacities N, fill to limit, verify (N+1)th registration is rejected
    - **Validates: Requirements 1.1, 3.2, 3.3, 3.4, 3.5**

- [x] 2. Heartbeat and disconnect detection
  - [x] 2.1 Configure STOMP heartbeat in WebSocketConfig
    - Modify `WebSocketConfig.configureMessageBroker()` to add `.setHeartbeatValue(new long[]{heartbeatIntervalMs, heartbeatIntervalMs})` and `.setTaskScheduler(heartbeatScheduler())`
    - Create a `ThreadPoolTaskScheduler` bean named `heartbeatScheduler` for heartbeat processing
    - Inject `WebSocketResilienceProperties` for heartbeat interval value
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 2.2 Implement DisconnectListener
    - Create `com.xpressbees.chatbot.service.DisconnectListener` with `@EventListener` on `SessionDisconnectEvent`
    - Look up application session ID from `ConnectionRegistry` using the STOMP session ID from the event
    - If found and session status is "active": update `ChatSession.status` to "disconnected" via `ChatSessionRepository`
    - Unregister from `ConnectionRegistry`
    - Log at INFO level with application session ID, STOMP session ID, and timestamp
    - If session not found: log WARN and skip
    - If DB error occurs: log ERROR, do not propagate exception
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x]* 2.3 Write unit tests for DisconnectListener
    - Test: successful disconnect updates session status to "disconnected"
    - Test: session not found in registry logs WARN and skips
    - Test: DB error is caught, logged at ERROR, not propagated
    - _Requirements: 1.2, 1.4, 1.5_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Connection limit enforcement
  - [x] 4.1 Implement ConnectionLimitInterceptor
    - Create `com.xpressbees.chatbot.config.ConnectionLimitInterceptor` implementing `ChannelInterceptor`
    - In `preSend()`: detect STOMP CONNECT command, call `ConnectionRegistry.register(stompSessionId, null)` (application session ID mapped later)
    - If registration returns false: throw `MessageDeliveryException` with message "Maximum connections reached" (triggers STOMP ERROR frame)
    - On DISCONNECT command: call `ConnectionRegistry.unregister(stompSessionId)`
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 4.2 Register ConnectionLimitInterceptor in WebSocketConfig
    - Inject `ConnectionLimitInterceptor` into `WebSocketConfig`
    - Add it to the inbound channel interceptor chain after `WebSocketAuthInterceptor`
    - _Requirements: 3.2, 3.5_

  - [x]* 4.3 Write unit tests for ConnectionLimitInterceptor
    - Test: CONNECT is accepted when below limit
    - Test: CONNECT is rejected with correct error message when at limit
    - Test: DISCONNECT correctly unregisters
    - _Requirements: 3.2, 3.3, 3.4_

- [x] 5. Inactivity timeout
  - [x] 5.1 Implement InactivityTimeoutScheduler
    - Create `com.xpressbees.chatbot.service.InactivityTimeoutScheduler` with `@Scheduled(fixedDelay = 60000)`
    - Query `ConnectionRegistry.getInactiveSessions()` with configured timeout duration
    - For each inactive session: send STOMP ERROR frame "Session timed out due to inactivity" via `SimpMessagingTemplate`, then close the connection
    - Skip ERROR frame if session has been inactive significantly longer than timeout (e.g., > 2x timeout)
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [x] 5.2 Add activity recording to message handling path
    - In `ChatWebSocketController` (or via a dedicated inbound `ChannelInterceptor`), call `ConnectionRegistry.recordActivity(stompSessionId)` on each application-level message (`/chat.start`, `/chat.message`, `/chat.back`, `/chat.restart`)
    - Ensure heartbeats do NOT reset the inactivity timer (only application-level messages do)
    - _Requirements: 4.5_

  - [x]* 5.3 Write property tests for inactivity detection
    - **Property 5: Inactivity Detection** — Generate random timestamps and timeout durations, verify session is flagged inactive iff elapsed time exceeds timeout
    - **Property 6: Activity Recording Resets Timer** — Generate sessions with old timestamps, record activity, verify they become active again
    - **Validates: Requirements 4.2, 4.5**

- [x] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Send buffer and back-pressure
  - [x] 7.1 Create SessionSendBuffer class
    - Create `com.xpressbees.chatbot.service.SessionSendBuffer` with `LinkedBlockingQueue<ChatResponse>` of configurable max size
    - Methods: `offer(ChatResponse)`, `poll()`, `isFull()`, `isEmpty()`, `size()`, `markPaused()`, `markResumed()`, `isPaused()`, `getPausedSince()`
    - Track `paused` state with `volatile boolean` and `pausedSince` with `volatile Instant`
    - _Requirements: 5.1, 5.7_

  - [x] 7.2 Implement BufferedMessageSender
    - Create `com.xpressbees.chatbot.service.BufferedMessageSender` interface with methods: `send(sessionId, response)`, `sendError(sessionId, errorMessage)`, `acknowledge(sessionId)`, `cleanup(sessionId)`
    - Create `com.xpressbees.chatbot.service.BufferedMessageSenderImpl` implementing the interface
    - Maintain a `ConcurrentHashMap<String, SessionSendBuffer>` of per-session buffers
    - `send()`: offer to buffer; if buffer full, mark paused and start drain timeout tracking; delegate to `ChatMessageSender.sendResponse()` for actual delivery
    - `sendError()`: bypass buffer, deliver immediately via `ChatMessageSender.sendError()`
    - `acknowledge()`: poll from buffer, if was paused and now has space, mark resumed
    - `cleanup()`: remove buffer for session
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.7_

  - [x] 7.3 Implement buffer drain timeout enforcement
    - In `BufferedMessageSenderImpl` or a scheduled task: periodically check paused sessions
    - If a session has been paused longer than `bufferDrainTimeoutSeconds`, close the connection
    - Log at WARN level with session ID and timeout duration
    - _Requirements: 5.4, 5.5, 5.6_

  - [x] 7.4 Wire BufferedMessageSender into WorkflowExecutionServiceImpl
    - Inject `BufferedMessageSender` into `WorkflowExecutionServiceImpl`
    - Replace direct `ChatMessageSender.sendResponse()` calls with `BufferedMessageSender.send()`
    - Keep `ChatMessageSender.sendError()` calls routed through `BufferedMessageSender.sendError()` (bypass buffer)
    - When `send()` returns false (connection closed due to drain timeout), stop workflow processing for that session
    - _Requirements: 5.2, 5.3_

  - [x]* 7.5 Write property tests for SessionSendBuffer
    - **Property 7: Buffer Size Invariant** — Generate random sequences of offer/poll on buffers of random sizes, verify size invariant holds
    - **Property 8: Buffer Full Triggers Pause** — Fill buffer to capacity, verify paused state
    - **Property 9: Buffer Drain Resumes Processing** — Pause then poll, verify resumed state
    - **Validates: Requirements 5.2, 5.3, 5.7**

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Reconnection protocol
  - [x] 9.1 Create ChatReconnectRequest DTO
    - Create `com.xpressbees.chatbot.dto.ChatReconnectRequest` with `@NotBlank String sessionId`
    - Use Lombok `@Data`
    - _Requirements: 6.1_

  - [x] 9.2 Store last prompt payload on outbound prompts
    - In `WorkflowExecutionServiceImpl`, when sending a prompt/question (input node or API interactive selection), serialize the `ChatResponse` and store it in `ChatSession.lastPromptPayload`
    - Save via `ChatSessionRepository`
    - _Requirements: 6.3_

  - [x] 9.3 Implement ReconnectionController
    - Create `com.xpressbees.chatbot.controller.ReconnectionController` with `@MessageMapping("/chat.reconnect")`
    - Validate session exists in DB; if not: send STOMP ERROR "Session not found"
    - If session status is "completed": send STOMP ERROR "Session has already completed"
    - If session status is "active": send STOMP ERROR "Session is already active on another connection"
    - If session status is "disconnected": transition to "active", register new connection in `ConnectionRegistry`, re-send `lastPromptPayload` via `BufferedMessageSender`
    - If re-send fails: roll back status to "disconnected", send STOMP ERROR "Reconnection failed"
    - Use `@Transactional` for atomicity
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [x]* 9.4 Write property tests for reconnection state machine
    - **Property 11: Reconnection State Machine** — Generate sessions in all states (not found, active, completed, disconnected), verify correct accept/reject behavior
    - **Property 12: Reconnection Re-sends Stored Prompt** — Generate random prompts, store, reconnect, verify re-send content matches stored payload
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.5, 6.6, 6.7**

  - [x]* 9.5 Write unit tests for ReconnectionController
    - Test: session not found returns "Session not found" error
    - Test: completed session returns "Session has already completed" error
    - Test: active session returns "Session is already active on another connection" error
    - Test: successful reconnection transitions status and re-sends prompt
    - Test: re-send failure rolls back status
    - _Requirements: 6.1, 6.2, 6.3, 6.5, 6.6, 6.7_

- [x] 10. Integration wiring and application session mapping
  - [x] 10.1 Map application session ID to STOMP session on chat.start
    - In `ChatWebSocketController.startWorkflow()`, extract the STOMP session ID from `SimpMessageHeaderAccessor`
    - Update the `ConnectionRegistry` entry to associate the application `sessionId` with the STOMP session ID
    - Add a method to `ConnectionRegistry` interface if needed (e.g., `associateApplicationSession(stompSessionId, applicationSessionId)`)
    - _Requirements: 1.1, 3.3_

  - [x] 10.2 Wire DisconnectListener cleanup with BufferedMessageSender
    - In `DisconnectListener`, after unregistering from `ConnectionRegistry`, call `BufferedMessageSender.cleanup(sessionId)` to free buffer resources
    - _Requirements: 1.2, 5.7_

  - [x] 10.3 Enable scheduling and finalize configuration
    - Add `@EnableScheduling` to a configuration class (or main application class) if not already present
    - Verify `WebSocketResilienceProperties` is properly loaded and injected across all components
    - Verify the interceptor chain order in `WebSocketConfig`: `WebSocketAuthInterceptor` → `ConnectionLimitInterceptor`
    - _Requirements: 4.1, 7.1_

- [x] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document (jqwik 1.8.2)
- Unit tests validate specific examples and edge cases (JUnit 5 + Mockito)
- The `BufferedMessageSender` wraps `ChatMessageSender` using the decorator pattern — existing code continues to work unchanged
- The `ConnectionLimitInterceptor` uses Spring's `ChannelInterceptor` extension point — no changes to existing interceptors required

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4", "9.1"] },
    { "id": 2, "tasks": ["1.5", "2.1", "4.1", "7.1"] },
    { "id": 3, "tasks": ["2.2", "4.2", "5.1", "7.2"] },
    { "id": 4, "tasks": ["2.3", "4.3", "5.2", "5.3", "7.3"] },
    { "id": 5, "tasks": ["7.4", "7.5", "9.2"] },
    { "id": 6, "tasks": ["9.3", "10.1"] },
    { "id": 7, "tasks": ["9.4", "9.5", "10.2", "10.3"] }
  ]
}
```
