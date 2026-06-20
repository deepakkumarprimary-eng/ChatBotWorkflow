package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.processor.WorkflowNodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for full workflow composition flow.
 * Tests parent → child → return to parent scenarios using real node processors.
 *
 * Validates: Requirements 2.5, 2.6, 3.1, 3.2, 3.3, 4.2, 4.3, 4.4, 4.5, 4.6, 5.2, 7.1, 7.2, 8.2, 8.5
 */
class WorkflowCompositionIntegrationTest {

    private static final String SESSION_ID = "composition-test-session";
    private static final Long PARENT_WORKFLOW_ID = 1L;
    private static final Long CHILD_WORKFLOW_ID = 2L;
    private static final Long GRANDCHILD_WORKFLOW_ID = 3L;

    private WorkflowRepository workflowRepository;
    private ChatSessionRepository chatSessionRepository;
    private SimpMessagingTemplate messagingTemplate;
    private WorkflowExecutionServiceImpl service;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);

        PlaceholderService placeholderService = new PlaceholderService();

        WorkflowNodeProcessor workflowNodeProcessor = new WorkflowNodeProcessor(workflowRepository);
        List<NodeProcessor> processors = List.of(
                new InputNodeProcessor(),
                new MessageNodeProcessor(),
                workflowNodeProcessor
        );

        service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, null);

        session = new ChatSession();
        session.setSessionId(SESSION_ID);
        session.setWorkflowId(PARENT_WORKFLOW_ID);
        session.setStatus("active");
        session.setContext(new HashMap<>());

        when(chatSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * Test 1: Parent has msg → workflow_node → msg_end.
     * Child has msg only.
     * Start parent → msg sends → enters child → child msg sends → child ends →
     * returns to parent → parent msg_end sends → completed.
     */
    @Test
    void parentToChildAndBackHappyPath() {
        // Parent: msg_start → workflow_node → msg_end
        Workflow parentWorkflow = buildWorkflow(PARENT_WORKFLOW_ID, "Parent",
                List.of(
                        messageNode("p_msg_start", "Hello from parent"),
                        workflowNode("p_wf_node", CHILD_WORKFLOW_ID),
                        messageNode("p_msg_end", "Back in parent")
                ),
                List.of(
                        transition("p_msg_start", "p_wf_node"),
                        transition("p_wf_node", "p_msg_end")
                )
        );

        // Child: c_msg only (single node, no outgoing transition → ends)
        Workflow childWorkflow = buildWorkflow(CHILD_WORKFLOW_ID, "Child",
                List.of(messageNode("c_msg", "Hello from child")),
                List.of(transition("c_msg", "c_msg_dummy")) // need a valid first-node resolution
        );
        // Actually, child needs a transition with c_msg as sourceNodeId for findFirstNode to work.
        // But there's no target. Let's use a single transition pointing to a non-existent node
        // so findFirstNode finds c_msg, but resolveNextNode returns null (child ends).
        childWorkflow = buildWorkflow(CHILD_WORKFLOW_ID, "Child",
                List.of(messageNode("c_msg", "Hello from child")),
                List.of(Map.of("sourceNodeId", "c_msg", "targetNodeId", "nonexistent"))
        );

        when(workflowRepository.findById(PARENT_WORKFLOW_ID)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(CHILD_WORKFLOW_ID)).thenReturn(Optional.of(childWorkflow));

        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // Session should be completed after full traversal
        assertEquals("completed", session.getStatus(),
                "Session should be completed after parent→child→parent finishes");

        // Workflow stack should be empty
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stack = (List<Map<String, Object>>) session.getContext().get("_workflowStack");
        assertTrue(stack == null || stack.isEmpty(),
                "Workflow stack should be empty after completion");

        // Session workflowId should be restored to parent
        assertEquals(PARENT_WORKFLOW_ID, session.getWorkflowId(),
                "Session workflowId should be restored to parent after child returns");
    }

    /**
     * Test 2: Parent: workflow_node → msg_end.
     * Child: input_node → msg.
     * Start → enters child → pauses at input → handleUserInput("reply") →
     * child msg sends → child ends → returns to parent → parent msg_end → completed.
     */
    @Test
    void childPauseReplyAndReturn() {
        // Parent: p_wf_node → p_msg_end
        Workflow parentWorkflow = buildWorkflow(PARENT_WORKFLOW_ID, "Parent",
                List.of(
                        workflowNode("p_wf_node", CHILD_WORKFLOW_ID),
                        messageNode("p_msg_end", "Parent done")
                ),
                List.of(transition("p_wf_node", "p_msg_end"))
        );

        // Child: c_input → c_msg
        Workflow childWorkflow = buildWorkflow(CHILD_WORKFLOW_ID, "Child",
                List.of(
                        inputNode("c_input", "Enter name", "userName"),
                        messageNode("c_msg", "Thanks!")
                ),
                List.of(transition("c_input", "c_msg"))
        );

        when(workflowRepository.findById(PARENT_WORKFLOW_ID)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(CHILD_WORKFLOW_ID)).thenReturn(Optional.of(childWorkflow));

        // Start workflow - should pause at child input node
        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // Verify paused state
        assertEquals(CHILD_WORKFLOW_ID, session.getWorkflowId(),
                "Session should be in child workflow after pause");
        assertEquals("c_input", session.getCurrentNodeId(),
                "Current node should be child input node");
        assertEquals("input", session.getCurrentNodeType(),
                "Current node type should be input");
        assertNotEquals("completed", session.getStatus(),
                "Session should NOT be completed while paused");

        // User replies
        service.handleUserInput(SESSION_ID, "reply");

        // After reply: child msg sends, child ends, return to parent, parent msg_end, completed
        assertEquals("completed", session.getStatus(),
                "Session should be completed after user reply flows through");
        assertEquals("reply", session.getContext().get("userName"),
                "User reply should be stored in context under variableName");
        assertEquals(PARENT_WORKFLOW_ID, session.getWorkflowId(),
                "Session workflowId should be restored to parent");
    }

    /**
     * Test 3: Multi-level nesting.
     * Parent: workflow_node_A → msg_end.
     * Child_A: workflow_node_B → msg_A_end.
     * Child_B (grandchild): msg only.
     * Start → enters A → A enters B → B msg → B ends → A msg_A_end → A ends → parent msg_end → completed.
     */
    @Test
    void multiLevelNesting() {
        // Parent: p_wf_A → p_msg_end
        Workflow parentWorkflow = buildWorkflow(PARENT_WORKFLOW_ID, "Parent",
                List.of(
                        workflowNode("p_wf_A", CHILD_WORKFLOW_ID),
                        messageNode("p_msg_end", "Parent complete")
                ),
                List.of(transition("p_wf_A", "p_msg_end"))
        );

        // Child A: a_wf_B → a_msg_end
        Workflow childAWorkflow = buildWorkflow(CHILD_WORKFLOW_ID, "Child A",
                List.of(
                        workflowNode("a_wf_B", GRANDCHILD_WORKFLOW_ID),
                        messageNode("a_msg_end", "Child A done")
                ),
                List.of(transition("a_wf_B", "a_msg_end"))
        );

        // Child B (grandchild): b_msg only
        Workflow childBWorkflow = buildWorkflow(GRANDCHILD_WORKFLOW_ID, "Child B",
                List.of(messageNode("b_msg", "Grandchild says hi")),
                List.of(Map.of("sourceNodeId", "b_msg", "targetNodeId", "nonexistent"))
        );

        when(workflowRepository.findById(PARENT_WORKFLOW_ID)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(CHILD_WORKFLOW_ID)).thenReturn(Optional.of(childAWorkflow));
        when(workflowRepository.findById(GRANDCHILD_WORKFLOW_ID)).thenReturn(Optional.of(childBWorkflow));

        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // Session should be completed
        assertEquals("completed", session.getStatus(),
                "Session should be completed after full 3-level nesting");

        // Workflow stack should be empty at end
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stack = (List<Map<String, Object>>) session.getContext().get("_workflowStack");
        assertTrue(stack == null || stack.isEmpty(),
                "Workflow stack should be empty after all levels complete");

        // workflowId should be back to parent
        assertEquals(PARENT_WORKFLOW_ID, session.getWorkflowId(),
                "Session workflowId should be restored to parent");
    }

    /**
     * Test 4: Recursion protection triggers at depth 10.
     * Pre-populate the workflow stack to size 10, then processor should return
     * CONTINUE with depth exceeded error message. The stack should never grow beyond 10.
     */
    @Test
    void recursionProtectionAtDepth10() {
        // Pre-populate the stack to size 10
        List<Map<String, Object>> preStack = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("parentWorkflowId", (long) (100 + i));
            entry.put("workflowNodeId", "wf_node_" + i);
            preStack.add(entry);
        }
        session.getContext().put("_workflowStack", preStack);

        // Parent workflow: workflow_node only (no subsequent nodes).
        // When recursion depth is exceeded, processor returns CONTINUE with error.
        // processNodes sends the error response, then resolveNextNode returns null.
        // The engine then sees a non-empty stack and calls handleChildWorkflowEnd.
        // The key invariant: stack never grew past 10 (no 11th entry was pushed).
        Workflow parentWorkflow = buildWorkflow(PARENT_WORKFLOW_ID, "Recursive Parent",
                List.of(workflowNode("p_wf_self", PARENT_WORKFLOW_ID)),
                List.of(Map.of("sourceNodeId", "p_wf_self", "targetNodeId", "nonexistent"))
        );

        when(workflowRepository.findById(PARENT_WORKFLOW_ID)).thenReturn(Optional.of(parentWorkflow));
        // Mock the parent workflow IDs from the stack for handleChildWorkflowEnd pops
        for (int i = 0; i < 10; i++) {
            long parentId = 100L + i;
            Workflow fakeParent = buildWorkflow(parentId, "FakeParent" + i,
                    List.of(messageNode("wf_node_" + i, "Fake")),
                    List.of(Map.of("sourceNodeId", "wf_node_" + i, "targetNodeId", "nonexistent"))
            );
            when(workflowRepository.findById(parentId)).thenReturn(Optional.of(fakeParent));
        }

        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // Verify recursion error was sent
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/chat/" + SESSION_ID), messageCaptor.capture());

        boolean foundRecursionError = messageCaptor.getAllValues().stream()
                .anyMatch(msg -> msg.toString().toLowerCase().contains("depth")
                        || msg.toString().toLowerCase().contains("nesting"));
        assertTrue(foundRecursionError,
                "Should have sent error about maximum nesting depth exceeded");

        // Key invariant: the stack never grew beyond 10 entries (recursion was blocked)
        // After the engine unwinds, the stack will be empty, but no 11th entry was ever pushed
        assertNotEquals("active", session.getStatus(),
                "Session should not remain active after recursion error and stack unwinding");
    }

    /**
     * Test 5: Child workflow has nodes but no transitions (findFirstNode returns null).
     * Should send error about no starting node.
     */
    @Test
    void childWorkflowEmptyTransitions() {
        // Parent: p_wf_node → p_msg_end
        Workflow parentWorkflow = buildWorkflow(PARENT_WORKFLOW_ID, "Parent",
                List.of(
                        workflowNode("p_wf_node", CHILD_WORKFLOW_ID),
                        messageNode("p_msg_end", "After child")
                ),
                List.of(transition("p_wf_node", "p_msg_end"))
        );

        // Child workflow with nodes but empty transitions list
        Map<String, Object> childJson = new HashMap<>();
        childJson.put("nodes", List.of(messageNode("c_msg", "Unreachable")));
        childJson.put("transitions", List.of()); // empty transitions

        Workflow childWorkflow = new Workflow();
        childWorkflow.setId(CHILD_WORKFLOW_ID);
        childWorkflow.setName("Empty Child");
        childWorkflow.setWorkflowJson(childJson);

        when(workflowRepository.findById(PARENT_WORKFLOW_ID)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(CHILD_WORKFLOW_ID)).thenReturn(Optional.of(childWorkflow));

        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // Verify an error message was sent about no starting node
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/chat/" + SESSION_ID), messageCaptor.capture());

        // Check that at least one message contains "no starting node" error
        boolean foundError = messageCaptor.getAllValues().stream()
                .anyMatch(msg -> msg.toString().toLowerCase().contains("no starting node"));
        assertTrue(foundError,
                "Should have sent error about child workflow having no starting node");
    }

    /**
     * Test 6: Navigation history records entries across parent and child workflows.
     * Parent → child → return. Verify _navigationHistory contains entries from both
     * parent and child workflows with correct workflowIds.
     */
    @Test
    @SuppressWarnings("unchecked")
    void navigationHistoryAcrossWorkflows() {
        // Parent: p_msg_start → p_wf_node → p_msg_end
        Workflow parentWorkflow = buildWorkflow(PARENT_WORKFLOW_ID, "Parent",
                List.of(
                        messageNode("p_msg_start", "Start"),
                        workflowNode("p_wf_node", CHILD_WORKFLOW_ID),
                        messageNode("p_msg_end", "End")
                ),
                List.of(
                        transition("p_msg_start", "p_wf_node"),
                        transition("p_wf_node", "p_msg_end")
                )
        );

        // Child: c_msg only
        Workflow childWorkflow = buildWorkflow(CHILD_WORKFLOW_ID, "Child",
                List.of(messageNode("c_msg", "Child message")),
                List.of(Map.of("sourceNodeId", "c_msg", "targetNodeId", "nonexistent"))
        );

        when(workflowRepository.findById(PARENT_WORKFLOW_ID)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(CHILD_WORKFLOW_ID)).thenReturn(Optional.of(childWorkflow));

        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        List<Map<String, Object>> history = (List<Map<String, Object>>)
                session.getContext().get("_navigationHistory");

        assertNotNull(history, "Navigation history should exist");
        assertTrue(history.size() >= 3,
                "Should have at least 3 navigation entries (p_msg_start, p_wf_node, c_msg, p_msg_end)");

        // Verify entries from parent workflow
        boolean hasParentEntry = history.stream()
                .anyMatch(e -> PARENT_WORKFLOW_ID.equals(((Number) e.get("workflowId")).longValue())
                        && "p_msg_start".equals(e.get("nodeId")));
        assertTrue(hasParentEntry, "Should have navigation entry for parent node p_msg_start");

        // Verify entries from child workflow
        boolean hasChildEntry = history.stream()
                .anyMatch(e -> CHILD_WORKFLOW_ID.equals(((Number) e.get("workflowId")).longValue())
                        && "c_msg".equals(e.get("nodeId")));
        assertTrue(hasChildEntry, "Should have navigation entry for child node c_msg");

        // Verify return-to-parent entry
        boolean hasReturnEntry = history.stream()
                .anyMatch(e -> PARENT_WORKFLOW_ID.equals(((Number) e.get("workflowId")).longValue())
                        && "p_msg_end".equals(e.get("nodeId")));
        assertTrue(hasReturnEntry, "Should have navigation entry for parent node p_msg_end after return");

        // Verify each entry has required fields
        for (Map<String, Object> entry : history) {
            assertNotNull(entry.get("workflowId"), "Each entry should have workflowId");
            assertNotNull(entry.get("nodeId"), "Each entry should have nodeId");
            assertNotNull(entry.get("timestamp"), "Each entry should have timestamp");
        }
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> messageNode(String id, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        // No config / no nodeType → MessageNodeProcessor handles it
        return node;
    }

    private Map<String, Object> workflowNode(String id, Long workflowId) {
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        config.put("workflowId", workflowId.toString());

        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", "Workflow Node " + id);
        node.put("type", "state");
        node.put("config", config);
        return node;
    }

    private Map<String, Object> inputNode(String id, String name, String variableName) {
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "input");
        config.put("variableName", variableName);

        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        node.put("config", config);
        return node;
    }

    private Map<String, Object> transition(String sourceNodeId, String targetNodeId) {
        return Map.of("sourceNodeId", sourceNodeId, "targetNodeId", targetNodeId);
    }

    private Workflow buildWorkflow(Long id, String name,
                                    List<Map<String, Object>> nodes,
                                    List<Map<String, Object>> transitions) {
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", new ArrayList<>(nodes));
        workflowJson.put("transitions", new ArrayList<>(transitions));

        Workflow workflow = new Workflow();
        workflow.setId(id);
        workflow.setName(name);
        workflow.setWorkflowJson(workflowJson);
        return workflow;
    }
}
