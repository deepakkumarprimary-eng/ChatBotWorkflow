package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Property 8: Input Validation Rejects Invalid Workflow IDs
 *
 * For any value that is null, the startWorkflow operation SHALL produce an error response
 * without loading a workflow. For any valid non-null numeric ID,
 * the system SHALL attempt to load the corresponding workflow.
 *
 * Feature: websocket-workflow-execution, Property 8: Input Validation Rejects Invalid Workflow IDs
 */
class WorkflowIdValidationPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 8: Input Validation Rejects Invalid Workflow IDs")
    void nullWorkflowIdProducesError() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new MessageNodeProcessor());

        ChatSession session = new ChatSession();
        session.setSessionId("sess-test");
        session.setStatus("active");
        session.setContext(new HashMap<>());
        when(chatSessionRepo.findBySessionId("sess-test")).thenReturn(Optional.of(session));

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepo, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        service.startWorkflow("sess-test", null);

        // Verify error was sent
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), messageCaptor.capture());

        Object sent = messageCaptor.getValue();
        assert sent instanceof ChatErrorResponse : "Should send ChatErrorResponse for null workflowId";
        ChatErrorResponse error = (ChatErrorResponse) sent;
        assert error.getError().contains("invalid") || error.getError().contains("Invalid") :
                "Error should mention invalid. Got: " + error.getError();

        // Verify no workflow was loaded
        verify(workflowRepo, never()).findById(any());
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 8: Input Validation Rejects Invalid Workflow IDs")
    void validWorkflowIdAttemptsToLoadWorkflow(@ForAll("validWorkflowIds") Long workflowId) {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        WorkflowRepository workflowRepo = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new MessageNodeProcessor());

        ChatSession session = new ChatSession();
        session.setSessionId("sess-test");
        session.setStatus("active");
        session.setContext(new HashMap<>());
        when(chatSessionRepo.findBySessionId("sess-test")).thenReturn(Optional.of(session));
        when(workflowRepo.findById(any())).thenReturn(Optional.empty());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepo, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        service.startWorkflow("sess-test", workflowId);

        // Verify workflow was attempted to be loaded
        verify(workflowRepo).findById(workflowId);
    }

    @Provide
    Arbitrary<Long> validWorkflowIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }
}
