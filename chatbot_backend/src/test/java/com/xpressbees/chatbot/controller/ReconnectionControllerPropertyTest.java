package com.xpressbees.chatbot.controller;

import com.xpressbees.chatbot.dto.ChatReconnectRequest;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.service.BufferedMessageSender;
import com.xpressbees.chatbot.service.ConnectionRegistry;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.api.lifecycle.AfterTry;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Feature: websocket-resilience, Property 11: Reconnection State Machine
// Feature: websocket-resilience, Property 12: Reconnection Re-sends Stored Prompt

/**
 * Property-based tests for ReconnectionController.
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.5, 6.6, 6.7
 */
class ReconnectionControllerPropertyTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private BufferedMessageSender bufferedMessageSender;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ReconnectionController controller;
    private AutoCloseable mocks;

    @BeforeTry
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        controller = new ReconnectionController(
                chatSessionRepository,
                connectionRegistry,
                bufferedMessageSender,
                messagingTemplate
        );
    }

    @AfterTry
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 11: Reconnection State Machine
    // For any reconnection request with a session ID, the reconnection shall succeed
    // (transitioning status to "active") if and only if the ChatSession exists in the
    // database AND its current status is "disconnected". All other states (not found,
    // "completed", "active") shall result in rejection with the appropriate error message.
    //
    // Validates: Requirements 6.1, 6.2, 6.5, 6.6, 6.7
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 11: Reconnection State Machine — Generate sessions in all states
     * (not found, active, completed, disconnected), verify correct accept/reject behavior.
     *
     * Validates: Requirements 6.1, 6.2, 6.5, 6.6, 6.7
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 11: Reconnection State Machine")
    void reconnectionSucceedsOnlyForDisconnectedSessions(
            @ForAll("sessionScenarios") SessionScenario scenario) {

        // Setup mock behavior based on scenario
        String sessionId = scenario.sessionId;
        ChatReconnectRequest request = new ChatReconnectRequest();
        request.setSessionId(sessionId);

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId("stomp-" + UUID.randomUUID());

        if (scenario.sessionState == SessionState.NOT_FOUND) {
            when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        } else {
            ChatSession chatSession = new ChatSession();
            chatSession.setSessionId(sessionId);
            chatSession.setStatus(scenario.sessionState.getStatusValue());
            chatSession.setWorkflowId(1L);
            when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(chatSession));
            when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        when(bufferedMessageSender.send(anyString(), any(ChatResponse.class))).thenReturn(true);

        // Execute
        controller.handleReconnect(request, accessor);

        // Verify behavior based on state
        switch (scenario.sessionState) {
            case NOT_FOUND:
                verify(bufferedMessageSender).sendError(eq(sessionId), eq("Session not found"));
                verify(chatSessionRepository, never()).save(any());
                break;

            case COMPLETED:
                verify(bufferedMessageSender).sendError(eq(sessionId), eq("Session has already completed"));
                verify(chatSessionRepository, never()).save(any());
                break;

            case ACTIVE:
                verify(bufferedMessageSender).sendError(eq(sessionId), eq("Session is already active on another connection"));
                verify(chatSessionRepository, never()).save(any());
                break;

            case DISCONNECTED:
                // Should transition to active
                ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
                verify(chatSessionRepository).save(captor.capture());
                assertThat(captor.getValue().getStatus()).isEqualTo("active");
                // Should NOT send error
                verify(bufferedMessageSender, never()).sendError(eq(sessionId), anyString());
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Property 12: Reconnection Re-sends Stored Prompt
    // For any ChatSession in "disconnected" status that has a non-null lastPromptPayload,
    // after successful reconnection the system shall re-send a message to the client
    // whose content equals the stored lastPromptPayload.
    //
    // Validates: Requirements 6.3
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Property 12: Reconnection Re-sends Stored Prompt — Generate random prompts,
     * store, reconnect, verify re-send content matches stored payload.
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    @Tag("Feature: websocket-resilience, Property 12: Reconnection Re-sends Stored Prompt")
    void reconnectionResendStoredPromptPayload(
            @ForAll("promptPayloads") Map<String, Object> lastPromptPayload) {

        String sessionId = "session-" + UUID.randomUUID();

        // Create a disconnected session with the generated payload
        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId(sessionId);
        chatSession.setStatus("disconnected");
        chatSession.setWorkflowId(1L);
        chatSession.setLastPromptPayload(lastPromptPayload);

        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(chatSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bufferedMessageSender.send(anyString(), any(ChatResponse.class))).thenReturn(true);

        ChatReconnectRequest request = new ChatReconnectRequest();
        request.setSessionId(sessionId);

        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId("stomp-" + UUID.randomUUID());

        // Execute
        controller.handleReconnect(request, accessor);

        // Verify that send was called with a ChatResponse matching the stored payload
        ArgumentCaptor<ChatResponse> responseCaptor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(bufferedMessageSender).send(eq(sessionId), responseCaptor.capture());

        ChatResponse sentResponse = responseCaptor.getValue();

        // The stored payload has keys: node, response, sessionId, completed
        @SuppressWarnings("unchecked")
        Map<String, Object> expectedNode = (Map<String, Object>) lastPromptPayload.get("node");
        String expectedResponseText = (String) lastPromptPayload.get("response");
        Boolean expectedCompleted = (Boolean) lastPromptPayload.get("completed");

        assertThat(sentResponse.getNode())
                .as("Re-sent node should match stored payload node")
                .isEqualTo(expectedNode);
        assertThat(sentResponse.getResponse())
                .as("Re-sent response text should match stored payload response")
                .isEqualTo(expectedResponseText);
        assertThat(sentResponse.getSessionId())
                .as("Re-sent sessionId should match the session being reconnected")
                .isEqualTo(sessionId);
        assertThat(sentResponse.getCompleted())
                .as("Re-sent completed flag should match stored payload completed")
                .isEqualTo(expectedCompleted);
    }

    // ──────────────────────────── Providers ────────────────────────────────────

    @Provide
    Arbitrary<SessionScenario> sessionScenarios() {
        Arbitrary<String> sessionIds = Arbitraries.strings()
                .alpha().ofMinLength(5).ofMaxLength(20)
                .map(s -> "session-" + s);

        Arbitrary<SessionState> states = Arbitraries.of(SessionState.values());

        return Combinators.combine(sessionIds, states)
                .as(SessionScenario::new);
    }

    @Provide
    Arbitrary<Map<String, Object>> promptPayloads() {
        // Generate a realistic lastPromptPayload map with node, response, sessionId, completed
        Arbitrary<Map<String, Object>> nodeArb = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(15)
                .map(label -> {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", "node-" + UUID.randomUUID().toString().substring(0, 8));
                    node.put("type", "input");
                    node.put("label", label);
                    return node;
                });

        Arbitrary<String> responseTexts = Arbitraries.strings()
                .alpha().ofMinLength(5).ofMaxLength(50)
                .map(s -> "Please enter " + s);

        Arbitrary<Boolean> completedFlags = Arbitraries.of(false, true);

        return Combinators.combine(nodeArb, responseTexts, completedFlags)
                .as((node, response, completed) -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("node", node);
                    payload.put("response", response);
                    payload.put("completed", completed);
                    return payload;
                });
    }

    // ──────────────────────────── Helper Types ────────────────────────────────────

    enum SessionState {
        NOT_FOUND(null),
        ACTIVE("active"),
        COMPLETED("completed"),
        DISCONNECTED("disconnected");

        private final String statusValue;

        SessionState(String statusValue) {
            this.statusValue = statusValue;
        }

        public String getStatusValue() {
            return statusValue;
        }
    }

    static class SessionScenario {
        final String sessionId;
        final SessionState sessionState;

        SessionScenario(String sessionId, SessionState sessionState) {
            this.sessionId = sessionId;
            this.sessionState = sessionState;
        }

        @Override
        public String toString() {
            return "SessionScenario{sessionId='" + sessionId + "', state=" + sessionState + "}";
        }
    }
}
