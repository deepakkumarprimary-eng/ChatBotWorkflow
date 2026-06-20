package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.processor.WorkflowNodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 6: Session completion on empty stack at workflow end
 *
 * For any session with an empty workflow stack whose currently executing node
 * has no outgoing transitions, the engine SHALL set the session status to "completed".
 *
 * Feature: workflow-node, Property 6: Session completion on empty stack at workflow end
 *
 * Validates: Requirements 4.6
 */
class SessionCompletionPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 6: Session completion on empty stack at workflow end")
    void sessionCompletedWhenWorkflowEndsWithEmptyStack(
            @ForAll("sessionIds") String sessionId,
            @ForAll("workflowIds") Long workflowId,
            @ForAll("extraContextVariables") Map<String, String> extraVariables) {

        // --- Arrange ---

        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        InputValidationService inputValidationService = mock(InputValidationService.class);
        PlaceholderService placeholderService = new PlaceholderService();

        InputNodeProcessor inputNodeProcessor = new InputNodeProcessor();
        MessageNodeProcessor messageNodeProcessor = new MessageNodeProcessor();
        WorkflowNodeProcessor workflowNodeProcessor = new WorkflowNodeProcessor(workflowRepository);

        List<NodeProcessor> processors = List.of(
                inputNodeProcessor, messageNodeProcessor, workflowNodeProcessor);

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, inputValidationService);

        // Create a session with an EMPTY _workflowStack and arbitrary context variables
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId(sessionId);
        session.setWorkflowId(workflowId);
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        // Add the extra variables (simulates session context from prior execution)
        for (Map.Entry<String, String> entry : extraVariables.entrySet()) {
            context.put(entry.getKey(), entry.getValue());
        }
        // Explicitly set an empty workflow stack (or no stack at all — the engine initializes it)
        context.put("_workflowStack", new ArrayList<>());
        session.setContext(context);

        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Build a simple workflow with a single message node that has no outgoing transition
        // (target node doesn't exist in nodes list, so resolveNextNode returns null)
        String messageNodeId = "msg-node-" + UUID.randomUUID();
        Map<String, Object> workflowJson = buildSingleMessageNodeWorkflow(messageNodeId);

        Workflow workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setName("Test Workflow");
        workflow.setWorkflowJson(workflowJson);

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        // --- Act ---
        service.startWorkflow(sessionId, workflowId);

        // --- Assert ---
        assertThat(session.getStatus())
                .as("Session status should be 'completed' when workflow ends with empty stack")
                .isEqualTo("completed");
    }

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 6: Session completion on empty stack at workflow end")
    void sessionCompletedWhenWorkflowEndsWithNoStackInContext(
            @ForAll("sessionIds") String sessionId,
            @ForAll("workflowIds") Long workflowId,
            @ForAll("extraContextVariables") Map<String, String> extraVariables) {

        // --- Arrange ---
        // Same test but with NO _workflowStack key in context at all (engine should initialize it as empty)

        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        InputValidationService inputValidationService = mock(InputValidationService.class);
        PlaceholderService placeholderService = new PlaceholderService();

        InputNodeProcessor inputNodeProcessor = new InputNodeProcessor();
        MessageNodeProcessor messageNodeProcessor = new MessageNodeProcessor();
        WorkflowNodeProcessor workflowNodeProcessor = new WorkflowNodeProcessor(workflowRepository);

        List<NodeProcessor> processors = List.of(
                inputNodeProcessor, messageNodeProcessor, workflowNodeProcessor);

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, inputValidationService);

        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId(sessionId);
        session.setWorkflowId(workflowId);
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        // Add extra variables but do NOT add _workflowStack — it shouldn't exist
        for (Map.Entry<String, String> entry : extraVariables.entrySet()) {
            context.put(entry.getKey(), entry.getValue());
        }
        session.setContext(context);

        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String messageNodeId = "msg-node-" + UUID.randomUUID();
        Map<String, Object> workflowJson = buildSingleMessageNodeWorkflow(messageNodeId);

        Workflow workflow = new Workflow();
        workflow.setId(workflowId);
        workflow.setName("Test Workflow");
        workflow.setWorkflowJson(workflowJson);

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

        // --- Act ---
        service.startWorkflow(sessionId, workflowId);

        // --- Assert ---
        assertThat(session.getStatus())
                .as("Session status should be 'completed' when workflow ends with no stack in context")
                .isEqualTo("completed");
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "session-" + s);
    }

    @Provide
    Arbitrary<Long> workflowIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Map<String, String>> extraContextVariables() {
        // Generate 0-5 user variables with random keys (no underscore prefix) and values
        Arbitrary<String> keys = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(12)
                .map(String::toLowerCase);

        Arbitrary<String> values = Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(20);

        return Arbitraries.maps(keys, values)
                .ofMinSize(0)
                .ofMaxSize(5);
    }

    // --- Helper methods ---

    /**
     * Builds a workflow with a single message node that effectively has no outgoing transition.
     * The transition's target node ID doesn't exist in the nodes list, so resolveNextNode returns null.
     * This simulates a workflow ending at that node.
     */
    private Map<String, Object> buildSingleMessageNodeWorkflow(String messageNodeId) {
        Map<String, Object> messageNode = new HashMap<>();
        messageNode.put("id", messageNodeId);
        messageNode.put("type", "state");
        messageNode.put("name", "End message");
        // No config with nodeType -> handled by MessageNodeProcessor

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(messageNode);

        // A single transition with this node as source but target pointing to non-existent node
        // This makes findFirstNode pick up this node, but resolveNextNode returns null
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", messageNodeId);
        transition.put("targetNodeId", "non-existent-end-node");

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(transition);

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        return workflowJson;
    }
}
