package com.xpressbees.chatbot.processor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.*;

/**
 * Bug Condition Exploration Test - ApiNodeProcessor Rejects Unified Format
 *
 * This test encodes the EXPECTED behavior after the fix: canHandle() should return true
 * for nodes with type="state" and config.nodeType="api".
 *
 * On UNFIXED code, this test is EXPECTED TO FAIL because the current canHandle()
 * implementation only checks "api".equals(node.get("type")) and returns false
 * for nodes with type="state".
 *
 * Validates: Requirements 1.1, 1.2, 2.1, 2.3
 */
class ApiNodeProcessorUnifiedFormatBugConditionTest {

    private final ApiNodeProcessor processor = new ApiNodeProcessor(null, null, null, null, null, null);

    /**
     * Property: For any node with type="state" and config.nodeType="api",
     * canHandle() should return true.
     *
     * This WILL FAIL on unfixed code because canHandle() checks "api".equals(node.get("type"))
     * and type="state" does not match.
     */
    @Property(tries = 100)
    @Tag("Feature: unified-node-type-handling, Property 1: Bug Condition")
    void canHandleAcceptsUnifiedFormatNodes(@ForAll("unifiedApiNodes") Map<String, Object> node) {
        boolean result = processor.canHandle(node);

        assertThat(result)
                .as("canHandle() should return true for unified format node %s "
                        + "but returned false. Bug confirmed: canHandle() only checks type==\"api\" "
                        + "and rejects type=\"state\" + config.nodeType=\"api\" nodes.", node)
                .isTrue();
    }

    /**
     * Generates node maps in the unified format:
     * - type: "state"
     * - config: {nodeType: "api"} with optional fields (apiConfigId, displayVariable)
     */
    @Provide
    Arbitrary<Map<String, Object>> unifiedApiNodes() {
        Arbitrary<Integer> apiConfigIds = Arbitraries.integers().between(1, 1000);
        Arbitrary<String> displayVariables = Arbitraries.of("options", "items", "results", "choices");
        Arbitrary<Boolean> includeApiConfigId = Arbitraries.of(true, false);
        Arbitrary<Boolean> includeDisplayVariable = Arbitraries.of(true, false);

        return Combinators.combine(apiConfigIds, displayVariables, includeApiConfigId, includeDisplayVariable)
                .as((apiConfigId, displayVar, hasApiConfigId, hasDisplayVar) -> {
                    Map<String, Object> node = new HashMap<>();
                    node.put("type", "state");
                    node.put("id", "node-" + apiConfigId);

                    Map<String, Object> config = new HashMap<>();
                    config.put("nodeType", "api");

                    if (hasApiConfigId) {
                        config.put("apiConfigId", apiConfigId);
                    }
                    if (hasDisplayVar) {
                        config.put("displayVariable", displayVar);
                    }

                    node.put("config", config);
                    return node;
                });
    }
}
