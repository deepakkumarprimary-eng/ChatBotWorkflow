package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Property 1: Navigation entry structure preservation
 *
 * For any workflow node (with any combination of nodeType config — "input", "api", "workflow",
 * or absent), when recordNavigationEntry is called, the resulting entry SHALL contain all required
 * fields (workflowId as Long, nodeId as String, nodeType as String or null, timestamp as non-empty
 * String) and nodeType SHALL equal the node's config.nodeType value.
 *
 * Validates: Requirements 1.1, 1.2
 *
 * Feature: chat-back-navigation, Property 1: Navigation entry structure preservation
 */
class NavigationEntryPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: chat-back-navigation, Property 1: Navigation entry structure preservation")
    void navigationEntryAlwaysContainsRequiredFieldsAndNodeTypeMatchesConfig(
            @ForAll("workflowIds") long workflowId,
            @ForAll("nodeIds") String nodeId,
            @ForAll("nodeConfigs") Map<String, Object> config) {

        // Arrange: create a session with a workflowId
        ChatSession session = new ChatSession();
        session.setSessionId("test-session-" + UUID.randomUUID());
        session.setWorkflowId(workflowId);
        session.setStatus("active");
        session.setContext(new HashMap<>());

        // Build the node map with the generated config
        Map<String, Object> node = new HashMap<>();
        node.put("id", nodeId);
        node.put("name", "Test Node");
        if (config != null) {
            node.put("config", config);
        }

        // Determine expected nodeType from config
        String expectedNodeType = null;
        if (config != null && config.get("nodeType") != null) {
            expectedNodeType = (String) config.get("nodeType");
        }

        // Act: invoke recordNavigationEntry via reflection
        try {
            java.lang.reflect.Method recordMethod = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                    "recordNavigationEntry", ChatSession.class, Map.class);
            recordMethod.setAccessible(true);

            // Create a minimal service instance (only recordNavigationEntry uses session, no external deps)
            WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                    null, null, List.of(), null, null, null);

            recordMethod.invoke(service, session, node);
        } catch (Exception e) {
            throw new AssertionError("Failed to invoke recordNavigationEntry via reflection", e);
        }

        // Assert: verify the navigation entry structure
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history =
                (List<Map<String, Object>>) session.getContext().get("_navigationHistory");

        assert history != null : "Navigation history should not be null after recording";
        assert history.size() == 1 : "Should have exactly 1 entry, got " + history.size();

        Map<String, Object> entry = history.get(0);

        // 1. Entry contains workflowId as Long matching session's workflowId
        assert entry.containsKey("workflowId") : "Entry must contain 'workflowId'";
        Object entryWorkflowId = entry.get("workflowId");
        assert entryWorkflowId instanceof Long :
                "workflowId should be Long, got: " + (entryWorkflowId != null ? entryWorkflowId.getClass().getName() : "null");
        assert entryWorkflowId.equals(workflowId) :
                "workflowId should match session's workflowId. Expected " + workflowId + " got " + entryWorkflowId;

        // 2. Entry contains nodeId as String matching the node's id
        assert entry.containsKey("nodeId") : "Entry must contain 'nodeId'";
        Object entryNodeId = entry.get("nodeId");
        assert entryNodeId instanceof String :
                "nodeId should be String, got: " + (entryNodeId != null ? entryNodeId.getClass().getName() : "null");
        assert entryNodeId.equals(nodeId) :
                "nodeId should match node's id. Expected '" + nodeId + "' got '" + entryNodeId + "'";

        // 3. Entry contains nodeType as String or null, matching config.nodeType
        assert entry.containsKey("nodeType") : "Entry must contain 'nodeType' key (even if value is null)";
        Object entryNodeType = entry.get("nodeType");
        if (expectedNodeType == null) {
            assert entryNodeType == null :
                    "nodeType should be null when config has no nodeType, got: " + entryNodeType;
        } else {
            assert entryNodeType instanceof String :
                    "nodeType should be String when config has nodeType, got: "
                            + (entryNodeType != null ? entryNodeType.getClass().getName() : "null");
            assert entryNodeType.equals(expectedNodeType) :
                    "nodeType should equal config.nodeType. Expected '" + expectedNodeType + "' got '" + entryNodeType + "'";
        }

        // 4. Entry contains timestamp as non-empty String (valid ISO-8601)
        assert entry.containsKey("timestamp") : "Entry must contain 'timestamp'";
        Object entryTimestamp = entry.get("timestamp");
        assert entryTimestamp instanceof String :
                "timestamp should be String, got: " + (entryTimestamp != null ? entryTimestamp.getClass().getName() : "null");
        String timestampStr = (String) entryTimestamp;
        assert !timestampStr.isEmpty() : "timestamp should be non-empty";
        try {
            Instant.parse(timestampStr);
        } catch (DateTimeParseException e) {
            throw new AssertionError("timestamp should be valid ISO-8601, got: '" + timestampStr + "'", e);
        }

        // 5. Entry has exactly 4 fields: workflowId, nodeId, nodeType, timestamp
        assert entry.size() == 4 :
                "Entry should have exactly 4 keys (workflowId, nodeId, nodeType, timestamp), got "
                        + entry.size() + " keys: " + entry.keySet();
    }

    @Provide
    Arbitrary<Long> workflowIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> nodeIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(3).ofMaxLength(20)
                .map(s -> "node-" + s);
    }

    @Provide
    Arbitrary<Map<String, Object>> nodeConfigs() {
        // Generate various config scenarios:
        // - null config (no config key on node)
        // - config with nodeType = "input"
        // - config with nodeType = "api"
        // - config with nodeType = "workflow"
        // - config with nodeType = null (key present but value null)
        // - config without nodeType key at all
        Arbitrary<Map<String, Object>> withNodeType = Arbitraries.of("input", "api", "workflow")
                .map(type -> {
                    Map<String, Object> config = new HashMap<>();
                    config.put("nodeType", type);
                    return config;
                });

        Arbitrary<Map<String, Object>> withNullNodeType = Arbitraries.just(null)
                .map(ignored -> {
                    Map<String, Object> config = new HashMap<>();
                    config.put("nodeType", null);
                    return config;
                });

        Arbitrary<Map<String, Object>> withoutNodeTypeKey = Arbitraries.just(null)
                .map(ignored -> {
                    Map<String, Object> config = new HashMap<>();
                    config.put("variableName", "someVar");
                    return config;
                });

        Arbitrary<Map<String, Object>> nullConfig = Arbitraries.just(null);

        return Arbitraries.oneOf(withNodeType, withNullNodeType, withoutNodeTypeKey, nullConfig);
    }
}
