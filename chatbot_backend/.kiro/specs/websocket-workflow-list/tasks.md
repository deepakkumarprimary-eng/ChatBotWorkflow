# Tasks

## Task 1: Create WorkflowSummaryDto

**Status:** completed

Create a lightweight DTO class with only `id` and `name` fields for the WebSocket response.

### Steps
1. Create `src/main/java/com/xpressbees/chatbot/dto/WorkflowSummaryDto.java`
2. Add fields: `Long id`, `String name`
3. Annotate with `@Data` and `@AllArgsConstructor` (Lombok)

---

## Task 2: Create WebSocketConfig

**Status:** completed

Configure STOMP over WebSocket with SockJS fallback and simple in-memory broker.

### Steps
1. Create `src/main/java/com/xpressbees/chatbot/config/WebSocketConfig.java`
2. Annotate with `@Configuration` and `@EnableWebSocketMessageBroker`
3. Implement `WebSocketMessageBrokerConfigurer`
4. In `configureMessageBroker`: enable simple broker with prefix `/topic`
5. In `registerStompEndpoints`: register `/ws` endpoint with `setAllowedOriginPatterns("*")` and `.withSockJS()`

---

## Task 3: Create ChatWebSocketHandler

**Status:** completed

Create a controller that handles `/topic/workflows` subscriptions and returns the workflow list.

### Steps
1. Create `src/main/java/com/xpressbees/chatbot/controller/ChatWebSocketHandler.java`
2. Annotate with `@Controller`
3. Inject `WorkflowRepository` via constructor
4. Add method with `@SubscribeMapping("/topic/workflows")` that:
   - Calls `workflowRepository.findAll()`
   - Maps each `Workflow` entity to `WorkflowSummaryDto(id, name)`
   - Returns `List<WorkflowSummaryDto>`

---

## Task 4: Verify Build

**Status:** completed

Ensure the project compiles successfully with the new files.

### Steps
1. Run `mvn compile`
2. Fix any compilation errors
