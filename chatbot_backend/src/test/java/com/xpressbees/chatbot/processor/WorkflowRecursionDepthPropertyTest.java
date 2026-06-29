package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.PlaceholderService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Property 7: Recursion depth enforcement with state preservation
 *
 * When the workflow stack size is >= 10 (MAX_RECURSION_DEPTH), the processor
 * must return CONTINUE with an error message about depth/nesting exceeded,
 * the stack must remain unchanged, and the session must not be marked completed.
 *
 * **Validates: Requirements 5.2, 5.4**
 */
class WorkflowRecursionDepthPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 7: Recursion depth enforcement with state preservation")
    void stackAtOrAboveMaxDepthReturnsContinueWithError(
            @ForAll @IntRange(min = 10, max = 20) int stackSize) {

        // Setup: mock WorkflowRepository to return a valid workflow for ID 1
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setName("Child Workflow");
        when(workflowRepository.findById(1L)).thenReturn(Optional.of(workflow));

        WorkflowNodeProcessor processor = new WorkflowNodeProcessor(workflowRepository);

        // Create a workflow stack of the given size
        List<Map<String, Object>> workflowStack = new ArrayList<>();
        for (int i = 0; i < stackSize; i++) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("parentWorkflowId", (long) (i + 1));
            entry.put("workflowNodeId", "node-" + i);
            workflowStack.add(entry);
        }

        // Create session with context containing the stack
        ChatSession session = new ChatSession();
        session.setSessionId("sess-recursion-test");
        session.setStatus("active");
        Map<String, Object> context = new HashMap<>();
        context.put("_workflowStack", workflowStack);
        session.setContext(context);

        // Create a node with config.nodeType="workflow", config.workflowId="1"
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        config.put("workflowId", "1");

        Map<String, Object> node = new HashMap<>();
        node.put("type", "state");
        node.put("id", "workflow-node-1");
        node.put("config", config);

        // Record stack size before processing
        int originalStackSize = workflowStack.size();

        // Process the node
        PlaceholderService placeholderService = new PlaceholderService();
        NodeProcessingResult result = processor.process(node, session, placeholderService, null);
        assert result.getAction() == NodeProcessingResult.Action.CONTINUE :
                "Expected CONTINUE when recursion depth exceeded, got: " + result.getAction();

        // Assert: response message contains "depth" or "exceeded" or "nesting"
        String message = result.getResponse().getResponse().toLowerCase();
        assert message.contains("depth") || message.contains("exceeded") || message.contains("nesting") :
                "Error message should mention depth, exceeded, or nesting. Got: " + message;

        // Assert: stack size is UNCHANGED (no modification)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stackAfter = (List<Map<String, Object>>) session.getContext().get("_workflowStack");
        assert stackAfter.size() == originalStackSize :
                "Stack size should remain " + originalStackSize + " but was " + stackAfter.size();

        // Assert: session status is NOT "completed"
        assert !"completed".equals(session.getStatus()) :
                "Session should not be marked as completed when recursion depth is exceeded";
    }
}
