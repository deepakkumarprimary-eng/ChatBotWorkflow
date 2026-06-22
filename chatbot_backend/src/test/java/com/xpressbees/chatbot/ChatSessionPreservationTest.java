package com.xpressbees.chatbot;

import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.InputValidationService;
import com.xpressbees.chatbot.service.PlaceholderService;
import com.xpressbees.chatbot.service.WorkflowExecutionServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Preservation Property Tests - Existing Chat Start and Message Flows Unchanged.
 *
 * These tests verify that existing flows (startWorkflow, handleUserInput) that operate
 * on already-persisted sessions work correctly on UNFIXED code. They must PASS before
 * and after the fix to confirm no regressions.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
 */
class ChatSessionPreservationTest {

    private ChatSessionRepository chatSessionRepository;
    private WorkflowRepository workflowRepository;
    private SimpMessagingTemplate messagingTemplate;
    private PlaceholderService placeholderService;
    private InputValidationService inputValidationService;
    private NodeProcessor messageNodeProcessor;
    private ChatWebSocketHandler chatWebSocketHandler;
    private WorkflowExecutionServiceImpl service;

    private void setup() {
        chatSessionRepository = mock(ChatSessionRepository.class);
        workflowRepository = mock(WorkflowRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        placeholderService = new PlaceholderService();
        inputValidationService = mock(InputValidationService.class);
        messageNodeProcessor = mock(NodeProcessor.class);
        chatWebSocketHandler = mock(ChatWebSocketHandler.class);

        // MessageNodeProcessor handles all nodes in our test (returns CONTINUE with a response)
        when(messageNodeProcessor.canHandle(any())).thenReturn(true);

        service = new WorkflowExecutionServiceImpl(
                workflowRepository,
                chatSessionRepository,
                List.of(messageNodeProcessor),
                placeholderService,
                messagingTemplate,
                inputValidationService,
                chatWebSocketHandler
        );
    }

    /**
     * Helper to create a simple workflow JSON with one message node and one transition.
     */
    private Map<String, Object> createSimpleWorkflowJson(String nodeId) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", nodeId);
        node.put("name", "Hello");
        node.put("config", Map.of("nodeType", "message"));

        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", nodeId);
        transition.put("targetNodeId", "end-node");

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", List.of(node));
        workflowJson.put("transitions", List.of(transition));
        return workflowJson;
    }

    /**
     * Helper to create a persisted ChatSession entity.
     */
    private ChatSession createPersistedSession(String sessionId, Long workflowId) {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId(sessionId);
        session.setWorkflowId(workflowId);
        session.setStatus("active");
        session.setContext(new HashMap<>());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }

    /**
     * Property: For all valid workflowId values (referencing existing workflows),
     * startWorkflow() with a known persisted session loads the workflow, finds first node,
     * and begins processing.
     *
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 50)
    void startWorkflowWithValidWorkflowId_loadsAndProcesses(
            @ForAll @LongRange(min = 1, max = 1000) Long workflowId) {
        setup();

        String sessionId = UUID.randomUUID().toString();
        String nodeId = "node-" + workflowId;

        // Mock: session was created during chat.init (pending session exists)
        when(chatWebSocketHandler.consumePendingSession(sessionId)).thenReturn(true);
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock: workflow exists with a valid workflow JSON
        Workflow workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setName("Test Workflow " + workflowId);
        workflow.setWorkflowJson(createSimpleWorkflowJson(nodeId));
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        // Mock: node processor returns CONTINUE (simulates message node processing)
        when(messageNodeProcessor.process(any(), any(), any())).thenReturn(
                new NodeProcessingResult(
                        NodeProcessingResult.Action.CONTINUE,
                        new ChatResponse(null, "Hello from node", sessionId)
                )
        );

        // Act
        service.startWorkflow(sessionId, workflowId);

        // Assert: pending session was consumed
        verify(chatWebSocketHandler).consumePendingSession(sessionId);

        // Assert: workflow was loaded
        verify(workflowRepository).findById(workflowId);

        // Assert: node processor was invoked (workflow execution started)
        verify(messageNodeProcessor, atLeastOnce()).process(any(), any(), any());

        // Assert: response was sent via messaging template (no error)
        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/chat/" + sessionId), responseCaptor.capture());

        // Verify at least one response is a ChatResponse (not an error)
        boolean hasChatResponse = responseCaptor.getAllValues().stream()
                .anyMatch(r -> r instanceof ChatResponse);
        assertThat(hasChatResponse).isTrue();
    }

    /**
     * Property: For all invalid workflowId values (not in DB), startWorkflow()
     * sends "Workflow not found" error.
     *
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 50)
    void startWorkflowWithInvalidWorkflowId_sendsWorkflowNotFoundError(
            @ForAll @LongRange(min = 1, max = 1000) Long workflowId) {
        setup();

        String sessionId = UUID.randomUUID().toString();

        // Mock: session was created during chat.init (pending session exists)
        when(chatWebSocketHandler.consumePendingSession(sessionId)).thenReturn(true);

        // Mock: workflow does NOT exist
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.empty());

        // Act
        service.startWorkflow(sessionId, workflowId);

        // Assert: error message "Workflow not found" is sent
        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/" + sessionId), responseCaptor.capture());

        Object sentMessage = responseCaptor.getValue();
        assertThat(sentMessage).isInstanceOf(ChatErrorResponse.class);
        ChatErrorResponse errorResponse = (ChatErrorResponse) sentMessage;
        assertThat(errorResponse.getError()).contains("Workflow not found");
        assertThat(errorResponse.getSessionId()).isEqualTo(sessionId);
    }

    /**
     * Property: For all valid persisted sessions, handleUserInput() looks up the session
     * and processes input without error. Tests the "input" node type resume path.
     *
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 50)
    void handleUserInputWithValidSession_processesWithoutError(
            @ForAll @LongRange(min = 1, max = 1000) Long workflowId) {
        setup();

        String sessionId = UUID.randomUUID().toString();
        ChatSession session = createPersistedSession(sessionId, workflowId);
        session.setCurrentNodeId("input-node-1");
        session.setCurrentNodeType("input");

        // Set up context with _inputVariableName (simulates an input node waiting for reply)
        Map<String, Object> context = new HashMap<>();
        context.put("_inputVariableName", "userName");
        session.setContext(context);

        // Mock: session is persisted
        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock: workflow exists with a node that has no validation config
        String nodeId = "input-node-1";
        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", nodeId);
        inputNode.put("name", "Enter your name");
        inputNode.put("config", Map.of("nodeType", "input"));

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", List.of(inputNode));
        workflowJson.put("transitions", List.of()); // No next node — workflow ends

        Workflow workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setName("Test Workflow");
        workflow.setWorkflowJson(workflowJson);
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        // Act
        service.handleUserInput(sessionId, "John");

        // Assert: session was looked up
        verify(chatSessionRepository).findBySessionId(sessionId);

        // Assert: session was saved (state updated after processing input)
        verify(chatSessionRepository, atLeastOnce()).save(any(ChatSession.class));

        // Assert: the user input was stored in context
        assertThat(session.getContext().get("userName")).isEqualTo("John");
    }

    /**
     * Property: startWorkflow() with an unknown sessionId sends "No active session found" error.
     *
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 50)
    void startWorkflowWithUnknownSessionId_sendsNoActiveSessionError(
            @ForAll @LongRange(min = 1, max = 1000) Long workflowId) {
        setup();

        String unknownSessionId = UUID.randomUUID().toString();

        // Mock: session was NOT created during chat.init (not in pending map)
        when(chatWebSocketHandler.consumePendingSession(unknownSessionId)).thenReturn(false);

        // Act
        service.startWorkflow(unknownSessionId, workflowId);

        // Assert: error "No active session found" is sent
        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/" + unknownSessionId), responseCaptor.capture());

        Object sentMessage = responseCaptor.getValue();
        assertThat(sentMessage).isInstanceOf(ChatErrorResponse.class);
        ChatErrorResponse errorResponse = (ChatErrorResponse) sentMessage;
        assertThat(errorResponse.getError()).isEqualTo("No active session found");
        assertThat(errorResponse.getSessionId()).isEqualTo(unknownSessionId);
    }
}
