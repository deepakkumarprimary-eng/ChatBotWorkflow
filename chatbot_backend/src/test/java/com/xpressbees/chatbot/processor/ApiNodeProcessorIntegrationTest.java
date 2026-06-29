package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.dto.HttpExecutionResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.dto.NodeProcessingResult.Action;
import com.xpressbees.chatbot.entity.*;
import com.xpressbees.chatbot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for full API node processing flows.
 * Uses real ResponseExtractor and PlaceholderService
 * with mocked external dependencies (repository, HTTP).
 *
 * Validates: Requirements 6.1, 6.2, 6.3, 6.5, 7.1, 7.5, 8.1, 8.3, 9.1, 9.5, 9.7,
 *            10.1, 10.2, 10.3, 11.1, 11.3, 12.1, 12.4
 */
@ExtendWith(MockitoExtension.class)
class ApiNodeProcessorIntegrationTest {

    @Mock private ApiConfigCacheService apiConfigCacheService;
    @Mock private HttpExecutor httpExecutor;
    @Mock private UrlValidator urlValidator;

    private final ResponseExtractor responseExtractor = new ResponseExtractor();
    private final PlaceholderService placeholderService = new PlaceholderService();

    private ApiNodeProcessor processor;

    private static final String SESSION_ID = "session-1";
    private static final Long WORKFLOW_ID = 1L;
    private static final Long API_CONFIG_ID = 1L;

    @BeforeEach
    void setUp() {
        // Mock UrlValidator to allow all URLs in integration tests (lenient since not all tests reach URL validation)
        lenient().when(urlValidator.validate(anyString()))
                .thenReturn(com.xpressbees.chatbot.dto.UrlValidationResult.allowed());
        processor = new ApiNodeProcessor(
                apiConfigCacheService, httpExecutor, responseExtractor, urlValidator);
    }

    // ======================== Helper Methods ========================

    private ChatSession createSession() {
        ChatSession session = new ChatSession();
        session.setSessionId(SESSION_ID);
        session.setWorkflowId(WORKFLOW_ID);
        session.setContext(new HashMap<>());
        session.setStatus("active");
        return session;
    }

    private ApiConfig createApiConfig(String url, String method, List<ApiResponseMapping> mappings) {
        ApiConfig config = new ApiConfig();
        config.setId(API_CONFIG_ID);
        config.setName("test-api");
        config.setUrl(url);
        config.setMethod(method);
        config.setTimeoutMs(5000);
        config.setRetryCount(1);
        config.setHeaders(new ArrayList<>());
        config.setResponseMappings(mappings != null ? mappings : new ArrayList<>());
        return config;
    }

    private ApiResponseMapping createMapping(String path, String variableName) {
        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath(path);
        mapping.setContextVariableName(variableName);
        return mapping;
    }

    private Map<String, Object> createNode(String nodeId, Map<String, Object> config) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", nodeId);
        node.put("type", "state");
        node.put("config", config);
        return node;
    }

    private Map<String, Object> createNodeConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "api");
        config.put("apiConfigId", String.valueOf(API_CONFIG_ID));
        return config;
    }

    private Map<String, Object> createWorkflowJson(List<Map<String, Object>> nodes, List<Map<String, Object>> transitions) {
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);
        return workflowJson;
    }

    private Map<String, Object> createTransition(String sourceId, String targetId, String condition) {
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", sourceId);
        transition.put("targetNodeId", targetId);
        if (condition != null) {
            transition.put("condition", condition);
        }
        return transition;
    }

    private Map<String, Object> createWorkflowNode(String id, String name, String type) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", type);
        return node;
    }

    // ======================== Test Methods ========================

    @Test
    @DisplayName("Type 1 Auto-Advance: single unconditional transition, context updated, CONTINUE returned")
    void testType1AutoAdvance() {
        // Arrange
        ChatSession session = createSession();
        ApiResponseMapping mapping = createMapping("$.status", "status");
        ApiConfig apiConfig = createApiConfig("http://api.example.com/status", "GET", List.of(mapping));

        Map<String, Object> node = createNode("node-1", createNodeConfig());

        // Single unconditional transition
        List<Map<String, Object>> transitions = List.of(
                createTransition("node-1", "node-2", null)
        );
        List<Map<String, Object>> nodes = List.of(
                createWorkflowNode("node-1", "API Node", "api"),
                createWorkflowNode("node-2", "Next Node", "message")
        );
        Map<String, Object> workflowJson = createWorkflowJson(nodes, transitions);

        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.of(apiConfig));
        when(httpExecutor.execute(eq(apiConfig), eq("http://api.example.com/status"), any(), any()))
                .thenReturn(new HttpExecutionResult(true, 200, "{\"status\": \"active\"}", null));

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.CONTINUE);
        assertThat(session.getContext()).containsEntry("status", "active");
    }

    @Test
    @DisplayName("Type 3 Interactive Pause: displayVariable present, PAUSE with array values")
    void testType3InteractivePause() {
        // Arrange
        ChatSession session = createSession();
        ApiResponseMapping mapping = createMapping("$.items", "options");
        ApiConfig apiConfig = createApiConfig("http://api.example.com/items", "GET", List.of(mapping));

        Map<String, Object> nodeConfig = createNodeConfig();
        Map<String, Object> node = createNode("node-1", nodeConfig);
        node.put("displayVariable", "options");

        // Single transition (used for resumption after selection)
        List<Map<String, Object>> transitions = List.of(
                createTransition("node-1", "node-2", null)
        );
        List<Map<String, Object>> nodes = List.of(
                createWorkflowNode("node-1", "API Node", "api"),
                createWorkflowNode("node-2", "Next Node", "message")
        );
        Map<String, Object> workflowJson = createWorkflowJson(nodes, transitions);

        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.of(apiConfig));
        when(httpExecutor.execute(eq(apiConfig), eq("http://api.example.com/items"), any(), any()))
                .thenReturn(new HttpExecutionResult(true, 200, "{\"items\":[\"A\",\"B\",\"C\"]}", null));

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.PAUSE);
        assertThat(session.getContext()).containsEntry("_displayVariable", "options");
        assertThat(session.getContext().get("options")).isEqualTo("A\nB\nC");
    }

    @Test
    @DisplayName("Button Node Pause: multiple unconditional transitions, PAUSE with button options")
    void testButtonNodePause() {
        // Arrange
        ChatSession session = createSession();
        ApiResponseMapping mapping = createMapping("$.status", "status");
        ApiConfig apiConfig = createApiConfig("http://api.example.com/data", "GET", List.of(mapping));

        Map<String, Object> node = createNode("node-1", createNodeConfig());

        // Multiple unconditional transitions (button node)
        List<Map<String, Object>> transitions = List.of(
                createTransition("node-1", "node-opt-a", null),
                createTransition("node-1", "node-opt-b", null)
        );
        List<Map<String, Object>> nodes = List.of(
                createWorkflowNode("node-1", "API Node", "api"),
                createWorkflowNode("node-opt-a", "Option A", "message"),
                createWorkflowNode("node-opt-b", "Option B", "message")
        );
        Map<String, Object> workflowJson = createWorkflowJson(nodes, transitions);

        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.of(apiConfig));
        when(httpExecutor.execute(eq(apiConfig), eq("http://api.example.com/data"), any(), any()))
                .thenReturn(new HttpExecutionResult(true, 200, "{\"status\":\"ok\"}", null));

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.PAUSE);
        assertThat(session.getContext()).containsKey("_buttonOptions");
        String buttonOptions = (String) session.getContext().get("_buttonOptions");
        assertThat(buttonOptions).contains("Option A");
        assertThat(buttonOptions).contains("Option B");
    }

    @Test
    @DisplayName("Session State on Pause: currentNodeId and currentNodeType set correctly")
    void testSessionStateOnPause() {
        // Arrange
        ChatSession session = createSession();
        ApiResponseMapping mapping = createMapping("$.items", "options");
        ApiConfig apiConfig = createApiConfig("http://api.example.com/items", "GET", List.of(mapping));

        Map<String, Object> nodeConfig = createNodeConfig();
        Map<String, Object> node = createNode("node-1", nodeConfig);
        node.put("displayVariable", "options");

        List<Map<String, Object>> transitions = List.of(
                createTransition("node-1", "node-2", null)
        );
        List<Map<String, Object>> nodes = List.of(
                createWorkflowNode("node-1", "API Node", "api"),
                createWorkflowNode("node-2", "Next", "message")
        );
        Map<String, Object> workflowJson = createWorkflowJson(nodes, transitions);

        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.of(apiConfig));
        when(httpExecutor.execute(eq(apiConfig), any(), any(), any()))
                .thenReturn(new HttpExecutionResult(true, 200, "{\"items\":[\"X\",\"Y\"]}", null));

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.PAUSE);
        assertThat(session.getCurrentNodeId()).isEqualTo("node-1");
        assertThat(session.getCurrentNodeType()).isEqualTo("api");
    }

    @Test
    @DisplayName("Missing apiConfigId: returns CONTINUE with error message")
    void testMissingApiConfigId() {
        // Arrange
        ChatSession session = createSession();
        Map<String, Object> nodeConfig = new HashMap<>();
        nodeConfig.put("nodeType", "api");
        // no apiConfigId
        Map<String, Object> node = createNode("node-1", nodeConfig);

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.CONTINUE);
        assertThat(result.getResponse()).isNotNull();
        assertThat(result.getResponse().getResponse()).contains("missing");
    }

    @Test
    @DisplayName("Invalid apiConfigId: returns CONTINUE with error message")
    void testInvalidApiConfigId() {
        // Arrange
        ChatSession session = createSession();
        Map<String, Object> nodeConfig = new HashMap<>();
        nodeConfig.put("nodeType", "api");
        nodeConfig.put("apiConfigId", "abc");
        Map<String, Object> node = createNode("node-1", nodeConfig);

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.CONTINUE);
        assertThat(result.getResponse()).isNotNull();
        assertThat(result.getResponse().getResponse()).contains("invalid");
    }

    @Test
    @DisplayName("ApiConfig not found: returns ERROR result")
    void testApiConfigNotFound() {
        // Arrange
        ChatSession session = createSession();
        Map<String, Object> node = createNode("node-1", createNodeConfig());

        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.empty());

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).contains("No API configuration found");
    }

    @Test
    @DisplayName("HTTP timeout failure: returns ERROR result")
    void testHttpTimeoutFailure() {
        // Arrange
        ChatSession session = createSession();
        ApiConfig apiConfig = createApiConfig("http://api.example.com/slow", "GET", List.of());

        Map<String, Object> node = createNode("node-1", createNodeConfig());

        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.of(apiConfig));
        when(httpExecutor.execute(eq(apiConfig), any(), any(), any()))
                .thenReturn(new HttpExecutionResult(false, 0, null, "timeout"));

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).contains("unreachable");
    }

    @Test
    @DisplayName("HTTP status failure: returns ERROR result")
    void testHttpStatusFailure() {
        // Arrange
        ChatSession session = createSession();
        ApiConfig apiConfig = createApiConfig("http://api.example.com/fail", "GET", List.of());

        Map<String, Object> node = createNode("node-1", createNodeConfig());

        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.of(apiConfig));
        when(httpExecutor.execute(eq(apiConfig), any(), any(), any()))
                .thenReturn(new HttpExecutionResult(false, 500, null, "error"));

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).contains("500");
    }

    @Test
    @DisplayName("Invalid JSON response: returns ERROR result")
    void testInvalidJsonResponse() {
        // Arrange
        ChatSession session = createSession();
        ApiResponseMapping mapping = createMapping("$.status", "status");
        ApiConfig apiConfig = createApiConfig("http://api.example.com/data", "GET", List.of(mapping));

        Map<String, Object> node = createNode("node-1", createNodeConfig());

        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.of(apiConfig));
        when(httpExecutor.execute(eq(apiConfig), any(), any(), any()))
                .thenReturn(new HttpExecutionResult(true, 200, "{invalid json content", null));

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("No options available (displayVariable empty): returns ERROR result")
    void testNoOptionsAvailable() {
        // Arrange
        ChatSession session = createSession();
        ApiConfig apiConfig = createApiConfig("http://api.example.com/items", "GET", List.of());

        Map<String, Object> nodeConfig = createNodeConfig();
        Map<String, Object> node = createNode("node-1", nodeConfig);
        node.put("displayVariable", "options");

        // Context has no value for "options"
        when(apiConfigCacheService.findById(API_CONFIG_ID)).thenReturn(Optional.of(apiConfig));
        when(httpExecutor.execute(eq(apiConfig), any(), any(), any()))
                .thenReturn(new HttpExecutionResult(true, 200, "{}", null));

        // Act
        NodeProcessingResult result = processor.process(node, session, placeholderService, null);

        // Assert
        assertThat(result.getAction()).isEqualTo(Action.ERROR);
        assertThat(result.getErrorMessage()).contains("No options available");
    }
}
