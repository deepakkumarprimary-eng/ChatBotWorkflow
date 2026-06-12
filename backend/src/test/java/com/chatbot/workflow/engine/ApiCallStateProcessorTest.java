package com.chatbot.workflow.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.service.ContextVariableService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for ApiCallStateProcessor covering:
 * - Successful API call with response mapping
 * - Template interpolation in URL and headers
 * - Non-2xx response follows error transition
 * - Timeout follows timeout transition
 * - Missing response fields map to null
 */
class ApiCallStateProcessorTest {

    private ApiCallStateProcessor processor;
    private ContextVariableService contextVariableService;
    private RestTemplate mockRestTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        contextVariableService = new ContextVariableService();
        objectMapper = new ObjectMapper();
        mockRestTemplate = mock(RestTemplate.class);

        ApiCallStateProcessor.RestTemplateFactory factory = timeoutSeconds -> mockRestTemplate;

        processor = new ApiCallStateProcessor(contextVariableService, objectMapper, factory);
    }

    @Test
    void process_successfulApiCallWithResponseMapping() {
        // Arrange
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://api.example.com/users/1");
        config.put("timeout", 30);

        Map<String, String> responseMapping = new HashMap<>();
        responseMapping.put("userName", "name");
        responseMapping.put("userEmail", "email");
        config.put("responseMapping", responseMapping);

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "GetUser", null, config, null, null);

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        String responseBody = "{\"name\":\"John Doe\",\"email\":\"john@example.com\",\"age\":30}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertNull(result.getNextTransitionCondition());

        Map<String, Object> output = result.getOutputVariables();
        assertEquals(200, output.get("_apiResponse_statusCode"));
        assertEquals(responseBody, output.get("_apiResponse_body"));
        assertEquals("John Doe", output.get("userName"));
        assertEquals("john@example.com", output.get("userEmail"));

        // Context should also be updated
        assertEquals("John Doe", context.getVariable("userName"));
        assertEquals("john@example.com", context.getVariable("userEmail"));
    }

    @Test
    void process_templateInterpolationInUrlAndHeaders() {
        // Arrange
        Map<String, Object> contextVars = new HashMap<>();
        contextVars.put("userId", "42");
        contextVars.put("apiToken", "secret123");

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), contextVars);

        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://api.example.com/users/{{userId}}/profile");

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer {{apiToken}}");
        headers.put("X-Custom", "static-value");
        config.put("headers", headers);

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "GetProfile", null, config, null, null);

        String responseBody = "{\"status\":\"ok\"}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                eq("https://api.example.com/users/42/profile"),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
    }

    @Test
    void process_nonSuccessResponseFollowsErrorTransition() {
        // Arrange
        Map<String, Object> config = new HashMap<>();
        config.put("method", "POST");
        config.put("url", "https://api.example.com/data");
        config.put("body", "{\"key\":\"value\"}");

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "PostData", null, config, null, null);

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        when(mockRestTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, 
                        "Resource not found".getBytes(), null));

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("404"));

        Map<String, Object> output = result.getOutputVariables();
        assertEquals(404, output.get("_apiResponse_statusCode"));
        assertEquals("Resource not found", output.get("_apiResponse_body"));
    }

    @Test
    void process_timeoutFollowsTimeoutTransition() {
        // Arrange
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://api.example.com/slow");
        config.put("timeout", 5);

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "SlowAPI", null, config, null, null);

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        when(mockRestTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("timeout", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("timeout"));
    }

    @Test
    void process_missingResponseFieldsMappedToNull() {
        // Arrange
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://api.example.com/partial");

        Map<String, String> responseMapping = new HashMap<>();
        responseMapping.put("existingField", "name");
        responseMapping.put("missingField", "nonExistentKey");
        config.put("responseMapping", responseMapping);

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "PartialResponse", null, config, null, null);

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        // Response only has "name", not "nonExistentKey"
        String responseBody = "{\"name\":\"Alice\"}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());

        Map<String, Object> output = result.getOutputVariables();
        assertEquals("Alice", output.get("existingField"));
        assertNull(output.get("missingField"));

        // Context should also reflect null
        assertEquals("Alice", context.getVariable("existingField"));
        assertNull(context.getVariable("missingField"));
    }

    @Test
    void process_nullConfigReturnsFailure() {
        // Arrange
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "NoConfig", null, null, null, null);

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
    }

    @Test
    void process_timeoutClampedToValidRange() {
        // Arrange: timeout below min (should clamp to 1)
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://api.example.com/test");
        config.put("timeout", 0);

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "Test", null, config, null, null);

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        String responseBody = "{\"ok\":true}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert - should still succeed (just verifying no exception from invalid timeout)
        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
    }

    @Test
    void process_templateInterpolationWithUndefinedVariable() {
        // Arrange
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://api.example.com/{{undefinedVar}}/data");

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "TestUndefined", null, config, null, null);

        String responseBody = "{\"result\":\"ok\"}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);

        // The URL should be interpolated to "https://api.example.com//data" (empty string for undefined)
        when(mockRestTemplate.exchange(
                eq("https://api.example.com//data"),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
    }

    @Test
    void process_bodyInterpolation() {
        // Arrange
        Map<String, Object> contextVars = new HashMap<>();
        contextVars.put("name", "World");
        contextVars.put("id", "123");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), contextVars);

        Map<String, Object> config = new HashMap<>();
        config.put("method", "POST");
        config.put("url", "https://api.example.com/greet");
        config.put("body", "{\"greeting\":\"Hello {{name}}\",\"id\":\"{{id}}\"}");

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "Greet", null, config, null, null);

        String responseBody = "{\"status\":\"sent\"}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
    }

    @Test
    void process_defaultTimeoutAppliedWhenNotConfigured() {
        // Arrange
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://api.example.com/test");
        // No timeout specified - should use default 30s

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "DefaultTimeout", null, config, null, null);

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        String responseBody = "{}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert
        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
    }

    @Test
    void interpolateTemplate_noTemplateReturnsOriginal() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        String result = processor.interpolateTemplate("plain text", context);
        assertEquals("plain text", result);
    }

    @Test
    void interpolateTemplate_multipleVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("host", "example.com");
        vars.put("port", "8080");
        vars.put("path", "api");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), vars);

        String result = processor.interpolateTemplate("https://{{host}}:{{port}}/{{path}}", context);
        assertEquals("https://example.com:8080/api", result);
    }

    @Test
    void interpolateTemplate_nullInputReturnsNull() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        String result = processor.interpolateTemplate(null, context);
        assertNull(result);
    }

    @Test
    void interpolateTemplate_emptyInputReturnsEmpty() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        String result = processor.interpolateTemplate("", context);
        assertEquals("", result);
    }

    @Test
    void process_networkErrorFollowsTimeoutTransition() {
        // Arrange: DNS resolution failure
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://nonexistent.invalid/api");

        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.API_CALL, "NetworkError", null, config, null, null);

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        when(mockRestTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("I/O error: Connection refused"));

        // Act
        StateProcessorResult result = processor.process(state, context);

        // Assert: network errors should follow timeout transition per Requirement 6.7
        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("timeout", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }
}
