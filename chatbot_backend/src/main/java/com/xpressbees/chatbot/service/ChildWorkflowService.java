package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChildWorkflowResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.util.WorkflowJsonUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Manages child workflow entry and exit.
 * Returns ChildWorkflowResult — never throws for expected outcomes.
 */
@Service
public class ChildWorkflowService {

    private final WorkflowCacheService workflowCacheService;

    public ChildWorkflowService(WorkflowCacheService workflowCacheService) {
        this.workflowCacheService = workflowCacheService;
    }

    /**
     * Pushes parent workflow onto stack, switches session to child workflow,
     * clears transient context keys, and returns the child's first node.
     *
     * @param session         the current chat session
     * @param childWorkflowId the ID of the child workflow to enter
     * @param workflowNode    the workflow node in the parent that triggered the child entry
     * @return ChildWorkflowResult with first node + child workflowJson, or error
     */
    @SuppressWarnings("unchecked")
    public ChildWorkflowResult enterChild(ChatSession session, Long childWorkflowId,
                                           Map<String, Object> workflowNode) {
        // 1. Get/create context and _workflowStack
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
            session.setContext(context);
        }

        // 2. Push stack entry with parent workflow info
        List<Map<String, Object>> stack = getWorkflowStack(context);
        Map<String, Object> stackEntry = new HashMap<>();
        stackEntry.put("parentWorkflowId", session.getWorkflowId());
        stackEntry.put("workflowNodeId", workflowNode.get("id"));
        stack.add(stackEntry);

        // 3. Clear transient keys
        clearTransientKeys(context);

        // 4. Switch session to child workflow
        session.setWorkflowId(childWorkflowId);

        // 5. Load child workflow from cache/DB
        Optional<Workflow> childWorkflowOpt = workflowCacheService.findById(childWorkflowId);

        // 6. If not found, return error
        if (childWorkflowOpt.isEmpty()) {
            return ChildWorkflowResult.error("Child workflow not found: " + childWorkflowId);
        }

        // 7. Find first node in child workflow
        Map<String, Object> childWorkflowJson = childWorkflowOpt.get().getWorkflowJson();
        Map<String, Object> firstNode = WorkflowJsonUtils.findFirstNode(childWorkflowJson);

        // 8. If no first node, return error
        if (firstNode == null) {
            return ChildWorkflowResult.error("Child workflow has no starting node");
        }

        // 9. Return first node and child workflow JSON
        return ChildWorkflowResult.nextNode(firstNode, childWorkflowJson);
    }

    /**
     * Pops the workflow stack, restores parent workflow ID, resolves next node
     * in parent after the workflow node. Recursively unwinds if parent also has
     * no next node.
     *
     * @param session the current chat session
     * @return ChildWorkflowResult with next node + parent workflowJson,
     *         or COMPLETE signal, or error
     */
    @SuppressWarnings("unchecked")
    public ChildWorkflowResult handleChildEnd(ChatSession session) {
        // 1. Get context and _workflowStack
        Map<String, Object> context = session.getContext();

        // 2. If context null or stack empty, return complete
        if (context == null) {
            return ChildWorkflowResult.complete();
        }

        List<Map<String, Object>> stack = getWorkflowStack(context);
        if (stack.isEmpty()) {
            return ChildWorkflowResult.complete();
        }

        // 3. Pop last entry from stack
        Map<String, Object> entry = stack.remove(stack.size() - 1);

        // 4. Extract parentWorkflowId and workflowNodeId
        Long parentWorkflowId = ((Number) entry.get("parentWorkflowId")).longValue();
        String workflowNodeId = (String) entry.get("workflowNodeId");

        // 5. Restore parent workflow ID on session
        session.setWorkflowId(parentWorkflowId);

        // 6. Load parent workflow from cache/DB
        Optional<Workflow> parentWorkflowOpt = workflowCacheService.findById(parentWorkflowId);

        // 7. If not found, return error
        if (parentWorkflowOpt.isEmpty()) {
            return ChildWorkflowResult.error("Parent workflow not found: " + parentWorkflowId);
        }

        // 8. Resolve next node after workflowNodeId in parent
        Map<String, Object> parentWorkflowJson = parentWorkflowOpt.get().getWorkflowJson();
        Map<String, Object> nextNode = WorkflowJsonUtils.resolveNextNode(workflowNodeId, parentWorkflowJson);

        // 9. If next node exists, return it
        if (nextNode != null) {
            return ChildWorkflowResult.nextNode(nextNode, parentWorkflowJson);
        }

        // 10. If no next node and stack still has entries, recursively unwind
        if (!stack.isEmpty()) {
            return handleChildEnd(session);
        }

        // 11. If no next node and stack empty, return complete
        return ChildWorkflowResult.complete();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getWorkflowStack(Map<String, Object> context) {
        List<Map<String, Object>> stack = (List<Map<String, Object>>) context.get("_workflowStack");
        if (stack == null) {
            stack = new ArrayList<>();
            context.put("_workflowStack", stack);
        }
        return stack;
    }

    private void clearTransientKeys(Map<String, Object> context) {
        context.remove("_targetNodeId");
        context.remove("_inputVariableName");
        context.remove("_displayVariable");
        context.remove("_buttonOptions");
    }
}
