package com.xpressbees.chatbot.processor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.*;

/**
 * Property 2: Preservation - Non-API Nodes Correctly Rejected
 *
 * These tests verify that canHandle() returns false for all node configurations
 * that should NOT be handled by ApiNodeProcessor. This establishes a behavioral
 * baseline on UNFIXED code that must be preserved after the fix.
 *
 * On UNFIXED code: all these tests PASS because canHandle() only matches type=="api",
 * so any node without type=="api" returns false (including type="state" nodes).
 *
 * After the fix: these tests must STILL PASS to confirm no regression — the fix
 * should only add acceptance of type="state" + config.nodeType="api" nodes, not
 * accidentally accept other configurations.
 *
 * Validates: Requirements 2.2, 2.4, 3.1, 3.2, 3.3
 */
class ApiNodeProcessorPreservationPropertyTest {

    private final ApiNodeProcessor processor = new ApiNodeProcessor(null, null, null, null);

    /**
     * Property: For any node with a type string that is NOT "state" AND NOT "api",
     * canHandle() returns false.
     *
     * This preserves requirement 3.1/3.2/3.3: nodes with other type values are
     * never claimed by ApiNodeProcessor.
     *
     * Validates: Requirements 2.2, 3.1, 3.2, 3.3
     */
    @Property(tries = 200)
    @Tag("Feature: unified-node-type-handling, Property 2: Preservation")
    void canHandleReturnsFalseForNonStateNonApiTypes(@ForAll("nonStateNonApiTypeStrings") String type) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", type);

        boolean result = processor.canHandle(node);

        assertThat(result)
                .as("canHandle() should return false for node with type=\"%s\" "
                        + "(not \"state\" and not \"api\"), but returned true.", type)
                .isFalse();
    }

    /**
     * Property: For any node with type="state" and config=null,
     * canHandle() returns false.
     *
     * This preserves requirement 2.4: null config must not cause NPE and
     * must return false. Also preserves 3.2: nodes with null config are
     * handled by MessageNodeProcessor.
     *
     * Validates: Requirements 2.4, 3.2
     */
    @Property(tries = 100)
    @Tag("Feature: unified-node-type-handling, Property 2: Preservation")
    void canHandleReturnsFalseForStateTypeWithNullConfig(@ForAll("nodeIdsArbitrary") String nodeId) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "state");
        node.put("config", null);
        node.put("id", nodeId);

        boolean result = processor.canHandle(node);

        assertThat(result)
                .as("canHandle() should return false for node with type=\"state\" and config=null, "
                        + "but returned true.")
                .isFalse();
    }

    /**
     * Property: For any node with type="state" and config.nodeType set to a random
     * string that is NOT "api", canHandle() returns false.
     *
     * This preserves requirements 3.1, 3.3: nodes with config.nodeType="input" or
     * "workflow" (or any other value) are NOT handled by ApiNodeProcessor.
     *
     * Validates: Requirements 3.1, 3.3
     */
    @Property(tries = 200)
    @Tag("Feature: unified-node-type-handling, Property 2: Preservation")
    void canHandleReturnsFalseForStateTypeWithNonApiNodeType(
            @ForAll("nonApiNodeTypeStrings") String nodeType) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "state");

        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", nodeType);
        node.put("config", config);

        boolean result = processor.canHandle(node);

        assertThat(result)
                .as("canHandle() should return false for node with type=\"state\" "
                        + "and config.nodeType=\"%s\", but returned true.", nodeType)
                .isFalse();
    }

    /**
     * Property: For any node with type="state" and a config map that does NOT
     * contain the "nodeType" key, canHandle() returns false.
     *
     * This preserves requirement 3.2: nodes without nodeType in config are
     * handled by MessageNodeProcessor.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    @Tag("Feature: unified-node-type-handling, Property 2: Preservation")
    void canHandleReturnsFalseForStateTypeWithConfigMissingNodeTypeKey(
            @ForAll("randomConfigWithoutNodeType") Map<String, Object> config) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "state");
        node.put("config", config);

        boolean result = processor.canHandle(node);

        assertThat(result)
                .as("canHandle() should return false for node with type=\"state\" "
                        + "and config without \"nodeType\" key: %s, but returned true.", config)
                .isFalse();
    }

    /**
     * Property: For any node with type=null, canHandle() returns false.
     *
     * Validates: Requirements 2.2
     */
    @Property(tries = 50)
    @Tag("Feature: unified-node-type-handling, Property 2: Preservation")
    void canHandleReturnsFalseForNullType(@ForAll("nodeIdsArbitrary") String nodeId) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", null);
        node.put("id", nodeId);

        boolean result = processor.canHandle(node);

        assertThat(result)
                .as("canHandle() should return false for node with type=null, but returned true.")
                .isFalse();
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<String> nonStateNonApiTypeStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !"state".equals(s) && !"api".equals(s));
    }

    @Provide
    Arbitrary<String> nonApiNodeTypeStrings() {
        return Arbitraries.oneOf(
                Arbitraries.of("input", "workflow", "message"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15)
                        .filter(s -> !"api".equals(s))
        );
    }

    @Provide
    Arbitrary<Map<String, Object>> randomConfigWithoutNodeType() {
        return Arbitraries.integers().between(1, 100).map(id -> {
            Map<String, Object> config = new HashMap<>();
            config.put("apiConfigId", id);
            // Intentionally NO "nodeType" key
            return config;
        });
    }

    @Provide
    Arbitrary<String> nodeIdsArbitrary() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "node-" + s);
    }
}
