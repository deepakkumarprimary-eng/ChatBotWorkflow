package com.xpressbees.chatbot.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.ExtractionResult;
import com.xpressbees.chatbot.dto.HttpExecutionResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult.Action;
import com.xpressbees.chatbot.dto.UrlValidationResult;
import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ApiHeader;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.service.ApiConfigCacheService;
import com.xpressbees.chatbot.service.HttpExecutor;
import com.xpressbees.chatbot.service.PlaceholderService;
import com.xpressbees.chatbot.service.ResponseExtractor;
import com.xpressbees.chatbot.service.UrlValidator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Order(3)
public class ApiNodeProcessor implements NodeProcessor {

    private final ApiConfigCacheService apiConfigCacheService;
    private final HttpExecutor httpExecutor;
    private final ResponseExtractor responseExtractor;
    private final UrlValidator urlValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiNodeProcessor(ApiConfigCacheService apiConfigCacheService,
                            HttpExecutor httpExecutor,
                            ResponseExtractor responseExtractor,
                            UrlValidator urlValidator) {
        this.apiConfigCacheService = apiConfigCacheService;
        this.httpExecutor = httpExecutor;
        this.responseExtractor = responseExtractor;
        this.urlValidator = urlValidator;
    }

    @Override
    public String getNodeType() {
        return "api";
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
                                         PlaceholderService placeholderService, Map<String, Object> workflowJson) {
        // --- Config loading and request preparation ---

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

        // 6. Load ApiConfig from cache service (Redis-backed with PostgreSQL fallback)
        Optional<ApiConfig> apiConfigOpt = apiConfigCacheService.findById(id);
        if (apiConfigOpt.isEmpty()) {
            // 7. Handle not-found case — return ERROR result
            return NodeProcessingResult.error("No API configuration found for ID: " + id);
        }

        ApiConfig apiConfig = apiConfigOpt.get();

        // 8. Resolve URL using PlaceholderService
        String resolvedUrl = placeholderService.resolve(apiConfig.getUrl(), session.getContext());

        // 8a. Validate resolved URL for SSRF protection
        UrlValidationResult urlValidation = urlValidator.validate(resolvedUrl);
        if (!urlValidation.isAllowed()) {
            return NodeProcessingResult.error(
                    "SSRF protection: URL blocked by security policy - " + urlValidation.reason());
        }

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

        // --- HTTP execution and response extraction ---

        // 11. Execute HTTP call
        HttpExecutionResult httpResult = httpExecutor.execute(apiConfig, resolvedUrl, resolvedHeaders, resolvedBody);

        // 12. Handle HTTP failure — return ERROR result
        if (!httpResult.isSuccess()) {
            String errorMsg;
            if (httpResult.getStatusCode() == 0) {
                errorMsg = "External API is unreachable (timeout)";
            } else {
                errorMsg = "External API call failed with status: " + httpResult.getStatusCode();
            }
            return NodeProcessingResult.error(errorMsg);
        }

        // 13. Extract response values
        if (apiConfig.getResponseMappings() != null && !apiConfig.getResponseMappings().isEmpty()) {
            ExtractionResult extractionResult = responseExtractor.extract(
                    httpResult.getResponseBody(), apiConfig.getResponseMappings());

            if (!extractionResult.isSuccess()) {
                // Extraction failure — return ERROR result
                return NodeProcessingResult.error(extractionResult.getErrorMessage());
            }

            // 14. Store extracted values in session context
            if (extractionResult.getExtractedValues() != null) {
                session.getContext().putAll(extractionResult.getExtractedValues());
            }
        }

        // --- Behavior inference and routing ---

        String nodeId = (String) node.get("id");
        Object displayVariable = node.get("displayVariable");

        // Use workflowJson parameter to get transitions (no DB lookup)
        List<Map<String, Object>> outgoingTransitions = getOutgoingTransitions(nodeId, workflowJson);

        // Type 3: Interactive array selection (node has displayVariable)
        if (displayVariable != null && !String.valueOf(displayVariable).trim().isEmpty()) {
            String displayVarName = String.valueOf(displayVariable);
            String arrayValues = (String) session.getContext().get(displayVarName);

            if (arrayValues == null || arrayValues.trim().isEmpty()) {
                // No options available — return ERROR result
                return NodeProcessingResult.error("No options available");
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

        if (outgoingTransitions.size() == 1) {
            // Type 1: Auto-advance (single transition without condition)
            return new NodeProcessingResult(Action.CONTINUE, null);
        }

        // Button node: multiple transitions without conditions
        // Collect target node names as button options
        List<String> buttonOptions = getTargetNodeNames(outgoingTransitions, workflowJson);

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
    private List<Map<String, Object>> getOutgoingTransitions(String nodeId, Map<String, Object> workflowJson) {
        if (nodeId == null || workflowJson == null) {
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
    private List<String> getTargetNodeNames(List<Map<String, Object>> outgoingTransitions, Map<String, Object> workflowJson) {
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
