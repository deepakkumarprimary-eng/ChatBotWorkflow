package com.xpressbees.chatbot.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.ExtractionResult;
import com.xpressbees.chatbot.dto.HttpExecutionResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult.Action;
import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ApiHeader;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.ApiConfigRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.ConditionEvaluator;
import com.xpressbees.chatbot.service.HttpExecutor;
import com.xpressbees.chatbot.service.PlaceholderService;
import com.xpressbees.chatbot.service.ResponseExtractor;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Order(3)
public class ApiNodeProcessor implements NodeProcessor {

    private final ApiConfigRepository apiConfigRepository;
    private final WorkflowRepository workflowRepository;
    private final HttpExecutor httpExecutor;
    private final ResponseExtractor responseExtractor;
    private final ConditionEvaluator conditionEvaluator;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiNodeProcessor(ApiConfigRepository apiConfigRepository,
                            WorkflowRepository workflowRepository,
                            HttpExecutor httpExecutor,
                            ResponseExtractor responseExtractor,
                            ConditionEvaluator conditionEvaluator,
                            SimpMessagingTemplate messagingTemplate) {
        this.apiConfigRepository = apiConfigRepository;
        this.workflowRepository = workflowRepository;
        this.httpExecutor = httpExecutor;
        this.responseExtractor = responseExtractor;
        this.conditionEvaluator = conditionEvaluator;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean canHandle(Map<String, Object> node) {
        String type = (String) node.get("type");
        if (!"state".equals(type)) {
            return false;
        }
        Map<String, Object> config = (Map<String, Object>) node.get("config");
        return config != null && "api".equals(config.get("nodeType"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public NodeProcessingResult process(Map<String, Object> node, ChatSession session,
                                         PlaceholderService placeholderService) {
        // --- Task 8.2: Config loading and request preparation ---

        // 1. Extract config map from node
        Map<String, Object> config = (Map<String, Object>) node.get("config");

        // 2. Get apiConfigId from config map
        Object apiConfigIdObj = (config != null) ? config.get("apiConfigId") : null;

        // 3. Handle missing config or apiConfigId
        if (config == null || apiConfigIdObj == null) {
            return new NodeProcessingResult(Action.CONTINUE,
                    new ChatResponse(node, "API configuration reference is missing from the node", session.getSessionId()));
        }

        // 4. Parse apiConfigId as Long
        Long id;
        try {
            id = Long.parseLong(String.valueOf(apiConfigIdObj));
        } catch (NumberFormatException e) {
            // 5. Handle unparseable apiConfigId
            return new NodeProcessingResult(Action.CONTINUE,
                    new ChatResponse(node, "API configuration identifier is invalid", session.getSessionId()));
        }

        // 6. Load ApiConfig from repository
        Optional<ApiConfig> apiConfigOpt = apiConfigRepository.findById(id);
        if (apiConfigOpt.isEmpty()) {
            // 7. Handle not-found case
            return new NodeProcessingResult(Action.CONTINUE,
                    new ChatResponse(node, "No API configuration found for ID: " + id, session.getSessionId()));
        }

        ApiConfig apiConfig = apiConfigOpt.get();

        // 8. Resolve URL using PlaceholderService
        String resolvedUrl = placeholderService.resolve(apiConfig.getUrl(), session.getContext());

        // 9. Resolve headers: iterate and resolve each header value
        Map<String, String> resolvedHeaders = new HashMap<>();
        if (apiConfig.getHeaders() != null) {
            for (ApiHeader header : apiConfig.getHeaders()) {
                String resolvedValue = placeholderService.resolve(header.getHeaderValue(), session.getContext());
                resolvedHeaders.put(header.getHeaderName(), resolvedValue);
            }
        }

        // 10. Resolve payload if present
        String resolvedBody = null;
        if (apiConfig.getPayload() != null && apiConfig.getPayload().getPayloadTemplate() != null) {
            Map<String, Object> resolvedPayloadMap = placeholderService.resolvePayload(
                    apiConfig.getPayload().getPayloadTemplate(), session.getContext());
            try {
                resolvedBody = objectMapper.writeValueAsString(resolvedPayloadMap);
            } catch (JsonProcessingException e) {
                return new NodeProcessingResult(Action.CONTINUE,
                        new ChatResponse(node, "Failed to serialize request payload", session.getSessionId()));
            }
        }

        // --- Task 8.3: HTTP execution and response extraction ---

        // 11. Execute HTTP call
        HttpExecutionResult httpResult = httpExecutor.execute(apiConfig, resolvedUrl, resolvedHeaders, resolvedBody);

        // 12. Handle HTTP failure
        if (!httpResult.isSuccess()) {
            String errorMsg;
            if (httpResult.getStatusCode() == 0) {
                errorMsg = "External API is unreachable (timeout)";
            } else {
                errorMsg = "External API call failed with status: " + httpResult.getStatusCode();
            }
            messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(),
                    new ChatErrorResponse(errorMsg, session.getSessionId()));
            return new NodeProcessingResult(Action.PAUSE, null);
        }

        // 13. Extract response values
        if (apiConfig.getResponseMappings() != null && !apiConfig.getResponseMappings().isEmpty()) {
            ExtractionResult extractionResult = responseExtractor.extract(
                    httpResult.getResponseBody(), apiConfig.getResponseMappings());

            if (!extractionResult.isSuccess()) {
                messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(),
                        new ChatErrorResponse(extractionResult.getErrorMessage(), session.getSessionId()));
                return new NodeProcessingResult(Action.PAUSE, null);
            }

            // 14. Store extracted values in session context
            if (extractionResult.getExtractedValues() != null) {
                session.getContext().putAll(extractionResult.getExtractedValues());
            }
        }

        // --- Task 8.4: Behavior inference and routing ---

        String nodeId = (String) node.get("id");
        Object displayVariable = node.get("displayVariable");

        // Load workflow to get transitions
        List<Map<String, Object>> outgoingTransitions = getOutgoingTransitions(nodeId, session.getWorkflowId());

        // Type 3: Interactive array selection (node has displayVariable)
        if (displayVariable != null && !String.valueOf(displayVariable).trim().isEmpty()) {
            String displayVarName = String.valueOf(displayVariable);
            String arrayValues = (String) session.getContext().get(displayVarName);

            if (arrayValues == null || arrayValues.trim().isEmpty()) {
                // No options available - send error and advance
                messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(),
                        new ChatErrorResponse("No options available", session.getSessionId()));
                return new NodeProcessingResult(Action.CONTINUE, null);
            }

            // Store displayVariable in context for resume handler
            session.getContext().put("_displayVariable", displayVarName);

            // Set session state for pause
            session.setCurrentNodeId(nodeId);
            session.setCurrentNodeType("api");

            // Send array values as message and PAUSE
            ChatResponse response = new ChatResponse(node, arrayValues, session.getSessionId());
            return new NodeProcessingResult(Action.PAUSE, response);
        }

        // Determine transition-based behavior
        if (outgoingTransitions.isEmpty()) {
            // No transitions - auto-advance (end of workflow will be handled by engine)
            return new NodeProcessingResult(Action.CONTINUE, null);
        }

        // Check if transitions have conditions (Its a dead code , commented out , this case is handled by decisin node now)
       /*  boolean hasConditions = outgoingTransitions.stream()
                .anyMatch(t -> t.get("condition") != null && !String.valueOf(t.get("condition")).trim().isEmpty());

        if (hasConditions && outgoingTransitions.size() > 1) {
            // Type 2: Conditional branching - evaluate conditions in order (first-match-wins)
            for (Map<String, Object> transition : outgoingTransitions) {
                Object conditionObj = transition.get("condition");
                if (conditionObj == null || String.valueOf(conditionObj).trim().isEmpty()) {
                    continue;
                }
                String condition = String.valueOf(conditionObj);
                if (conditionEvaluator.evaluate(condition, session.getContext())) {
                    // First match wins - store target for the engine to use
                    String targetNodeId = (String) transition.get("targetNodeId");
                    session.getContext().put("_targetNodeId", targetNodeId);
                    return new NodeProcessingResult(Action.CONTINUE, null);
                }
            }

            // No condition matched - error
            messagingTemplate.convertAndSend("/topic/chat/" + session.getSessionId(),
                    new ChatErrorResponse("No matching transition found for current context", session.getSessionId()));
            return new NodeProcessingResult(Action.PAUSE, null);
        }
*/
        if (outgoingTransitions.size() == 1) {
            // Type 1: Auto-advance (single transition without condition)
            return new NodeProcessingResult(Action.CONTINUE, null);
        }

        // Button node: multiple transitions without conditions
        // Collect target node names as button options
        List<String> buttonOptions = getTargetNodeNames(outgoingTransitions, session.getWorkflowId());

        if (buttonOptions.isEmpty()) {
            return new NodeProcessingResult(Action.CONTINUE, null);
        }

        // Store button options in context for resume handler
        String optionsString = String.join("\n", buttonOptions);
        session.getContext().put("_buttonOptions", optionsString);

        // Set session state for pause
        session.setCurrentNodeId(nodeId);
        session.setCurrentNodeType("api");

        // Send button options as message and PAUSE
        ChatResponse buttonResponse = new ChatResponse(node, optionsString, session.getSessionId());
        return new NodeProcessingResult(Action.PAUSE, buttonResponse);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getOutgoingTransitions(String nodeId, Long workflowId) {
        if (nodeId == null || workflowId == null) {
            return Collections.emptyList();
        }

        Optional<Workflow> workflowOpt = workflowRepository.findById(workflowId);
        if (workflowOpt.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();
        if (workflowJson == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");
        if (transitions == null) {
            return Collections.emptyList();
        }

        return transitions.stream()
                .filter(t -> nodeId.equals(t.get("sourceNodeId")))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<String> getTargetNodeNames(List<Map<String, Object>> outgoingTransitions, Long workflowId) {
        Optional<Workflow> workflowOpt = workflowRepository.findById(workflowId);
        if (workflowOpt.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();
        if (workflowJson == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");
        if (nodes == null) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<>();
        for (Map<String, Object> transition : outgoingTransitions) {
            String targetNodeId = (String) transition.get("targetNodeId");
            for (Map<String, Object> n : nodes) {
                if (targetNodeId != null && targetNodeId.equals(n.get("id"))) {
                    Object name = n.get("name");
                    if (name != null) {
                        names.add(String.valueOf(name));
                    }
                    break;
                }
            }
        }
        return names;
    }
}
