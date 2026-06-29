package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.socket.CloseStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DisconnectListener.
 * Validates: Requirements 1.2, 1.4, 1.5
 */
@ExtendWith(MockitoExtension.class)
class DisconnectListenerTest {

    @Mock
    private ConnectionRegistry connectionRegistry;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private BufferedMessageSender bufferedMessageSender;

    private DisconnectListener disconnectListener;

    @BeforeEach
    void setUp() {
        disconnectListener = new DisconnectListener(connectionRegistry, chatSessionRepository, bufferedMessageSender);
    }

    /**
     * Test: successful disconnect updates session status to "disconnected".
     * Mock ConnectionRegistry to return an applicationSessionId,
     * mock ChatSessionRepository.findBySessionId() to return a ChatSession with status "active",
     * verify save() is called with status "disconnected".
     *
     * Validates: Requirement 1.2
     */
    @Test
    void handleDisconnect_activeSession_updatesStatusToDisconnected() {
        // Arrange
        String stompSessionId = "stomp-123";
        String applicationSessionId = "app-session-456";

        when(connectionRegistry.getApplicationSessionId(stompSessionId)).thenReturn(applicationSessionId);

        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId(applicationSessionId);
        chatSession.setStatus("active");

        when(chatSessionRepository.findBySessionId(applicationSessionId)).thenReturn(Optional.of(chatSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionDisconnectEvent event = createDisconnectEvent(stompSessionId);

        // Act
        disconnectListener.handleDisconnect(event);

        // Assert
        verify(chatSessionRepository).save(argThat(session ->
                "disconnected".equals(session.getStatus())));
        verify(connectionRegistry).unregister(stompSessionId);
        verify(bufferedMessageSender).cleanup(applicationSessionId);
    }

    /**
     * Test: session not found in registry (applicationSessionId is null) logs WARN and skips.
     * Mock ConnectionRegistry to return null, verify ChatSessionRepository is never called.
     * Also verify BufferedMessageSender.cleanup() is NOT called since applicationSessionId is null.
     *
     * Validates: Requirement 1.4
     */
    @Test
    void handleDisconnect_sessionNotFoundInRegistry_skipsStatusUpdate() {
        // Arrange
        String stompSessionId = "stomp-unknown";

        when(connectionRegistry.getApplicationSessionId(stompSessionId)).thenReturn(null);

        SessionDisconnectEvent event = createDisconnectEvent(stompSessionId);

        // Act
        disconnectListener.handleDisconnect(event);

        // Assert
        verify(chatSessionRepository, never()).findBySessionId(any());
        verify(chatSessionRepository, never()).save(any());
        verify(connectionRegistry).unregister(stompSessionId);
        verify(bufferedMessageSender, never()).cleanup(any());
    }

    /**
     * Test: DB error is caught, logged at ERROR, not propagated.
     * Mock ChatSessionRepository.save() to throw RuntimeException,
     * verify no exception propagates from handleDisconnect().
     * BufferedMessageSender.cleanup() should still be called after the error.
     *
     * Validates: Requirement 1.5
     */
    @Test
    void handleDisconnect_dbError_caughtAndNotPropagated() {
        // Arrange
        String stompSessionId = "stomp-789";
        String applicationSessionId = "app-session-101";

        when(connectionRegistry.getApplicationSessionId(stompSessionId)).thenReturn(applicationSessionId);

        ChatSession chatSession = new ChatSession();
        chatSession.setSessionId(applicationSessionId);
        chatSession.setStatus("active");

        when(chatSessionRepository.findBySessionId(applicationSessionId)).thenReturn(Optional.of(chatSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenThrow(new RuntimeException("Database connection failed"));

        SessionDisconnectEvent event = createDisconnectEvent(stompSessionId);

        // Act & Assert - no exception should propagate
        assertDoesNotThrow(() -> disconnectListener.handleDisconnect(event));
        verify(connectionRegistry).unregister(stompSessionId);
        verify(bufferedMessageSender).cleanup(applicationSessionId);
    }

    private SessionDisconnectEvent createDisconnectEvent(String stompSessionId) {
        Message<byte[]> message = new GenericMessage<>(new byte[0]);
        return new SessionDisconnectEvent(this, message, stompSessionId, CloseStatus.NORMAL);
    }
}
