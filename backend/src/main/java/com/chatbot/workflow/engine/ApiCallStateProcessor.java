package com.chatbot.workflow.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.service.ContextVariableService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Processor for API_Call states. Makes HTTP requests with configured method, URL,
 * headers, and body. Supports template variable interpolation using {{variableName}} syntax.
 * Handles timeouts, non-2xx responses, and network errors.
 */
@Component
public class ApiCallStateProcessor implements StateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApiCallStateProcessor.class);
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 120;

    private final ContextVariableService contextVariableService;
    private final ObjectMapper objectMapper;
    private final RestTemplateFactory restTemplateFactory;

    @org.springframework.beans.factory.annotation.Autowired
    public ApiCallStateProcessor(ContextVariableService contextVariableService) {
        this(contextVariableService, new ObjectMapper(), new DefaultRestTemplateFactory());
    }

    public ApiCallStateProcessor(ContextVariableService contextVariableService,
                                  ObjectMapper objectMapper,
                                  RestTemplateFactory restTemplateFactory) {
        this.contextVariableService = contextVariableService;
        this.objectMapper = objectMapper;
        this.restTemplateFactory = restTemplateFactory;
    }

    @Override
    public StateType getType() {
        return StateType.API_CALL;
    }

    @Override
    public StateProcessorResult process(StateDefinition state, ExecutionContext context) {
        Map<String, Object> config = state.getConfig();
        if (config == null) {
            return StateProcessorResult.failure("API_Call state has no configuration");
        }

        try {
            // Extract config
            String method = getStringConfig(config, "method", "GET");
            String url = getStringConfig(config, "url", "");
            String body = getStringConfig(config, "body", null);
            int timeoutSeconds = getTimeoutFromConfig(config);

            // Interpolate templates
            String interpolatedUrl = interpolateTemplate(url, context);
            String interpolatedBody = body != null ? interpolateTemplate(body, context) : null;
            HttpHeaders headers = buildHeaders(config, context);

            // Build RestTemplate with timeout
            RestTemplate restTemplate = restTemplateFactory.create(timeoutSeconds);

            // Execute request
            HttpMethod httpMethod = resolveHttpMethod(method);
            HttpEntity<String> requestEntity = new HttpEntity<>(interpolatedBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    interpolatedUrl, httpMethod, requestEntity, String.class);

            // Handle response
            return handleSuccessResponse(response, config, context, state);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // Non-2xx responses
            return handleHttpError(e.getRawStatusCode(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            // Timeout or network errors
            logger.warn("API call timeout/network error for state '{}': {}",
                    state.getId(), e.getMessage());
            return buildTimeoutResult(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in API_Call state '{}': {}",
                    state.getId(), e.getMessage(), e);
            return buildTimeoutResult(e.getMessage());
        }
    }

    /**
     * Interpolates all {{variableName}} references in the given template string
     * with values from the execution context. Undefined variables are replaced with empty string.
     */
    String interpolateTemplate(String template, ExecutionContext context) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = context.getVariable(variableName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private int getTimeoutFromConfig(Map<String, Object> config) {
        Object timeoutObj = config.get("timeout");
        int timeout = DEFAULT_TIMEOUT_SECONDS;

        if (timeoutObj instanceof Number) {
            timeout = ((Number) timeoutObj).intValue();
        }

        // Clamp to [1, 120]
        return Math.max(MIN_TIMEOUT_SECONDS, Math.min(MAX_TIMEOUT_SECONDS, timeout));
    }

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders buildHeaders(Map<String, Object> config, ExecutionContext context) {
        HttpHeaders headers = new HttpHeaders();
        Object headersObj = config.get("headers");

        if (headersObj instanceof Map) {
            Map<String, Object> headersMap = (Map<String, Object>) headersObj;
            for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                String headerValue = entry.getValue() != null ? entry.getValue().toString() : "";
                String interpolatedValue = interpolateTemplate(headerValue, context);
                headers.add(entry.getKey(), interpolatedValue);
            }
        }

        return headers;
    }

    private HttpMethod resolveHttpMethod(String method) {
        if (method == null || method.isEmpty()) {
            return HttpMethod.GET;
        }
        switch (method.toUpperCase()) {
            case "POST": return HttpMethod.POST;
            case "PUT": return HttpMethod.PUT;
            case "PATCH": return HttpMethod.PATCH;
            case "DELETE": return HttpMethod.DELETE;
            default: return HttpMethod.GET;
        }
    }

    @SuppressWarnings("unchecked")
    private StateProcessorResult handleSuccessResponse(ResponseEntity<String> response,
                                                        Map<String, Object> config,
                                                        ExecutionContext context,
                                                        StateDefinition state) {
        Map<String, Object> outputVariables = new HashMap<>();
        outputVariables.put("_apiResponse_statusCode", response.getStatusCodeValue());
        outputVariables.put("_apiResponse_body", response.getBody());

        // Apply response mapping
        Object responseMappingObj = config.get("responseMapping");
        if (responseMappingObj instanceof Map) {
            Map<String, String> responseMapping = (Map<String, String>) responseMappingObj;
            Map<String, Object> responseBody = parseResponseBody(response.getBody());

            for (Map.Entry<String, String> entry : responseMapping.entrySet()) {
                String contextVariableName = entry.getKey();
                String responseField = entry.getValue();

                Object value = responseBody != null ? responseBody.get(responseField) : null;
                outputVariables.put(contextVariableName, value);
                context.setVariable(contextVariableName, value);
            }
        }

        return StateProcessorResult.builder()
                .outcome(StateOutcome.SUCCEEDED)
                .outputVariables(outputVariables)
                .build();
    }

    private StateProcessorResult handleHttpError(int statusCode, String responseBody) {
        Map<String, Object> outputVariables = new HashMap<>();
        outputVariables.put("_apiResponse_statusCode", statusCode);
        outputVariables.put("_apiResponse_body", responseBody);

        return StateProcessorResult.builder()
                .outcome(StateOutcome.FAILED)
                .nextTransitionCondition("error")
                .errorMessage("API call failed with status " + statusCode)
                .outputVariables(outputVariables)
                .build();
    }

    private StateProcessorResult buildTimeoutResult(String errorMessage) {
        Map<String, Object> outputVariables = new HashMap<>();
        outputVariables.put("_apiResponse_statusCode", null);
        outputVariables.put("_apiResponse_body", null);

        return StateProcessorResult.builder()
                .outcome(StateOutcome.FAILED)
                .nextTransitionCondition("timeout")
                .errorMessage("API call timeout/network error: " + errorMessage)
                .outputVariables(outputVariables)
                .build();
    }

    private Map<String, Object> parseResponseBody(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.warn("Failed to parse API response body as JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Factory interface for creating RestTemplate instances with specific timeout settings.
     * Allows injection of mock/test implementations.
     */
    public interface RestTemplateFactory {
        RestTemplate create(int timeoutSeconds);
    }

    /**
     * Default implementation that creates a real RestTemplate with configured timeouts.
     */
    public static class DefaultRestTemplateFactory implements RestTemplateFactory {
        @Override
        public RestTemplate create(int timeoutSeconds) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(timeoutSeconds * 1000);
            factory.setReadTimeout(timeoutSeconds * 1000);
            return new RestTemplate(factory);
        }
    }
}
