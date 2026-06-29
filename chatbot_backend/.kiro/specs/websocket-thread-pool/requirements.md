# Requirements Document

## Introduction

The chatbot backend processes WebSocket STOMP messages on the IO transport thread by default (Spring's DirectChannel behavior). When workflow execution blocks — particularly during API node retries with `Thread.sleep` — the transport thread is held, preventing other users' messages from being processed. This feature introduces a dedicated, bounded thread pool for the WebSocket inbound channel to isolate long-running workflow executions from the transport layer, ensuring concurrent user sessions do not block each other.

## Glossary

- **Inbound_Channel**: The Spring `clientInboundChannel` that receives STOMP messages from connected WebSocket clients and dispatches them to message handlers.
- **Outbound_Channel**: The Spring `clientOutboundChannel` that sends STOMP messages from the server back to connected WebSocket clients.
- **Thread_Pool**: A bounded pool of worker threads managed by Spring's `ThreadPoolTaskExecutor`, configured with core size, max size, and queue capacity.
- **WebSocketConfig**: The Spring configuration class (`WebSocketConfig.java`) that configures STOMP endpoints, message broker, and channel interceptors.
- **WebSocketResilienceProperties**: The Spring `@ConfigurationProperties` class that externalizes WebSocket-related configuration to application properties under the `chatbot.websocket` prefix.
- **Transport_Thread**: The NIO/IO thread managed by the WebSocket transport layer (SockJS/Tomcat) responsible for reading bytes off the network socket.
- **Inbound_Interceptor**: A `ChannelInterceptor` registered on the Inbound_Channel (e.g., authentication, connection limiting) that executes before message handling.

## Requirements

### Requirement 1: Inbound Channel Thread Pool Configuration

**User Story:** As a system administrator, I want the WebSocket inbound channel to use a dedicated thread pool, so that long-running workflow executions do not block the transport thread and other users' messages are processed concurrently.

#### Acceptance Criteria

1. WHEN the application starts, THE WebSocketConfig SHALL configure the Inbound_Channel with a dedicated Thread_Pool using Spring's `ThreadPoolTaskExecutor`.
2. THE Thread_Pool SHALL use a core pool size that defaults to 10 threads, configurable via application properties.
3. THE Thread_Pool SHALL use a maximum pool size that defaults to 50 threads, configurable via application properties.
4. THE Thread_Pool SHALL use a task queue capacity that defaults to 200 pending messages, configurable via application properties.
5. THE Thread_Pool SHALL name all threads with the prefix "ws-inbound-" followed by a sequential numeric identifier.
6. WHEN a STOMP message arrives on the Inbound_Channel, THE WebSocketConfig SHALL dispatch the message to the Thread_Pool instead of processing it on the Transport_Thread.
7. WHILE the application is shutting down, THE Thread_Pool SHALL wait for in-progress tasks to complete up to a maximum of 30 seconds before forcibly terminating remaining threads.

### Requirement 2: Thread Pool Properties Externalization

**User Story:** As a DevOps engineer, I want thread pool settings to be configurable via application properties, so that I can tune pool sizes per environment without code changes.

#### Acceptance Criteria

1. THE WebSocketResilienceProperties SHALL expose an inbound channel core pool size property under `chatbot.websocket.inbound-pool-core-size` with a default value of 10.
2. THE WebSocketResilienceProperties SHALL expose an inbound channel max pool size property under `chatbot.websocket.inbound-pool-max-size` with a default value of 50.
3. THE WebSocketResilienceProperties SHALL expose an inbound channel queue capacity property under `chatbot.websocket.inbound-pool-queue-capacity` with a default value of 200.
4. IF the configured inbound-pool-core-size is less than 1, THEN THE application SHALL fail to start with a validation error.
5. IF the configured inbound-pool-max-size is less than 1, THEN THE application SHALL fail to start with a validation error.
6. IF the configured inbound-pool-queue-capacity is less than 1, THEN THE application SHALL fail to start with a validation error.
7. IF the configured inbound-pool-max-size is less than the configured inbound-pool-core-size, THEN THE application SHALL fail to start with a validation error indicating that max pool size must be greater than or equal to core pool size.

### Requirement 3: Interceptor Compatibility

**User Story:** As a developer, I want the existing channel interceptors to continue working after adding the thread pool, so that authentication and connection limiting behavior is preserved.

#### Acceptance Criteria

1. WHEN the Inbound_Channel is configured with a Thread_Pool, THE WebSocketConfig SHALL register the WebSocketAuthInterceptor and ConnectionLimitInterceptor on the same channel registration, with WebSocketAuthInterceptor ordered before ConnectionLimitInterceptor.
2. WHEN a STOMP CONNECT frame arrives, THE WebSocketAuthInterceptor SHALL execute its `preSend` method before the message is dispatched to the Thread_Pool.
3. WHEN a STOMP CONNECT frame arrives, THE ConnectionLimitInterceptor SHALL execute its `preSend` method before the message is dispatched to the Thread_Pool.
4. IF the WebSocketAuthInterceptor rejects a STOMP CONNECT frame by throwing an exception, THEN THE Inbound_Channel SHALL not dispatch the message to the Thread_Pool and the client connection SHALL be terminated.
5. IF the ConnectionLimitInterceptor rejects a STOMP CONNECT frame by throwing an exception, THEN THE Inbound_Channel SHALL not dispatch the message to the Thread_Pool and the client connection SHALL be terminated.

### Requirement 4: Outbound Channel Thread Pool Configuration

**User Story:** As a system administrator, I want an optional dedicated thread pool for the WebSocket outbound channel, so that message sending does not contend with inbound processing threads.

#### Acceptance Criteria

1. THE WebSocketConfig SHALL configure the Outbound_Channel with a dedicated Thread_Pool using Spring's `ThreadPoolTaskExecutor`.
2. THE Thread_Pool for the Outbound_Channel SHALL use a core pool size that defaults to 10 threads.
3. THE Thread_Pool for the Outbound_Channel SHALL use a maximum pool size that defaults to 50 threads.
4. THE Thread_Pool for the Outbound_Channel SHALL use a task queue capacity that defaults to 200 pending messages.
5. THE Thread_Pool for the Outbound_Channel SHALL name all threads with the prefix "ws-outbound-" followed by a numeric identifier.
6. THE WebSocketResilienceProperties SHALL expose outbound channel pool properties under `chatbot.websocket.outbound-pool-core-size`, `chatbot.websocket.outbound-pool-max-size`, and `chatbot.websocket.outbound-pool-queue-capacity` with the same defaults and validation rules as the inbound pool properties.

### Requirement 5: Thread Pool Saturation Handling

**User Story:** As a system administrator, I want predictable behavior when the thread pool is fully saturated, so that the system degrades gracefully under extreme load rather than silently dropping messages.

#### Acceptance Criteria

1. WHEN the inbound channel Thread_Pool queue reaches its configured queue capacity and all threads (up to max pool size) are busy, THE Thread_Pool SHALL reject new tasks using a caller-runs policy, executing the task on the submitting thread rather than discarding it.
2. WHEN a task is rejected and executed via caller-runs policy, THE WebSocketConfig SHALL log a warning at WARN level indicating thread pool saturation, including the current active thread count and queue size.
3. THE Thread_Pool SHALL expose its configuration (core pool size, max pool size, queue capacity, and thread name prefix) via application logs at startup at INFO level.
4. WHILE the Thread_Pool is operating under caller-runs policy, THE Thread_Pool SHALL NOT silently drop or discard any submitted task; all tasks SHALL either be executed by a pool thread or by the submitting caller thread.
