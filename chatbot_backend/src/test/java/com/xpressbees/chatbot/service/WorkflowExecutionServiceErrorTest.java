package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkflowExecutionService error handling paths.
 */
class WorkflowExecutionServiceErrorTest {

    private WorkflowRepository workflowRepository;
    private ChatSessionRepository chatSessionRepository;
    private SimpMessagingTemplate messagingTemplate;
    private WorkflowExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        ChatWebSocketHandler chatWebSocketHandler = mock(ChatWebSocketHandler.class);
        when(chatWebSocketHandler.consumePendingSession(anyString())).thenReturn(true);
        service = TestServiceFactory.createService(workflowRepository, processors, placeholderService, null, chatWebSocketHandler, new ChatMessageSender(messagingTemplate), new SessionStateManager(chatSessionRepository), new NavigationService(TestServiceFactory.createMockCacheService(workflowRepository), placeholderService), new ChildWorkflowService(TestServiceFactory.createMockCacheService(workflowRepository)));
    }

    @Test
    void startWorkflow_withNonExistentId_sendsError() {
        ChatSession session = new ChatSession();
        session.setSessionId("sess-123");
        session.setStatus("active");
        session.setContext(new HashMap<>());

        when(chatSessionRepository.findBySessionId("sess-123")).thenReturn(Optional.of(session));
        when(workflowRepository.findById(999L)).thenReturn(Optional.empty());

        service.startWorkflow("sess-123", 999L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertTrue(error.getError().contains("Workflow not found"));
    }

    @Test
    void startWorkflow_withEmptyTransitions_sendsError() {
        ChatSession session = new ChatSession();
        session.setSessionId("sess-123");
        session.setStatus("active");
        session.setContext(new HashMap<>());

        Workflow workflow = new Workflow();
        Map<String, Object> json = new HashMap<>();
        json.put("nodes", List.of(Map.of("id", "1", "name", "Node", "type", "state")));
        json.put("transitions", List.of());
        workflow.setWorkflowJson(json);

        when(chatSessionRepository.findBySessionId("sess-123")).thenReturn(Optional.of(session));
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        service.startWorkflow("sess-123", 1L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertTrue(error.getError().contains("no starting node"));
    }

    @Test
    void handleUserInput_withNonExistentSession_sendsError() {
        when(chatSessionRepository.findBySessionId("unknown")).thenReturn(Optional.empty());

        service.handleUserInput("unknown", "hello");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertTrue(error.getError().contains("No active session"));
    }

    @Test
    void handleUserInput_whenSessionNotAwaitingInput_sendsError() {
        ChatSession session = new ChatSession();
        session.setSessionId("sess-123");
        session.setStatus("active");
        session.setCurrentNodeType("state"); // Not "input"
        session.setContext(new HashMap<>());

        when(chatSessionRepository.findBySessionId("sess-123")).thenReturn(Optional.of(session));

        service.handleUserInput("sess-123", "hello");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertTrue(error.getError().contains("not awaiting input"));
    }

    @Test
    void handleUserInput_whenSessionCompleted_sendsError() {
        ChatSession session = new ChatSession();
        session.setSessionId("sess-456");
        session.setStatus("completed");
        session.setCurrentNodeType("input");
        session.setContext(new HashMap<>());

        when(chatSessionRepository.findBySessionId("sess-456")).thenReturn(Optional.of(session));

        service.handleUserInput("sess-456", "hello");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertTrue(error.getError().contains("already completed"));
    }

    @Test
    void handleUserInput_withEmptyMessage_sendsError() {
        service.handleUserInput("some-session", "");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertTrue(error.getError().contains("Non-empty message is required"));
    }

    @Test
    void handleUserInput_withNullMessage_sendsError() {
        service.handleUserInput("some-session", null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertTrue(error.getError().contains("Non-empty message is required"));
    }

    @Test
    void startWorkflow_withNullWorkflowId_sendsError() {
        ChatSession session = new ChatSession();
        session.setSessionId("sess-123");
        session.setStatus("active");
        session.setContext(new HashMap<>());

        when(chatSessionRepository.findBySessionId("sess-123")).thenReturn(Optional.of(session));

        service.startWorkflow("sess-123", null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), captor.capture());

        ChatErrorResponse error = (ChatErrorResponse) captor.getValue();
        assertTrue(error.getError().contains("invalid") || error.getError().contains("Invalid"));
    }
}
