# Design Document

## Overview

Implement STOMP over WebSocket with SockJS fallback. On client subscribe to `/topic/workflows`, the server fetches all workflows from the DB and sends back a JSON array of `{id, name}` objects to only that subscriber's session.

## Architecture

### Components

1. **WebSocketConfig** — Spring `@Configuration` class that enables WebSocket message broker and registers the STOMP endpoint.
2. **ChatWebSocketHandler** — A `@Controller` class with a `@SubscribeMapping` method that handles subscriptions to `/topic/workflows` and returns the workflow list directly to the subscribing client.
3. **WorkflowSummaryDto** — A lightweight DTO with only `id` and `name` fields (avoids sending the full workflow JSON over WebSocket).

### Data Flow

```
Browser                        Backend
  |                               |
  |--- CONNECT /ws (SockJS) ---->|
  |<-- CONNECTED ----------------|
  |                               |
  |--- SUBSCRIBE /topic/workflows |
  |                               |--- WorkflowRepository.findAll()
  |                               |--- map to [{id, name}, ...]
  |<-- MESSAGE (JSON array) -----|
```

## High-Level Design

### WebSocketConfig

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

### ChatWebSocketHandler

```java
@Controller
public class ChatWebSocketHandler {

    private final WorkflowRepository workflowRepository;

    public ChatWebSocketHandler(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @SubscribeMapping("/topic/workflows")
    public List<WorkflowSummaryDto> onSubscribeWorkflows() {
        return workflowRepository.findAll().stream()
                .map(w -> new WorkflowSummaryDto(w.getId(), w.getName()))
                .collect(Collectors.toList());
    }
}
```

### WorkflowSummaryDto

```java
@Data
@AllArgsConstructor
public class WorkflowSummaryDto {
    private Long id;
    private String name;
}
```

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `@SubscribeMapping` instead of `@MessageMapping` | Returns data directly to the subscribing client's session (not broadcast). Triggered automatically when client subscribes. |
| Separate `WorkflowSummaryDto` | Avoids serializing the full JSONB workflow payload over WebSocket. Keeps the message lightweight. |
| `setAllowedOriginPatterns("*")` | Frontend is a separate web app on a different origin. Can be tightened later. |
| Simple in-memory broker | Single instance deployment — no need for external broker (Redis/RabbitMQ) at this stage. |
| SockJS fallback | Ensures compatibility with browsers/proxies that may not support native WebSocket. |

## File Changes

| File | Action | Package |
|------|--------|---------|
| `WebSocketConfig.java` | Create | `com.xpressbees.chatbot.config` |
| `ChatWebSocketHandler.java` | Create | `com.xpressbees.chatbot.controller` |
| `WorkflowSummaryDto.java` | Create | `com.xpressbees.chatbot.dto` |

## Out of Scope

- Message authentication/authorization
- Workflow execution logic via WebSocket
- Conversation session management
- External message broker setup
