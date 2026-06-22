package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Method;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Property 5: Workflow stack pop and parent restoration
 *
 * For any non-empty workflow stack, when a child workflow reaches its end (no outgoing transitions),
 * the engine SHALL pop the top entry and set session.workflowId to the popped entry's parentWorkflowId,
 * resulting in a stack of size N-1.
 *
 * Feature: workflow-node, Property 5: Workflow stack pop and parent restoration
 *
 * Validates: Requirements 4.3, 4.4, 4.5
 */
class WorkflowStackPopPropertyTest {

    @Property(tries = 100)
    void stackPopRestoresParentWorkflowId(@ForAll("stackScenarios") StackPopScenario scenario) throws Exception {
        // Set up mocks
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor(), new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, null, null);

        // Create session pointing to child workflow
        ChatSession session = new ChatSession();
        session.setSessionId("test-session-" + UUID.randomUUID());
        session.setWorkflowId(scenario.childWorkflowId);
        session.setStatus("active");

        // Build context with pre-populated workflow stack
        Map<String, Object> context = new HashMap<>();
        List<Map<String, Object>> stack = new ArrayList<>(scenario.workflowStack);
        context.put("_workflowStack", stack);
        session.setContext(context);

        int originalStackSize = stack.size();
        Long expectedParentWorkflowId = ((Number) stack.get(stack.size() - 1).get("parentWorkflowId")).longValue();

        // Mock parent workflow - has a transition from workflowNodeId to an input node (which will PAUSE)
        Workflow parentWorkflow = new Workflow();
        parentWorkflow.setId(expectedParentWorkflowId);
        parentWorkflow.setName("Parent Workflow");
        parentWorkflow.setWorkflowJson(scenario.parentWorkflowJson);
        when(workflowRepository.findById(expectedParentWorkflowId)).thenReturn(Optional.of(parentWorkflow));

        // Mock session save (needed when PAUSE saves state)
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Invoke processNodes via reflection with child's terminal message node
        // The child message node has no outgoing transitions in the child workflow JSON,
        // so after processing it, resolveNextNode returns null.
        // Since _workflowStack is non-empty, handleChildWorkflowEnd is called:
        //   - pops top entry
        //   - restores session workflowId to parentWorkflowId
        //   - loads parent workflow, finds next node (input node) after workflowNodeId
        //   - calls processNodes on parent starting at input node → PAUSE stops execution
        Method processNodes = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                "processNodes", ChatSession.class, Map.class, Map.class);
        processNodes.setAccessible(true);
        processNodes.invoke(service, session, scenario.childStartNode, scenario.childWorkflowJson);

        // Assert: stack size decreased by exactly 1
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultStack = (List<Map<String, Object>>) session.getContext().get("_workflowStack");
        assert resultStack != null : "Workflow stack should still exist in context";
        assert resultStack.size() == originalStackSize - 1 :
                "Stack size should decrease by 1. Expected " + (originalStackSize - 1) +
                " but got " + resultStack.size();

        // Assert: session workflowId restored to parentWorkflowId from popped entry
        assert session.getWorkflowId().equals(expectedParentWorkflowId) :
                "Session workflowId should be restored to parent. Expected " +
                expectedParentWorkflowId + " but got " + session.getWorkflowId();
    }

    @Provide
    Arbitrary<StackPopScenario> stackScenarios() {
        // Generate stack sizes between 1 and 5
        return Arbitraries.integers().between(1, 5).flatMap(stackSize -> {
            return Arbitraries.longs().between(100L, 999L).flatMap(parentWorkflowId -> {
                return Arbitraries.longs().between(1000L, 9999L).flatMap(childWorkflowId -> {
                    return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(10).map(nodeIdSuffix -> {
                        String workflowNodeId = "wf-node-" + nodeIdSuffix;
                        String inputNodeId = "input-node-" + nodeIdSuffix;

                        // Build the workflow stack with N entries
                        // The TOP entry (last in list) references the parent we'll return to
                        List<Map<String, Object>> stack = new ArrayList<>();
                        for (int i = 0; i < stackSize - 1; i++) {
                            Map<String, Object> entry = new HashMap<>();
                            entry.put("parentWorkflowId", (long) (i + 1));
                            entry.put("workflowNodeId", "older-wf-node-" + i);
                            stack.add(entry);
                        }
                        // Top entry - the one that will be popped
                        Map<String, Object> topEntry = new HashMap<>();
                        topEntry.put("parentWorkflowId", parentWorkflowId);
                        topEntry.put("workflowNodeId", workflowNodeId);
                        stack.add(topEntry);

                        // Parent workflow: workflowNodeId -> inputNodeId (input node causes PAUSE)
                        // The input node stops execution, preventing cascading pops
                        Map<String, Object> parentWorkflowJson = createParentWorkflow(workflowNodeId, inputNodeId);

                        // Child workflow: single message node with NO outgoing transitions
                        String childNodeId = "child-msg-" + nodeIdSuffix;
                        Map<String, Object> childWorkflowJson = createChildWorkflow(childNodeId);
                        Map<String, Object> childStartNode = createMessageNode(childNodeId, "Child message");

                        return new StackPopScenario(
                                stack,
                                parentWorkflowId,
                                childWorkflowId,
                                parentWorkflowJson,
                                childWorkflowJson,
                                childStartNode
                        );
                    });
                });
            });
        });
    }

    private Map<String, Object> createParentWorkflow(String workflowNodeId, String inputNodeId) {
        // Parent workflow: workflowNodeId -> inputNodeId (input node will PAUSE execution)
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createWorkflowTypeNode(workflowNodeId, "Call Child"));
        nodes.add(createInputNode(inputNodeId, "Enter your name"));

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(createTransition(workflowNodeId, inputNodeId));

        Map<String, Object> wf = new HashMap<>();
        wf.put("nodes", nodes);
        wf.put("transitions", transitions);
        return wf;
    }

    private Map<String, Object> createChildWorkflow(String nodeId) {
        // Child workflow: single message node with NO outgoing transitions (terminal)
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createMessageNode(nodeId, "Child only message"));

        // Empty transitions list means resolveNextNode will return null for the child node
        List<Map<String, Object>> transitions = new ArrayList<>();

        Map<String, Object> wf = new HashMap<>();
        wf.put("nodes", nodes);
        wf.put("transitions", transitions);
        return wf;
    }

    private Map<String, Object> createMessageNode(String id, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        node.put("config", null);
        return node;
    }

    private Map<String, Object> createInputNode(String id, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "input");
        config.put("variableName", "userInput");
        node.put("config", config);
        return node;
    }

    private Map<String, Object> createWorkflowTypeNode(String id, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        config.put("workflowId", "999");
        node.put("config", config);
        return node;
    }

    private Map<String, Object> createTransition(String sourceId, String targetId) {
        Map<String, Object> t = new HashMap<>();
        t.put("sourceNodeId", sourceId);
        t.put("targetNodeId", targetId);
        return t;
    }

    record StackPopScenario(
            List<Map<String, Object>> workflowStack,
            Long parentWorkflowId,
            Long childWorkflowId,
            Map<String, Object> parentWorkflowJson,
            Map<String, Object> childWorkflowJson,
            Map<String, Object> childStartNode
    ) {}
}
