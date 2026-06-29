package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.repository.ChatSessionRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.processor.WorkflowNodeProcessor;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 11: Child workflow variables retained after completion
 *
 * For any variables added to the session context during child workflow execution,
 * when the child workflow completes and the engine returns to the parent workflow,
 * those variables SHALL remain present in the session context with their values unchanged.
 *
 * Feature: workflow-node, Property 11: Child workflow variables retained after completion
 *
 * Validates: Requirements 3.2, 3.3
 */
class ChildVariableRetentionPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 11: Child workflow variables retained after completion")
    void childVariablesRetainedAfterCompletionAndReturnToParent(
            @ForAll("variableNames") String variableName,
            @ForAll("variableValues") String variableValue) {

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

        ChatWebSocketHandler chatWebSocketHandler = mock(ChatWebSocketHandler.class);
        when(chatWebSocketHandler.consumePendingSession(anyString())).thenReturn(true);

        WorkflowExecutionServiceImpl service = TestServiceFactory.createService(workflowRepository, processors, placeholderService, inputValidationService, chatWebSocketHandler, new ChatMessageSender(messagingTemplate), new SessionStateManager(chatSessionRepository), new NavigationService(TestServiceFactory.createMockCacheService(workflowRepository), placeholderService), new ChildWorkflowService(TestServiceFactory.createMockCacheService(workflowRepository)));

        // Session setup
        String sessionId = "test-session-" + UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setStatus("active");
        session.setContext(new HashMap<>());

        // startWorkflow() creates its own session, so we capture it and bridge findBySessionId
        final ChatSession[] sessionHolder = new ChatSession[1];
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            sessionHolder[0] = invocation.getArgument(0);
            when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(sessionHolder[0]));
            return sessionHolder[0];
        });

        // Parent workflow: workflow node → message node (after-node)
        Long parentWorkflowId = 1L;
        Long childWorkflowId = 2L;

        String workflowNodeId = "wf-node-1";
        String afterNodeId = "after-node-1";

        Map<String, Object> parentWorkflowJson = buildParentWorkflow(workflowNodeId, afterNodeId, childWorkflowId);

        Workflow parentWorkflow = new Workflow();
        parentWorkflow.setId(parentWorkflowId);
        parentWorkflow.setName("Parent Workflow");
        parentWorkflow.setWorkflowJson(parentWorkflowJson);

        // Child workflow: input node (captures user reply into variableName) → no next node (ends)
        String childInputNodeId = "child-input-1";

        Map<String, Object> childWorkflowJson = buildChildWorkflowWithInputNode(childInputNodeId, variableName);

        Workflow childWorkflow = new Workflow();
        childWorkflow.setId(childWorkflowId);
        childWorkflow.setName("Child Workflow");
        childWorkflow.setWorkflowJson(childWorkflowJson);

        when(workflowRepository.findById(parentWorkflowId)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(childWorkflowId)).thenReturn(Optional.of(childWorkflow));

        // --- Act ---

        // Step 1: Start parent workflow → enters child → pauses at input node
        service.startWorkflow(sessionId, parentWorkflowId);

        // Verify we paused in the child at the input node
        assertThat(sessionHolder[0].getCurrentNodeId()).isEqualTo(childInputNodeId);
        assertThat(sessionHolder[0].getCurrentNodeType()).isEqualTo("input");
        assertThat(sessionHolder[0].getWorkflowId()).isEqualTo(childWorkflowId);

        // Step 2: User replies with variableValue → stored under variableName in context
        // After storing, child has no next node → handleChildWorkflowEnd → returns to parent
        // Parent continues to after-node (message node) → sends message → workflow completes
        service.handleUserInput(sessionId, variableValue);

        // --- Assert ---

        // After the full execution completes (child ended, parent continued and completed),
        // the variable stored during child execution must still be in context
        Map<String, Object> resultContext = sessionHolder[0].getContext();

        assertThat(resultContext)
                .as("Variable '%s' set during child execution should still be in context after returning to parent",
                        variableName)
                .containsKey(variableName);
        assertThat(resultContext.get(variableName))
                .as("Variable '%s' should retain value '%s' set during child execution",
                        variableName, variableValue)
                .isEqualTo(variableValue);
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> variableNames() {
        // Generate 1-15 char alphabetic variable names without underscore prefix
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(15)
                .map(String::toLowerCase);
    }

    @Provide
    Arbitrary<String> variableValues() {
        // Generate non-empty string values (user replies)
        return Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(50)
                .filter(s -> !s.trim().isEmpty());
    }

    // --- Helper methods ---

    /**
     * Builds a parent workflow with:
     * - workflow node (references child workflow) as first node
     * - message node (after-node) as second node
     * - transition: workflowNode → afterNode
     */
    private Map<String, Object> buildParentWorkflow(String workflowNodeId, String afterNodeId, Long childWorkflowId) {
        // Workflow node
        Map<String, Object> workflowNode = new HashMap<>();
        workflowNode.put("id", workflowNodeId);
        workflowNode.put("type", "state");
        workflowNode.put("name", "Call Child Workflow");
        Map<String, Object> wfConfig = new HashMap<>();
        wfConfig.put("nodeType", "workflow");
        wfConfig.put("workflowId", childWorkflowId.toString());
        workflowNode.put("config", wfConfig);

        // After-node (message node - no nodeType in config means MessageNodeProcessor handles it)
        Map<String, Object> afterNode = new HashMap<>();
        afterNode.put("id", afterNodeId);
        afterNode.put("type", "state");
        afterNode.put("name", "Back in parent");

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(workflowNode);
        nodes.add(afterNode);

        // Transition: workflowNode → afterNode
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", workflowNodeId);
        transition.put("targetNodeId", afterNodeId);

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(transition);

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        return workflowJson;
    }

    /**
     * Builds a child workflow with:
     * - input node as first (and only) node, capturing user reply into the given variableName
     * - No outgoing transition from the input node (child ends after user replies)
     *
     * The input node is the first node because it appears as the sourceNodeId in the first transition.
     * We use a dummy transition to establish it as the first node.
     */
    private Map<String, Object> buildChildWorkflowWithInputNode(String inputNodeId, String variableName) {
        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", inputNodeId);
        inputNode.put("type", "state");
        inputNode.put("name", "Enter value");
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "input");
        config.put("variableName", variableName);
        inputNode.put("config", config);

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(inputNode);

        // The input node is the sourceNodeId in the first transition to mark it as the first node.
        // But it has no valid targetNodeId (child ends after input reply).
        // We use a non-existent target so resolveNextNode returns null (end of child).
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", inputNodeId);
        transition.put("targetNodeId", "nonexistent-end-marker");

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(transition);

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        return workflowJson;
    }
}
