package com.xpressbees.chatbot.processor;

import java.util.HashMap;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property 1: canHandle Type Equivalence
 *
 * For any node map, canHandle SHALL return true if and only if the node's
 * type value is the exact string "api". For all other type values (including
 * null and absent keys), it SHALL return false.
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 */
class ApiNodeProcessorCanHandlePropertyTest {

    private final ApiNodeProcessor processor = new ApiNodeProcessor(null, null, null, null, null, null);

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 1: canHandle Type Equivalence")
    void canHandleReturnsTrueForApiType(@ForAll("apiTypeNodes") Map<String, Object> node) {
        boolean result = processor.canHandle(node);

        assert result : "canHandle should return true for node with type=\"api\", but returned false";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 1: canHandle Type Equivalence")
    void canHandleReturnsFalseForOtherTypes(@ForAll("nonApiTypeStrings") String type) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", type);

        boolean result = processor.canHandle(node);

        assert !result : "canHandle should return false for node with type=\"" + type + "\", but returned true";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 1: canHandle Type Equivalence")
    void canHandleReturnsFalseForNullType() {
        Map<String, Object> node = new HashMap<>();
        node.put("type", null);

        boolean result = processor.canHandle(node);

        assert !result : "canHandle should return false for node with type=null, but returned true";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 1: canHandle Type Equivalence")
    void canHandleReturnsFalseForAbsentTypeKey() {
        Map<String, Object> node = new HashMap<>();
        // Intentionally do NOT put "type" key

        boolean result = processor.canHandle(node);

        assert !result : "canHandle should return false for node without 'type' key, but returned true";
    }

    @Provide
    Arbitrary<Map<String, Object>> apiTypeNodes() {
        return Arbitraries.just("api").map(type -> {
            Map<String, Object> node = new HashMap<>();
            node.put("type", type);
            node.put("id", "node-1");
            node.put("name", "API Node");
            return node;
        });
    }

    @Provide
    Arbitrary<String> nonApiTypeStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !"api".equals(s));
    }
}
