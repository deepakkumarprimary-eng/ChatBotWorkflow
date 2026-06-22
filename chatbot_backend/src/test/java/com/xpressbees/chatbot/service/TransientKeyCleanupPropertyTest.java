package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 3: Transient key cleanup on child workflow entry
 *
 * For any session context containing transient engine keys (_targetNodeId, _inputVariableName,
 * _displayVariable, _buttonOptions), when the engine enters a child workflow, all of these
 * transient keys SHALL be removed from the session context before the child's first node is processed.
 *
 * Feature: workflow-node, Property 3: Transient key cleanup on child workflow entry
 *
 * Validates: Requirements 3.4
 */
class TransientKeyCleanupPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 3: Transient key cleanup on child workflow entry")
    void transientKeysRemovedOnChildWorkflowEntry(
            @ForAll("transientKeyValues") Map<String, String> transientValues) {

        // --- Arrange ---

        // Set up mocks
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        InputValidationService inputValidationService = mock(InputValidationService.class);
        PlaceholderService placeholderService = new PlaceholderService();

        // Create node processors
        InputNodeProcessor inputNodeProcessor = new InputNodeProcessor();
        MessageNodeProcessor messageNodeProcessor = new MessageNodeProcessor();
        WorkflowNodeProcessor workflowNodeProcessor = new WorkflowNodeProcessor(workflowRepository);

        List<NodeProcessor> processors = List.of(
                inputNodeProcessor, messageNodeProcessor, workflowNodeProcessor);

        ChatWebSocketHandler chatWebSocketHandler = mock(ChatWebSocketHandler.class);
        when(chatWebSocketHandler.consumePendingSession(anyString())).thenReturn(true);

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, inputValidationService, chatWebSocketHandler);

        // Session with transient keys in context
        String sessionId = "test-session-" + UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        // Set generated transient key values
        context.put("_targetNodeId", transientValues.get("_targetNodeId"));
        context.put("_inputVariableName", transientValues.get("_inputVariableName"));
        context.put("_displayVariable", transientValues.get("_displayVariable"));
        context.put("_buttonOptions", transientValues.get("_buttonOptions"));
        session.setContext(context);

        // startWorkflow() creates its own session with empty context.
        // We need to inject the transient keys into it after creation so we can test they get cleaned.
        final ChatSession[] sessionHolder = new ChatSession[1];
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession savedSession = invocation.getArgument(0);
            // Inject the transient keys into the newly created session's context
            if (sessionHolder[0] == null) {
                savedSession.getContext().put("_targetNodeId", transientValues.get("_targetNodeId"));
                savedSession.getContext().put("_inputVariableName", transientValues.get("_inputVariableName"));
                savedSession.getContext().put("_displayVariable", transientValues.get("_displayVariable"));
                savedSession.getContext().put("_buttonOptions", transientValues.get("_buttonOptions"));
                sessionHolder[0] = savedSession;
            }
            when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(savedSession));
            return savedSession;
        });

        // Parent workflow: has a workflow node that references child workflow (id=2)
        Long parentWorkflowId = 1L;
        Long childWorkflowId = 2L;

        String workflowNodeId = "wf-node-1";
        Map<String, Object> parentWorkflowJson = buildParentWorkflow(workflowNodeId, childWorkflowId);

        Workflow parentWorkflow = new Workflow();
        parentWorkflow.setId(parentWorkflowId);
        parentWorkflow.setName("Parent Workflow");
        parentWorkflow.setWorkflowJson(parentWorkflowJson);

        // Child workflow: has an input node as its first node (causes PAUSE)
        String childInputNodeId = "child-input-1";
        Map<String, Object> childWorkflowJson = buildChildWorkflowWithInputNode(childInputNodeId);

        Workflow childWorkflow = new Workflow();
        childWorkflow.setId(childWorkflowId);
        childWorkflow.setName("Child Workflow");
        childWorkflow.setWorkflowJson(childWorkflowJson);

        when(workflowRepository.findById(parentWorkflowId)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(childWorkflowId)).thenReturn(Optional.of(childWorkflow));

        // --- Act ---
        service.startWorkflow(sessionId, parentWorkflowId);

        // --- Assert ---
        // After entering child workflow and pausing at input node,
        // all transient keys must be removed from context
        Map<String, Object> resultContext = sessionHolder[0].getContext();

        assertThat(resultContext)
                .as("_targetNodeId should be removed after entering child workflow")
                .doesNotContainKey("_targetNodeId");
        assertThat(resultContext)
                .as("_displayVariable should be removed after entering child workflow")
                .doesNotContainKey("_displayVariable");
        assertThat(resultContext)
                .as("_buttonOptions should be removed after entering child workflow")
                .doesNotContainKey("_buttonOptions");

        // Note: _inputVariableName is set again by the InputNodeProcessor in the child workflow
        // when it pauses, so we verify the OLD value was cleaned (the new value is from the child's
        // input node). The key exists but with the child's variableName value, not the original.
        // The clearTransientKeys method removes it, but InputNodeProcessor adds it back.
        // We verify the value is NOT the original transient value we set.
        String originalInputVarName = transientValues.get("_inputVariableName");
        Object currentInputVarName = resultContext.get("_inputVariableName");
        if (currentInputVarName != null) {
            // If _inputVariableName is present, it must be the child's value ("childInput"),
            // not our original random value
            assertThat(currentInputVarName)
                    .as("_inputVariableName should be the child input node's variableName, not the original transient value")
                    .isEqualTo("childInput");
        }

        // Assert structural keys are preserved (not removed by clearTransientKeys)
        assertThat(resultContext)
                .as("_workflowStack should still exist after entering child workflow")
                .containsKey("_workflowStack");
        assertThat(resultContext)
                .as("_navigationHistory should still exist after entering child workflow")
                .containsKey("_navigationHistory");
    }

    // --- Providers ---

    @Provide
    Arbitrary<Map<String, String>> transientKeyValues() {
        // Generate random string values for each transient key
        Arbitrary<String> values = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20);

        return values.flatMap(targetNodeId ->
                values.flatMap(inputVarName ->
                        values.flatMap(displayVar ->
                                values.map(buttonOpts -> {
                                    Map<String, String> map = new HashMap<>();
                                    map.put("_targetNodeId", targetNodeId);
                                    map.put("_inputVariableName", inputVarName);
                                    map.put("_displayVariable", displayVar);
                                    map.put("_buttonOptions", buttonOpts);
                                    return map;
                                }))));
    }

    // --- Helper methods to build workflow JSON structures ---

    /**
     * Builds a parent workflow with a single workflow node that references the child workflow.
     * Structure: workflowNode -> afterNode
     */
    private Map<String, Object> buildParentWorkflow(String workflowNodeId, Long childWorkflowId) {
        Map<String, Object> workflowNode = new HashMap<>();
        workflowNode.put("id", workflowNodeId);
        workflowNode.put("type", "state");
        workflowNode.put("name", "Call Child Workflow");
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        config.put("workflowId", childWorkflowId.toString());
        workflowNode.put("config", config);

        // A dummy "after" node for transitions (parent continues here after child completes)
        Map<String, Object> afterNode = new HashMap<>();
        afterNode.put("id", "after-child-node");
        afterNode.put("type", "state");
        afterNode.put("name", "After child");
        Map<String, Object> afterConfig = new HashMap<>();
        afterConfig.put("nodeType", "message");
        afterNode.put("config", afterConfig);

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(workflowNode);
        nodes.add(afterNode);

        // Transition: workflowNode -> afterNode
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", workflowNodeId);
        transition.put("targetNodeId", "after-child-node");

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(transition);

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        return workflowJson;
    }

    /**
     * Builds a child workflow with an input node as its first (and only) node.
     * The input node causes PAUSE, allowing us to inspect context at that point.
     */
    private Map<String, Object> buildChildWorkflowWithInputNode(String inputNodeId) {
        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", inputNodeId);
        inputNode.put("type", "state");
        inputNode.put("name", "Enter your name");
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "input");
        config.put("variableName", "childInput");
        inputNode.put("config", config);

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(inputNode);

        // Transition from input node (to make it the "first node" via transitions[0].sourceNodeId)
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", inputNodeId);
        transition.put("targetNodeId", "no-next-node");

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(transition);

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        return workflowJson;
    }
}
