package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for error edge cases in handleBack and handleRestart methods.
 * Validates: Requirements 5.1, 5.2, 5.3, 10.1, 10.2, 10.3
 */
@ExtendWith(MockitoExtension.class)
class BackNavigationErrorTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WorkflowExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, null);
    }

    /**
     * Validates Requirement 5.3: No active session found when session doesn't exist.
     */
    @Test
    void handleBack_noSession_sendsNoActiveSessionError() {
        when(chatSessionRepository.findBySessionId("nonexistent-session"))
                .thenReturn(Optional.empty());

        service.handleBack("nonexistent-session");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/nonexistent-session"), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertEquals("No active session found", error.getError());
        assertEquals("nonexistent-session", error.getSessionId());
    }

    /**
     * Validates Requirement 5.2: Session is already completed error.
     */
    @Test
    void handleBack_completedSession_sendsCompletedError() {
        ChatSession session = new ChatSession();
        session.setSessionId("completed-session");
        session.setStatus("completed");
        session.setWorkflowId(1L);
        session.setContext(new HashMap<>());

        when(chatSessionRepository.findBySessionId("completed-session"))
                .thenReturn(Optional.of(session));

        service.handleBack("completed-session");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/completed-session"), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertEquals("Session is already completed", error.getError());
        assertEquals("completed-session", error.getSessionId());
    }

    /**
     * Validates Requirement 5.1: No previous input to go back to when history is empty.
     */
    @Test
    void handleBack_emptyHistory_sendsNoPreviousInputError() {
        ChatSession session = new ChatSession();
        session.setSessionId("active-session");
        session.setStatus("active");
        session.setWorkflowId(1L);

        // Session with empty navigation history (no entries with awaitsInput)
        Map<String, Object> context = new HashMap<>();
        context.put("_navigationHistory", new ArrayList<>());
        session.setContext(context);

        when(chatSessionRepository.findBySessionId("active-session"))
                .thenReturn(Optional.of(session));

        service.handleBack("active-session");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/active-session"), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertEquals("No previous input to go back to", error.getError());
        assertEquals("active-session", error.getSessionId());
    }

    /**
     * Validates Requirement 5.1: No previous input when history has entries but none with awaitsInput=true.
     */
    @Test
    void handleBack_historyWithNoAwaitsInputEntries_sendsNoPreviousInputError() {
        ChatSession session = new ChatSession();
        session.setSessionId("active-session-2");
        session.setStatus("active");
        session.setWorkflowId(1L);

        // History with entries that have no awaitsInput flag
        Map<String, Object> context = new HashMap<>();
        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> entry = new HashMap<>();
        entry.put("workflowId", 1L);
        entry.put("nodeId", "node-1");
        entry.put("nodeType", null);
        entry.put("timestamp", "2024-01-15T10:30:00Z");
        // No awaitsInput field set
        history.add(entry);
        context.put("_navigationHistory", history);
        session.setContext(context);

        when(chatSessionRepository.findBySessionId("active-session-2"))
                .thenReturn(Optional.of(session));

        service.handleBack("active-session-2");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/active-session-2"), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertEquals("No previous input to go back to", error.getError());
        assertEquals("active-session-2", error.getSessionId());
    }

    /**
     * Validates Requirement 10.1: No active session found on restart when session doesn't exist.
     */
    @Test
    void handleRestart_noSession_sendsNoActiveSessionError() {
        when(chatSessionRepository.findBySessionId("missing-session"))
                .thenReturn(Optional.empty());

        service.handleRestart("missing-session");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/missing-session"), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertEquals("No active session found", error.getError());
        assertEquals("missing-session", error.getSessionId());
    }

    /**
     * Validates Requirement 10.2: Workflow not found when root workflow cannot be loaded.
     */
    @Test
    void handleRestart_workflowNotFound_sendsWorkflowNotFoundError() {
        ChatSession session = new ChatSession();
        session.setSessionId("restart-session");
        session.setStatus("active");
        session.setWorkflowId(1L);

        Map<String, Object> context = new HashMap<>();
        context.put("_rootWorkflowId", 42L);
        context.put("_navigationHistory", new ArrayList<>());
        context.put("_workflowStack", new ArrayList<>());
        session.setContext(context);

        when(chatSessionRepository.findBySessionId("restart-session"))
                .thenReturn(Optional.of(session));
        when(workflowRepository.findById(42L)).thenReturn(Optional.empty());

        service.handleRestart("restart-session");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/restart-session"), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertEquals("Workflow not found", error.getError());
        assertEquals("restart-session", error.getSessionId());
    }
}
