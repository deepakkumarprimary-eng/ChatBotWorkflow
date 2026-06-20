package com.xpressbees.chatbot.service;

import net.jqwik.api.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Property 3: Graph Traversal Returns Correct Next Node
 *
 * For any valid workflow JSON containing a transitions array and a nodes array,
 * and for any node whose id matches the sourceNodeId of at least one transition,
 * resolveNextNode SHALL return the node whose id equals the targetNodeId of the first
 * matching transition. If no transition exists with the given sourceNodeId, it SHALL return null.
 *
 * Feature: websocket-workflow-execution, Property 3: Graph Traversal Returns Correct Next Node
 */
class WorkflowGraphTraversalPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 3: Graph Traversal Returns Correct Next Node")
    void resolvesCorrectNextNodeWhenTransitionExists(@ForAll("workflowsWithTransitions") WorkflowTestData data) throws Exception {
        WorkflowExecutionServiceImpl service = createServiceInstance();
        Method resolveNextNode = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                "resolveNextNode", String.class, Map.class);
        resolveNextNode.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resolveNextNode.invoke(
                service, data.sourceNodeId, data.workflowJson);

        assert result != null : "Should find next node when transition exists";
        assert data.expectedTargetNodeId.equals(result.get("id")) :
                "Expected target node id " + data.expectedTargetNodeId + " but got " + result.get("id");
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 3: Graph Traversal Returns Correct Next Node")
    void returnsNullWhenNoTransitionExists(@ForAll("terminalNodeIds") String terminalNodeId) throws Exception {
        WorkflowExecutionServiceImpl service = createServiceInstance();
        Method resolveNextNode = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                "resolveNextNode", String.class, Map.class);
        resolveNextNode.setAccessible(true);

        Map<String, Object> workflowJson = createSimpleWorkflow();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resolveNextNode.invoke(
                service, terminalNodeId, workflowJson);

        assert result == null : "Should return null for terminal node (no outgoing transition)";
    }

    @Provide
    Arbitrary<WorkflowTestData> workflowsWithTransitions() {
        return Arbitraries.of(
                new WorkflowTestData("1", "2", createWorkflow(
                        List.of(node("1", "First"), node("2", "Second"), node("3", "Third")),
                        List.of(transition("1", "2"), transition("2", "3"))
                )),
                new WorkflowTestData("2", "3", createWorkflow(
                        List.of(node("1", "First"), node("2", "Second"), node("3", "Third")),
                        List.of(transition("1", "2"), transition("2", "3"))
                )),
                new WorkflowTestData("A", "B", createWorkflow(
                        List.of(node("A", "NodeA"), node("B", "NodeB")),
                        List.of(transition("A", "B"))
                )),
                new WorkflowTestData("start", "middle", createWorkflow(
                        List.of(node("start", "Start"), node("middle", "Middle"), node("end", "End")),
                        List.of(transition("start", "middle"), transition("middle", "end"))
                ))
        );
    }

    @Provide
    Arbitrary<String> terminalNodeIds() {
        // These IDs don't appear as sourceNodeId in the simple workflow
        return Arbitraries.of("3", "999", "nonexistent", "end");
    }

    private Map<String, Object> createSimpleWorkflow() {
        return createWorkflow(
                List.of(node("1", "First"), node("2", "Second"), node("3", "Third")),
                List.of(transition("1", "2"), transition("2", "3"))
        );
    }

    private Map<String, Object> createWorkflow(List<Map<String, Object>> nodes,
                                                List<Map<String, Object>> transitions) {
        Map<String, Object> wf = new HashMap<>();
        wf.put("nodes", nodes);
        wf.put("transitions", transitions);
        return wf;
    }

    private static Map<String, Object> node(String id, String name) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", id);
        n.put("name", name);
        n.put("type", "state");
        n.put("config", null);
        return n;
    }

    private static Map<String, Object> transition(String sourceId, String targetId) {
        Map<String, Object> t = new HashMap<>();
        t.put("sourceNodeId", sourceId);
        t.put("targetNodeId", targetId);
        return t;
    }

    private WorkflowExecutionServiceImpl createServiceInstance() {
        return new WorkflowExecutionServiceImpl(null, null, List.of(), null, null, null);
    }

    record WorkflowTestData(String sourceNodeId, String expectedTargetNodeId, Map<String, Object> workflowJson) {}
}
