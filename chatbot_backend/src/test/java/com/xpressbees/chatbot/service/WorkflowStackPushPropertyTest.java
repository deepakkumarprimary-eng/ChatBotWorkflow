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
import net.jqwik.api.constraints.IntRange;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property 4: Workflow stack push correctness
 *
 * For any workflow stack of size N (where N < 10) and any workflow node being processed,
 * after the engine pushes a stack entry, the stack SHALL have size N+1 and the top entry
 * SHALL contain the current session workflowId as parentWorkflowId and the workflow node's
 * id as workflowNodeId.
 *
 * Feature: workflow-node, Property 4: Workflow stack push correctness
 *
 * Validates: Requirements 4.2
 */
class WorkflowStackPushPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 4: Workflow stack push correctness")
    void stackPushAddsCorrectEntryWithParentWorkflowIdAndNodeId(
            @ForAll @IntRange(min = 0, max = 9) int initialStackSize,
            @ForAll("parentWorkflowIds") Long parentWorkflowId,
            @ForAll("workflowNodeIds") String workflowNodeId) {

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

        // Create session with pre-populated workflow stack of size N
        String sessionId = "test-session-" + UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionId(sessionId);
        session.setWorkflowId(parentWorkflowId);
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        List<Map<String, Object>> existingStack = new ArrayList<>();
        for (int i = 0; i < initialStackSize; i++) {
            Map<String, Object> stackEntry = new HashMap<>();
            stackEntry.put("parentWorkflowId", (long) (100 + i));
            stackEntry.put("workflowNodeId", "existing-node-" + i);
            existingStack.add(stackEntry);
        }
        context.put("_workflowStack", existingStack);
        session.setContext(context);

        // startWorkflow() creates a new session with empty context, so we inject the
        // pre-populated stack into it after creation
        final ChatSession[] sessionHolder = new ChatSession[1];
        final List<Map<String, Object>> stackToInject = existingStack;
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession savedSession = invocation.getArgument(0);
            if (sessionHolder[0] == null) {
                // Inject the pre-existing stack entries into the new session's context
                savedSession.getContext().put("_workflowStack", new ArrayList<>(stackToInject));
            }
            sessionHolder[0] = savedSession;
            when(chatSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(savedSession));
            return savedSession;
        });

        // Parent workflow: has a workflow node that references child workflow (id=999)
        Long childWorkflowId = 999L;

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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultStack = (List<Map<String, Object>>) sessionHolder[0].getContext().get("_workflowStack");

        assertThat(resultStack)
                .as("Stack size should be N+1 after push (N=%d)", initialStackSize)
                .hasSize(initialStackSize + 1);

        // The top (last) entry should have the correct parentWorkflowId and workflowNodeId
        Map<String, Object> topEntry = resultStack.get(initialStackSize);
        assertThat(topEntry.get("parentWorkflowId"))
                .as("Top entry parentWorkflowId should match the parent workflow's ID")
                .isEqualTo(parentWorkflowId);
        assertThat(topEntry.get("workflowNodeId"))
                .as("Top entry workflowNodeId should match the workflow node's ID in the parent")
                .isEqualTo(workflowNodeId);

        // Verify existing stack entries are unchanged
        for (int i = 0; i < initialStackSize; i++) {
            Map<String, Object> entry = resultStack.get(i);
            assertThat(entry.get("parentWorkflowId"))
                    .as("Existing stack entry %d parentWorkflowId should be unchanged", i)
                    .isEqualTo((long) (100 + i));
            assertThat(entry.get("workflowNodeId"))
                    .as("Existing stack entry %d workflowNodeId should be unchanged", i)
                    .isEqualTo("existing-node-" + i);
        }
    }

    // --- Providers ---

    @Provide
    Arbitrary<Long> parentWorkflowIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> workflowNodeIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "wf-node-" + s);
    }

    // --- Helper methods to build workflow JSON structures ---

    /**
     * Builds a parent workflow with a single workflow node that references the child workflow.
     * The workflow node is the first node (resolved from the first transition's sourceNodeId).
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
     * The input node causes PAUSE, allowing us to inspect the stack at that point.
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

        // Transition: inputNode is the first node (sourceNodeId of the first transition)
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
