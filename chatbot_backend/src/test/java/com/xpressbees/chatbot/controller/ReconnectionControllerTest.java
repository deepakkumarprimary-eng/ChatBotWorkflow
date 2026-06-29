package com.xpressbees.chatbot.controller;

import com.xpressbees.chatbot.dto.ChatReconnectRequest;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.service.BufferedMessageSender;
import com.xpressbees.chatbot.service.ConnectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReconnectionController.
 * Validates: Requirements 6.1, 6.2, 6.3, 6.5, 6.6, 6.7
 */
@ExtendWith(MockitoExtension.class)
class ReconnectionControllerTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private BufferedMessageSender bufferedMessageSender;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    private ReconnectionController reconnectionController;

    @BeforeEach
    void setUp() {
        reconnectionController = new ReconnectionController(
                chatSessionRepository,
                connectionRegistry,
                bufferedMessageSender,
                messagingTemplate
        );
    }

    /**
     * Test: session not found returns "Session not found" error.
     * Validates: Requirement 6.5
     */
    @Test
    void handleReconnect_sessionNotFound_sendsErrorMessage() {
        // Arrange
        ChatReconnectRequest request = new ChatReconnectRequest();
        request.setSessionId("session-123");

        when(headerAccessor.getSessionId()).thenReturn("stomp-123");
        when(chatSessionRepository.findBySessionId("session-123")).thenReturn(Optional.empty());

        // Act
        reconnectionController.handleReconnect(request, headerAccessor);

        // Assert
        verify(bufferedMessageSender).sendError(eq("session-123"), eq("Session not found"));
        verify(chatSessionRepository, never()).save(any());
    }

    /**
     * Test: completed session returns "Session has already completed" error.
     * Validates: Requirement 6.6
     */
    @Test
    void handleReconnect_completedSession_sendsErrorMessage() {
        // Arrange
        ChatReconnectRequest request = new ChatReconnectRequest();
        request.setSessionId("session-123");

        when(headerAccessor.getSessionId()).thenReturn("stomp-123");

        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId("session-123");
        chatSession.setStatus("completed");

        when(chatSessionRepository.findBySessionId("session-123")).thenReturn(Optional.of(chatSession));

        // Act
        reconnectionController.handleReconnect(request, headerAccessor);

        // Assert
        verify(bufferedMessageSender).sendError(eq("session-123"), eq("Session has already completed"));
        verify(chatSessionRepository, never()).save(any());
    }

    /**
     * Test: active session returns "Session is already active on another connection" error.
     * Validates: Requirement 6.7
     */
    @Test
    void handleReconnect_activeSession_sendsErrorMessage() {
        // Arrange
        ChatReconnectRequest request = new ChatReconnectRequest();
        request.setSessionId("session-123");

        when(headerAccessor.getSessionId()).thenReturn("stomp-123");

        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId("session-123");
        chatSession.setStatus("active");

        when(chatSessionRepository.findBySessionId("session-123")).thenReturn(Optional.of(chatSession));

        // Act
        reconnectionController.handleReconnect(request, headerAccessor);

        // Assert
        verify(bufferedMessageSender).sendError(eq("session-123"), eq("Session is already active on another connection"));
        verify(chatSessionRepository, never()).save(any());
    }

    /**
     * Test: successful reconnection transitions status and re-sends prompt.
     * Validates: Requirements 6.2, 6.3
     */
    @Test
    void handleReconnect_disconnectedSession_transitionsToActiveAndResendPrompt() {
        // Arrange
        ChatReconnectRequest request = new ChatReconnectRequest();
        request.setSessionId("session-123");

        when(headerAccessor.getSessionId()).thenReturn("stomp-123");

        Map<String, Object> nodeMap = new HashMap<>();
        nodeMap.put("id", "node-1");
        nodeMap.put("type", "input");

        Map<String, Object> lastPromptPayload = new HashMap<>();
        lastPromptPayload.put("node", nodeMap);
        lastPromptPayload.put("response", "What is your name?");
        lastPromptPayload.put("sessionId", "session-123");
        lastPromptPayload.put("completed", false);

        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId("session-123");
        chatSession.setStatus("disconnected");
        chatSession.setLastPromptPayload(lastPromptPayload);

        when(chatSessionRepository.findBySessionId("session-123")).thenReturn(Optional.of(chatSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bufferedMessageSender.send(eq("session-123"), any(ChatResponse.class))).thenReturn(true);

        // Act
        reconnectionController.handleReconnect(request, headerAccessor);

        // Assert
        verify(chatSessionRepository).save(argThat(session ->
                "active".equals(session.getStatus())));
        verify(connectionRegistry).associateApplicationSession("stomp-123", "session-123");
        verify(bufferedMessageSender).send(eq("session-123"), any(ChatResponse.class));
        verify(bufferedMessageSender, never()).sendError(any(), any());
    }

    /**
     * Test: re-send failure rolls back status to "disconnected".
     * Validates: Requirement 6.3
     */
    @Test
    void handleReconnect_resendFails_rollsBackStatusToDisconnected() {
        // Arrange
        ChatReconnectRequest request = new ChatReconnectRequest();
        request.setSessionId("session-123");

        when(headerAccessor.getSessionId()).thenReturn("stomp-123");

        Map<String, Object> nodeMap = new HashMap<>();
        nodeMap.put("id", "node-1");
        nodeMap.put("type", "input");

        Map<String, Object> lastPromptPayload = new HashMap<>();
        lastPromptPayload.put("node", nodeMap);
        lastPromptPayload.put("response", "What is your name?");
        lastPromptPayload.put("sessionId", "session-123");
        lastPromptPayload.put("completed", false);

        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId("session-123");
        chatSession.setStatus("disconnected");
        chatSession.setLastPromptPayload(lastPromptPayload);

        when(chatSessionRepository.findBySessionId("session-123")).thenReturn(Optional.of(chatSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bufferedMessageSender.send(eq("session-123"), any(ChatResponse.class))).thenReturn(false);

        // Act
        reconnectionController.handleReconnect(request, headerAccessor);

        // Assert - verify save was called twice (once for "active", once for rollback to "disconnected")
        verify(chatSessionRepository, times(2)).save(any(ChatSession.class));
        // After rollback, the session should be back to "disconnected"
        assertEquals("disconnected", chatSession.getStatus());
        verify(bufferedMessageSender).sendError(eq("session-123"), eq("Reconnection failed"));
    }
}
