package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.WorkflowNodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 2: Context preservation on child workflow entry
 *
 * For any session context containing arbitrary user variables (non-underscore-prefixed keys),
 * when the engine enters a child workflow, all non-transient keys shall remain present in the
 * session context with their original values.
 *
 * Feature: workflow-node, Property 2: Context preservation on child workflow entry
 *
 * Validates: Requirements 2.5, 3.1
 */
class ContextPreservationPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 2: Context preservation on child workflow entry")
    void userVariablesPreservedOnChildWorkflowEntry(
            @ForAll("userVariableMaps") Map<String, String> userVariables) {

        // --- Arrange ---

        // Set up repositories and messaging
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        InputValidationService inputValidationService = mock(InputValidationService.class);
        PlaceholderService placeholderService = new PlaceholderService();

        // Create node processors
        InputNodeProcessor inputNodeProcessor = new InputNodeProcessor();
        MessageNodeProcessor messageNodeProcessor = new MessageNodeProcessor();
        WorkflowNodeProcessor workflowNodeProcessor = new WorkflowNodeProcessor(workflowRepository);

        List<com.xpressbees.chatbot.processor.NodeProcessor> processors = List.of(
                inputNodeProcessor, messageNodeProcessor, workflowNodeProcessor);

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, inputValidationService);

        // Session with user variables in context
        String sessionId = "test-session-" + UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId(sessionId);
        session.setWorkflowId(1L);
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        // Copy all user variables into context
        for (Map.Entry<String, String> entry : userVariables.entrySet()) {
            context.put(entry.getKey(), entry.getValue());
        }
        session.setContext(context);

        // Snapshot of original user variables for assertion
        Map<String, String> originalVariables = new HashMap<>(userVariables);

        when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        // After execution pauses in the child workflow at the input node,
        // all original user variables must still be present with original values
        Map<String, Object> resultContext = session.getContext();

        for (Map.Entry<String, String> entry : originalVariables.entrySet()) {
            assertThat(resultContext)
                    .as("User variable '%s' should be preserved in context after child workflow entry", entry.getKey())
                    .containsKey(entry.getKey());
            assertThat(resultContext.get(entry.getKey()))
                    .as("User variable '%s' should retain its original value '%s'", entry.getKey(), entry.getValue())
                    .isEqualTo(entry.getValue());
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<Map<String, String>> userVariableMaps() {
        // Generate 1-10 user variables with random string keys (no underscore prefix) and string values
        Arbitrary<String> keys = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(15)
                .map(s -> s.toLowerCase()); // ensure no underscore prefix

        Arbitrary<String> values = Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(30);

        return Arbitraries.maps(keys, values)
                .ofMinSize(1)
                .ofMaxSize(10);
    }

    // --- Helper methods to build workflow JSON structures ---

    /**
     * Builds a parent workflow with a single workflow node that references the child workflow.
     * Structure: workflowNode (first node, triggers child workflow entry)
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

        // Single transition from a dummy source to input node (to make it the "first node")
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", inputNodeId);
        transition.put("targetNodeId", "no-next-node"); // no actual target needed since it pauses

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(transition);

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        return workflowJson;
    }
}
