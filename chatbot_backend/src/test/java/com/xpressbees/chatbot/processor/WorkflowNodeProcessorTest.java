package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.PlaceholderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkflowNodeProcessor.
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 5.2, 6.2, 6.3
 */
@ExtendWith(MockitoExtension.class)
class WorkflowNodeProcessorTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private PlaceholderService placeholderService;

    private WorkflowNodeProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WorkflowNodeProcessor(workflowRepository);
    }

    // --- Helper methods ---

    private ChatSession createSession(String sessionId) {
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setContext(new HashMap<>());
        return session;
    }

    private Map<String, Object> createWorkflowNode(String nodeId, String workflowId) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", nodeId);
        node.put("type", "state");

        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        if (workflowId != null) {
            config.put("workflowId", workflowId);
        }
        node.put("config", config);

        return node;
    }

    // --- canHandle tests ---

    @Test
    @DisplayName("canHandle returns true for node with type='state' and config.nodeType='workflow'")
    void canHandle_returnsTrueForWorkflowNodeType() {
        Map<String, Object> node = createWorkflowNode("node-1", "42");

        assertTrue(processor.canHandle(node));
    }

    @Test
    @DisplayName("canHandle returns false for node with type='state' and config.nodeType='input'")
    void canHandle_returnsFalseForOtherNodeTypes() {
        Map<String, Object> node = new HashMap<>();
        node.put("id", "node-2");
        node.put("type", "state");

        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "input");
        node.put("config", config);

        assertFalse(processor.canHandle(node));
    }

    @Test
    @DisplayName("canHandle returns false for node with type='transition'")
    void canHandle_returnsFalseForNonStateType() {
        Map<String, Object> node = new HashMap<>();
        node.put("id", "node-3");
        node.put("type", "transition");

        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        node.put("config", config);

        assertFalse(processor.canHandle(node));
    }

    @Test
    @DisplayName("canHandle returns false when config is null")
    void canHandle_returnsFalseForNullConfig() {
        Map<String, Object> node = new HashMap<>();
        node.put("id", "node-4");
        node.put("type", "state");
        node.put("config", null);

        assertFalse(processor.canHandle(node));
    }

    // --- process tests ---

    @Test
    @DisplayName("process returns CONTINUE with error when config is null")
    void process_missingConfigReturnsError() {
        Map<String, Object> node = new HashMap<>();
        node.put("id", "node-5");
        node.put("type", "state");
        node.put("config", null);

        ChatSession session = createSession("session-1");

        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        assertEquals(NodeProcessingResult.Action.CONTINUE, result.getAction());
        assertNotNull(result.getResponse());
        assertTrue(result.getResponse().getResponse().toLowerCase().contains("missing"));
    }

    @Test
    @DisplayName("process returns CONTINUE with error when workflowId is null")
    void process_nullWorkflowIdReturnsError() {
        Map<String, Object> node = createWorkflowNode("node-6", null);
        ChatSession session = createSession("session-2");

        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        assertEquals(NodeProcessingResult.Action.CONTINUE, result.getAction());
        assertNotNull(result.getResponse());
        assertTrue(result.getResponse().getResponse().toLowerCase().contains("missing"));
    }

    @Test
    @DisplayName("process returns CONTINUE with error for non-numeric workflowId")
    void process_nonNumericWorkflowIdReturnsError() {
        Map<String, Object> node = createWorkflowNode("node-7", "abc");
        ChatSession session = createSession("session-3");

        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        assertEquals(NodeProcessingResult.Action.CONTINUE, result.getAction());
        assertNotNull(result.getResponse());
        assertTrue(result.getResponse().getResponse().toLowerCase().contains("invalid"));
    }

    @Test
    @DisplayName("process returns CONTINUE with error when workflow not found in DB")
    void process_workflowNotFoundReturnsError() {
        Map<String, Object> node = createWorkflowNode("node-8", "999");
        ChatSession session = createSession("session-4");

        when(workflowRepository.findById(999L)).thenReturn(Optional.empty());

        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        assertEquals(NodeProcessingResult.Action.CONTINUE, result.getAction());
        assertNotNull(result.getResponse());
        assertTrue(result.getResponse().getResponse().contains("999"));
        verify(workflowRepository).findById(999L);
    }

    @Test
    @DisplayName("process returns ENTER_CHILD with _childWorkflowId in context for valid workflowId")
    void process_validWorkflowIdReturnsEnterChild() {
        Map<String, Object> node = createWorkflowNode("node-9", "42");
        ChatSession session = createSession("session-5");

        Workflow workflow = new Workflow();
        workflow.setId(42L);
        workflow.setName("Child Workflow");
        when(workflowRepository.findById(42L)).thenReturn(Optional.of(workflow));

        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        assertEquals(NodeProcessingResult.Action.ENTER_CHILD, result.getAction());
        assertNull(result.getResponse());
        assertEquals(42L, session.getContext().get("_childWorkflowId"));
        verify(workflowRepository).findById(42L);
    }

    @Test
    @DisplayName("process returns CONTINUE with error when recursion depth is exceeded (stack size >= 10)")
    void process_recursionDepthExceededReturnsError() {
        Map<String, Object> node = createWorkflowNode("node-10", "50");
        ChatSession session = createSession("session-6");

        // Build a workflow stack of size 10
        List<Map<String, Object>> workflowStack = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("parentWorkflowId", (long) i);
            entry.put("workflowNodeId", "wf-node-" + i);
            workflowStack.add(entry);
        }
        session.getContext().put("_workflowStack", workflowStack);

        Workflow workflow = new Workflow();
        workflow.setId(50L);
        workflow.setName("Deep Child");
        when(workflowRepository.findById(50L)).thenReturn(Optional.of(workflow));

        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        assertEquals(NodeProcessingResult.Action.CONTINUE, result.getAction());
        assertNotNull(result.getResponse());
        assertTrue(result.getResponse().getResponse().contains("depth"));
        // Stack should remain unchanged
        assertEquals(10, workflowStack.size());
    }
}
