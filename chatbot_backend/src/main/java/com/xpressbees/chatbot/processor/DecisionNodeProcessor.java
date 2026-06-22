package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult.Action;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.ConditionEvaluator;
import com.xpressbees.chatbot.service.PlaceholderService;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Order(5)
public class DecisionNodeProcessor implements NodeProcessor {

    private final WorkflowRepository workflowRepository;
    private final ConditionEvaluator conditionEvaluator;
    private final SimpMessagingTemplate messagingTemplate;

    public DecisionNodeProcessor(WorkflowRepository workflowRepository,
                                  ConditionEvaluator conditionEvaluator,
                                  SimpMessagingTemplate messagingTemplate) {
        this.workflowRepository = workflowRepository;
        this.conditionEvaluator = conditionEvaluator;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public boolean canHandle(Map<String, Object> node) {
        String type = (String) node.get("type");
        return "decision".equals(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public NodeProcessingResult process(Map<String, Object> node, ChatSession session,
                                         PlaceholderService placeholderService) {
        // 1. Extract the decision node's ID
        String nodeId = (String) node.get("id");

        // 2. Load workflow from WorkflowRepository
        Optional<Workflow> workflowOpt = workflowRepository.findById(session.getWorkflowId());

        // 3. If workflow not found or workflowJson is null, send error and return PAUSE
        if (workflowOpt.isEmpty() || workflowOpt.get().getWorkflowJson() == null) {
            messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(),
                    new ChatErrorResponse("Workflow is no longer available", session.getSessionId()));
            return new NodeProcessingResult(Action.PAUSE, null);
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();

        // 4. Extract the transitions array from the workflow JSON
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");

        // 5. Filter transitions where sourceNodeId equals the decision node's ID, preserving array order
        List<Map<String, Object>> outgoingTransitions = (transitions != null)
                ? transitions.stream()
                    .filter(t -> nodeId != null && nodeId.equals(t.get("sourceNodeId")))
                    .collect(Collectors.toList())
                : List.of();

        // 6. If zero outgoing transitions, send error and return PAUSE
        if (outgoingTransitions.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(),
                    new ChatErrorResponse("Decision node has no outgoing transitions", session.getSessionId()));
            return new NodeProcessingResult(Action.PAUSE, null);
        }

        // 7. Iterate filtered transitions in order
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
                    messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(),
                            new ChatErrorResponse("Matched transition has no target node", session.getSessionId()));
                    return new NodeProcessingResult(Action.PAUSE, null);
                }

                // Store targetNodeId in session context and return CONTINUE
                session.getContext().put("_targetNodeId", targetNodeId);
                return new NodeProcessingResult(Action.CONTINUE, null);
            }
        }

        // 8. No condition matched — send error and return PAUSE
        messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(),
                new ChatErrorResponse("No matching condition found for decision node", session.getSessionId()));
        return new NodeProcessingResult(Action.PAUSE, null);
    }
}
