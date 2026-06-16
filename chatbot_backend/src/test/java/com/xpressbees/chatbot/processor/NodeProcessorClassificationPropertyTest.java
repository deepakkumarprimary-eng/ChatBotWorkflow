package com.xpressbees.chatbot.processor;

import java.util.HashMap;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property 2: Node Classification Mutual Exclusivity
 *
 * For any node represented as a Map, exactly one of the following holds:
 * (a) InputNodeProcessor.canHandle returns true,
 * (b) MessageNodeProcessor.canHandle returns true, or
 * (c) neither processor handles it.
 * The two processors SHALL never both return true for the same node.
 *
 * Feature: websocket-workflow-execution, Property 2: Node Classification Mutual Exclusivity
 */
class NodeProcessorClassificationPropertyTest {

    private final InputNodeProcessor inputProcessor = new InputNodeProcessor();
    private final MessageNodeProcessor messageProcessor = new MessageNodeProcessor();

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 2: Node Classification Mutual Exclusivity")
    void inputAndMessageProcessorsNeverBothHandleSameNode(
            @ForAll("randomNodes") Map<String, Object> node) {

        boolean inputHandles = inputProcessor.canHandle(node);
        boolean messageHandles = messageProcessor.canHandle(node);

        // They must never both return true
        assert !(inputHandles && messageHandles) :
                "Both InputNodeProcessor and MessageNodeProcessor claim to handle node: " + node;
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 2: Node Classification Mutual Exclusivity")
    void stateNodeWithInputConfigIsHandledByInputProcessor(
            @ForAll("inputNodes") Map<String, Object> node) {

        assert inputProcessor.canHandle(node) :
                "InputNodeProcessor should handle state node with config.nodeType=input";
        assert !messageProcessor.canHandle(node) :
                "MessageNodeProcessor should NOT handle state node with config.nodeType=input";
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 2: Node Classification Mutual Exclusivity")
    void stateNodeWithoutConfigIsHandledByMessageProcessor(
            @ForAll("messageNodes") Map<String, Object> node) {

        assert messageProcessor.canHandle(node) :
                "MessageNodeProcessor should handle state node without config.nodeType";
        assert !inputProcessor.canHandle(node) :
                "InputNodeProcessor should NOT handle state node without config.nodeType";
    }

    @Provide
    Arbitrary<Map<String, Object>> randomNodes() {
        Arbitrary<String> types = Arbitraries.of("state", "action", "condition", "end", "start", null);
        Arbitrary<Map<String, Object>> configs = Arbitraries.of(
                null,
                new HashMap<>(),
                Map.of("nodeType", "input"),
                Map.of("nodeType", "output"),
                Map.of("someKey", "someValue")
        );

        return Combinators.combine(types, configs).as((type, config) -> {
            Map<String, Object> node = new HashMap<>();
            node.put("type", type);
            node.put("config", config);
            node.put("id", "test-id");
            node.put("name", "Test Node");
            return node;
        });
    }

    @Provide
    Arbitrary<Map<String, Object>> inputNodes() {
        return Arbitraries.of("input-node").map(ignore -> {
            Map<String, Object> node = new HashMap<>();
            node.put("type", "state");
            node.put("config", Map.of("nodeType", "input"));
            node.put("id", "test-id");
            node.put("name", "Enter value");
            return node;
        });
    }

    @Provide
    Arbitrary<Map<String, Object>> messageNodes() {
        Arbitrary<Map<String, Object>> configs = Arbitraries.of(
                null,
                new HashMap<>(),
                Map.of("someKey", "value")
        );
        return configs.map(config -> {
            Map<String, Object> node = new HashMap<>();
            node.put("type", "state");
            node.put("config", config);
            node.put("id", "test-id");
            node.put("name", "Hello!");
            return node;
        });
    }
}
