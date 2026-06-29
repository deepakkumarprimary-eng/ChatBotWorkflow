package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult.Action;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.service.ConditionEvaluator;
import com.xpressbees.chatbot.service.PlaceholderService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Order(5)
public class DecisionNodeProcessor implements NodeProcessor {

    private final ConditionEvaluator conditionEvaluator;

    public DecisionNodeProcessor(ConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public String getNodeType() {
        return "decision";
    }

    @Override
    public boolean canHandle(Map<String, Object> node) {
        String type = (String) node.get("type");
        return "decision".equals(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public NodeProcessingResult process(Map<String, Object> node, ChatSession session,
                                         PlaceholderService placeholderService, Map<String, Object> workflowJson) {
        // 1. Extract the decision node's ID
        String nodeId = (String) node.get("id");

        // 2. Validate workflowJson parameter
        if (workflowJson == null) {
            return NodeProcessingResult.error("Workflow is no longer available");
        }

        // 3. Extract the transitions array from the workflow JSON
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");

        // 4. Filter transitions where sourceNodeId equals the decision node's ID, preserving array order
        List<Map<String, Object>> outgoingTransitions = (transitions != null)
                ? transitions.stream()
                    .filter(t -> nodeId != null && nodeId.equals(t.get("sourceNodeId")))
                    .collect(Collectors.toList())
                : List.of();

        // 5. If zero outgoing transitions, return error
        if (outgoingTransitions.isEmpty()) {
            return NodeProcessingResult.error("Decision node has no outgoing transitions");
        }

        // 6. Iterate filtered transitions in order
        for (Map<String, Object> transition : outgoingTransitions) {
            Object conditionObj = transition.get("condition");
            String condition = (conditionObj != null) ? String.valueOf(conditionObj) : null;

            // Skip transitions with null or empty condition
            if (condition == null || condition.trim().isEmpty()) {
                continue;
            }

            // Evaluate condition against session context
            if (conditionEvaluator.evaluate(condition, session.getContext())) {
                // First match: validate targetNodeId is not null/empty
                String targetNodeId = (String) transition.get("targetNodeId");
                if (targetNodeId == null || targetNodeId.trim().isEmpty()) {
                    return NodeProcessingResult.error("Matched transition has no target node");
                }

                // Store targetNodeId in session context and return CONTINUE
                session.getContext().put("_targetNodeId", targetNodeId);
                return new NodeProcessingResult(Action.CONTINUE, null);
            }
        }

        // 7. No condition matched — return error
        return NodeProcessingResult.error("No matching condition found for decision node");
    }
}
