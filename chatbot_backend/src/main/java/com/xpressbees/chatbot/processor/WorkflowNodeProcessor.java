package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.PlaceholderService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Order(4)
public class WorkflowNodeProcessor implements NodeProcessor {

    private static final int MAX_RECURSION_DEPTH = 10;
    private final WorkflowRepository workflowRepository;

    public WorkflowNodeProcessor(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @Override
    public boolean canHandle(Map<String, Object> node) {
        String type = (String) node.get("type");
        if (!"state".equals(type)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) node.get("config");
        return config != null && "workflow".equals(config.get("nodeType"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public NodeProcessingResult process(Map<String, Object> node, ChatSession session,
                                         PlaceholderService placeholderService) {
        // 1. Extract config map
        Map<String, Object> config = (Map<String, Object>) node.get("config");
        if (config == null) {
            ChatResponse errorResponse = new ChatResponse(node,
                    "Workflow reference is missing from the node", session.getSessionId());
            return new NodeProcessingResult(NodeProcessingResult.Action.CONTINUE, errorResponse);
        }

        // 2. Extract workflowId from config
        Object workflowIdObj = config.get("workflowId");
        if (workflowIdObj == null) {
            ChatResponse errorResponse = new ChatResponse(node,
                    "Workflow reference is missing from the node", session.getSessionId());
            return new NodeProcessingResult(NodeProcessingResult.Action.CONTINUE, errorResponse);
        }

        // 3. Parse workflowId as Long
        Long childWorkflowId;
        try {
            childWorkflowId = Long.parseLong(workflowIdObj.toString());
        } catch (NumberFormatException e) {
            ChatResponse errorResponse = new ChatResponse(node,
                    "Workflow identifier is invalid", session.getSessionId());
            return new NodeProcessingResult(NodeProcessingResult.Action.CONTINUE, errorResponse);
        }

        // 4. Check existence in DB
        if (!workflowRepository.findById(childWorkflowId).isPresent()) {
            ChatResponse errorResponse = new ChatResponse(node,
                    "No workflow found for ID: " + childWorkflowId, session.getSessionId());
            return new NodeProcessingResult(NodeProcessingResult.Action.CONTINUE, errorResponse);
        }

        // 5. Check recursion depth via _workflowStack size
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new java.util.HashMap<>();
            session.setContext(context);
        }

        List<Map<String, Object>> workflowStack = (List<Map<String, Object>>) context.get("_workflowStack");
        if (workflowStack == null) {
            workflowStack = new ArrayList<>();
            context.put("_workflowStack", workflowStack);
        }

        if (workflowStack.size() >= MAX_RECURSION_DEPTH) {
            ChatResponse errorResponse = new ChatResponse(node,
                    "Maximum workflow nesting depth (10) exceeded", session.getSessionId());
            return new NodeProcessingResult(NodeProcessingResult.Action.CONTINUE, errorResponse);
        }

        // 6. Store child workflow ID in context and return ENTER_CHILD
        context.put("_childWorkflowId", childWorkflowId);
        return new NodeProcessingResult(NodeProcessingResult.Action.ENTER_CHILD, null);
    }
}
