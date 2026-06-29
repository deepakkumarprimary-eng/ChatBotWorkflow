package com.xpressbees.chatbot.integration;

import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for WebSocket STOMP endpoints.
 * Tests the full lifecycle: chat.init → chat.start → chat.message → completion.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6
 */
@ActiveProfiles("test")
class WebSocketStompIntegrationTest extends BaseIntegrationTest {

    private static final String API_KEY = "test-api-key-12345";
    private static final long TIMEOUT_SECONDS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    private WebSocketStompClient stompClient;
    private Workflow savedWorkflow;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
        workflowRepository.deleteAll();

        // Create a workflow: message → input → message (allows full lifecycle testing)
        Map<String, Object> workflowJson = buildTestWorkflow();
        Workflow workflow = new Workflow();
        workflow.setName("WebSocket Test Workflow");
        workflow.setWorkflowJson(workflowJson);
        savedWorkflow = workflowRepository.save(workflow);
        workflowRepository.flush();

        // Set up STOMP client with SockJS transport
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    void shouldReturnSessionIdAndWorkflowsOnChatInit() throws Exception {
        // Connect and subscribe to /app/chat.init
        CompletableFuture<Map<String, Object>> initFuture = new CompletableFuture<>();

        StompSession session = connectToWebSocket();

        session.subscribe("/app/chat.init", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                initFuture.complete((Map<String, Object>) payload);
            }
        });

        Map<String, Object> response = initFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(response).containsKey("sessionId");
        assertThat(response.get("sessionId")).isNotNull();
        assertThat(response.get("sessionId").toString()).isNotBlank();
        assertThat(response).containsKey("workflows");
        assertThat((List<?>) response.get("workflows")).isNotNull();

        session.disconnect();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReceiveFirstNodeContentOnChatStart() throws Exception {
        // Step 1: Connect and get sessionId from chat.init
        CompletableFuture<Map<String, Object>> initFuture = new CompletableFuture<>();
        StompSession session = connectToWebSocket();

        session.subscribe("/app/chat.init", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                initFuture.complete((Map<String, Object>) payload);
            }
        });

        Map<String, Object> initResponse = initFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String sessionId = initResponse.get("sessionId").toString();

        // Step 2: Subscribe to the session topic to receive responses
        CompletableFuture<Map<String, Object>> responseFuture = new CompletableFuture<>();
        session.subscribe("/topic/chat/" + sessionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (!responseFuture.isDone()) {
                    responseFuture.complete((Map<String, Object>) payload);
                }
            }
        });

        // Step 3: Send chat.start with valid sessionId and workflowId
        Map<String, Object> startRequest = new HashMap<>();
        startRequest.put("sessionId", sessionId);
        startRequest.put("workflowId", savedWorkflow.getId());
        session.send("/app/chat.start", startRequest);

        // Step 4: Verify we receive the first node content
        Map<String, Object> chatResponse = responseFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(chatResponse).containsKey("response");
        assertThat(chatResponse.get("response")).isNotNull();
        assertThat(chatResponse.get("sessionId")).isEqualTo(sessionId);

        session.disconnect();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAdvanceWorkflowOnChatMessage() throws Exception {
        // Step 1: Initialize session
        CompletableFuture<Map<String, Object>> initFuture = new CompletableFuture<>();
        StompSession session = connectToWebSocket();

        session.subscribe("/app/chat.init", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                initFuture.complete((Map<String, Object>) payload);
            }
        });

        Map<String, Object> initResponse = initFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String sessionId = initResponse.get("sessionId").toString();

        // Step 2: Subscribe to topic and collect all responses
        List<Map<String, Object>> responses = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> inputNodeReceived = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> afterInputResponse = new CompletableFuture<>();

        session.subscribe("/topic/chat/" + sessionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> resp = (Map<String, Object>) payload;
                responses.add(resp);

                // Detect input node (pauses for user input)
                Map<String, Object> node = (Map<String, Object>) resp.get("node");
                if (node != null) {
                    Map<String, Object> config = (Map<String, Object>) node.get("config");
                    if (config != null && "input".equals(config.get("nodeType"))) {
                        inputNodeReceived.complete(null);
                        return;
                    }
                }

                // After input node was already received, this is the response to our message
                if (inputNodeReceived.isDone() && !afterInputResponse.isDone()) {
                    afterInputResponse.complete(resp);
                }
            }
        });

        // Step 3: Start workflow
        Map<String, Object> startRequest = new HashMap<>();
        startRequest.put("sessionId", sessionId);
        startRequest.put("workflowId", savedWorkflow.getId());
        session.send("/app/chat.start", startRequest);

        // Step 4: Wait for the input node (workflow pauses here)
        inputNodeReceived.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Step 5: Send user input to advance workflow
        Map<String, Object> messageRequest = new HashMap<>();
        messageRequest.put("sessionId", sessionId);
        messageRequest.put("message", "TestUser");
        session.send("/app/chat.message", messageRequest);

        // Step 6: Verify workflow advanced (received response after user input)
        Map<String, Object> advancedResponse = afterInputResponse.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(advancedResponse).containsKey("response");
        assertThat(advancedResponse.get("sessionId")).isEqualTo(sessionId);

        session.disconnect();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectChatMessageWithInvalidSessionId() throws Exception {
        // Connect but use a completely invalid session ID for chat.message
        StompSession session = connectToWebSocket();

        // Subscribe to topic for the invalid session to catch error response
        String invalidSessionId = "invalid-session-" + UUID.randomUUID();
        CompletableFuture<Map<String, Object>> errorFuture = new CompletableFuture<>();

        session.subscribe("/topic/chat/" + invalidSessionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorFuture.complete((Map<String, Object>) payload);
            }
        });

        // Small delay to ensure subscription is established
        Thread.sleep(500);

        // Send chat.message with invalid sessionId
        Map<String, Object> messageRequest = new HashMap<>();
        messageRequest.put("sessionId", invalidSessionId);
        messageRequest.put("message", "Hello");
        session.send("/app/chat.message", messageRequest);

        // Verify error response is received
        Map<String, Object> errorResponse = errorFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(errorResponse).containsKey("error");
        assertThat(errorResponse.get("sessionId")).isEqualTo(invalidSessionId);

        session.disconnect();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnErrorOnChatStartWithInvalidSessionId() throws Exception {
        StompSession session = connectToWebSocket();

        // Use an invalid session ID that was never registered via chat.init
        String invalidSessionId = "never-registered-" + UUID.randomUUID();
        CompletableFuture<Map<String, Object>> errorFuture = new CompletableFuture<>();

        session.subscribe("/topic/chat/" + invalidSessionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errorFuture.complete((Map<String, Object>) payload);
            }
        });

        // Small delay to ensure subscription is established
        Thread.sleep(500);

        // Send chat.start with invalid sessionId
        Map<String, Object> startRequest = new HashMap<>();
        startRequest.put("sessionId", invalidSessionId);
        startRequest.put("workflowId", savedWorkflow.getId());
        session.send("/app/chat.start", startRequest);

        // Verify a ChatErrorResponse is received
        Map<String, Object> errorResponse = errorFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(errorResponse).containsKey("error");
        assertThat(errorResponse.get("sessionId")).isEqualTo(invalidSessionId);

        session.disconnect();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSetCompletionFlagOnWorkflowCompletion() throws Exception {
        // Step 1: Initialize session
        CompletableFuture<Map<String, Object>> initFuture = new CompletableFuture<>();
        StompSession session = connectToWebSocket();

        session.subscribe("/app/chat.init", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                initFuture.complete((Map<String, Object>) payload);
            }
        });

        Map<String, Object> initResponse = initFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String sessionId = initResponse.get("sessionId").toString();

        // Step 2: Subscribe and track all responses
        CompletableFuture<Void> inputNodeReceived = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> completionFuture = new CompletableFuture<>();

        session.subscribe("/topic/chat/" + sessionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> resp = (Map<String, Object>) payload;

                // Detect input node pause
                Map<String, Object> node = (Map<String, Object>) resp.get("node");
                if (node != null && !inputNodeReceived.isDone()) {
                    Map<String, Object> config = (Map<String, Object>) node.get("config");
                    if (config != null && "input".equals(config.get("nodeType"))) {
                        inputNodeReceived.complete(null);
                        return;
                    }
                }

                // Detect completion flag
                Object completed = resp.get("completed");
                if (Boolean.TRUE.equals(completed)) {
                    completionFuture.complete(resp);
                }
            }
        });

        // Step 3: Start workflow
        Map<String, Object> startRequest = new HashMap<>();
        startRequest.put("sessionId", sessionId);
        startRequest.put("workflowId", savedWorkflow.getId());
        session.send("/app/chat.start", startRequest);

        // Step 4: Wait for input node, then send user input
        inputNodeReceived.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Map<String, Object> messageRequest = new HashMap<>();
        messageRequest.put("sessionId", sessionId);
        messageRequest.put("message", "TestUser");
        session.send("/app/chat.message", messageRequest);

        // Step 5: Verify workflow completes with completed=true
        Map<String, Object> completionResponse = completionFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(completionResponse.get("completed")).isEqualTo(true);
        assertThat(completionResponse.get("sessionId")).isEqualTo(sessionId);

        session.disconnect();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPushErrorResponseOnWorkflowProcessingError() throws Exception {
        // Create a workflow with an invalid structure that will cause a processing error
        Map<String, Object> brokenWorkflowJson = buildBrokenWorkflow();
        Workflow brokenWorkflow = new Workflow();
        brokenWorkflow.setName("Broken Workflow");
        brokenWorkflow.setWorkflowJson(brokenWorkflowJson);
        brokenWorkflow = workflowRepository.save(brokenWorkflow);
        workflowRepository.flush();

        // Step 1: Initialize session
        CompletableFuture<Map<String, Object>> initFuture = new CompletableFuture<>();
        StompSession session = connectToWebSocket();

        session.subscribe("/app/chat.init", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                initFuture.complete((Map<String, Object>) payload);
            }
        });

        Map<String, Object> initResponse = initFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String sessionId = initResponse.get("sessionId").toString();

        // Step 2: Subscribe to topic for error responses
        CompletableFuture<Map<String, Object>> errorFuture = new CompletableFuture<>();
        session.subscribe("/topic/chat/" + sessionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> resp = (Map<String, Object>) payload;
                if (resp.containsKey("error")) {
                    errorFuture.complete(resp);
                }
            }
        });

        // Small delay to ensure subscription is established
        Thread.sleep(500);

        // Step 3: Start workflow with the broken workflow (which has no valid starting node)
        Map<String, Object> startRequest = new HashMap<>();
        startRequest.put("sessionId", sessionId);
        startRequest.put("workflowId", brokenWorkflow.getId());
        session.send("/app/chat.start", startRequest);

        // Step 4: Verify a ChatErrorResponse is pushed to the session topic
        Map<String, Object> errorResponse = errorFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(errorResponse).containsKey("error");
        assertThat(errorResponse.get("sessionId")).isEqualTo(sessionId);

        session.disconnect();
    }

    // ─── Helper Methods ──────────────────────────────────────────────────────────

    private StompSession connectToWebSocket() throws Exception {
        String url = "ws://localhost:" + port + "/ws";

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("X-API-Key", API_KEY);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();

        stompClient.connectAsync(url, new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        sessionFuture.complete(session);
                    }

                    @Override
                    public void handleException(StompSession session, StompCommand command,
                                                StompHeaders headers, byte[] payload, Throwable exception) {
                        sessionFuture.completeExceptionally(exception);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        if (!sessionFuture.isDone()) {
                            sessionFuture.completeExceptionally(exception);
                        }
                    }
                });

        return sessionFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Builds a 3-node workflow: message → input → message.
     * - msg1: "Hello! What is your name?" (auto-advances)
     * - inp1: "Please enter your name:" (pauses for input)
     * - msg2: "Thank you, {{userName}}! Goodbye." (auto-advances, completes workflow)
     */
    private Map<String, Object> buildTestWorkflow() {
        Map<String, Object> workflowJson = new HashMap<>();

        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("id", "msg1");
        msg1.put("name", "Hello! What is your name?");
        msg1.put("type", "state");
        msg1.put("config", null);

        Map<String, Object> inp1 = new HashMap<>();
        inp1.put("id", "inp1");
        inp1.put("name", "Please enter your name:");
        inp1.put("type", "state");
        Map<String, Object> inputConfig = new HashMap<>();
        inputConfig.put("nodeType", "input");
        inputConfig.put("variableName", "userName");
        inp1.put("config", inputConfig);

        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("id", "msg2");
        msg2.put("name", "Thank you, {{userName}}! Goodbye.");
        msg2.put("type", "state");
        msg2.put("config", null);

        workflowJson.put("nodes", List.of(msg1, inp1, msg2));

        Map<String, Object> t1 = new HashMap<>();
        t1.put("sourceNodeId", "msg1");
        t1.put("targetNodeId", "inp1");

        Map<String, Object> t2 = new HashMap<>();
        t2.put("sourceNodeId", "inp1");
        t2.put("targetNodeId", "msg2");

        workflowJson.put("transitions", List.of(t1, t2));

        return workflowJson;
    }

    /**
     * Builds a workflow with no valid nodes — will cause a "no starting node" error.
     */
    private Map<String, Object> buildBrokenWorkflow() {
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", List.of());
        workflowJson.put("transitions", List.of());
        return workflowJson;
    }
}
