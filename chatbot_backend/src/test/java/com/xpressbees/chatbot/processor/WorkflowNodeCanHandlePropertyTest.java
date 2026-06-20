package com.xpressbees.chatbot.processor;

import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * Property 8: canHandle rejects non-workflow node types
 *
 * For any node map whose config.nodeType value is a string not equal to "workflow",
 * the WorkflowNodeProcessor's canHandle method SHALL return false.
 *
 * Validates: Requirements 6.3
 *
 * Feature: workflow-node, Property 8: canHandle rejects non-workflow node types
 */
class WorkflowNodeCanHandlePropertyTest {

    private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    private final WorkflowNodeProcessor processor = new WorkflowNodeProcessor(workflowRepository);

    /**
     * Property: canHandle returns false for any config.nodeType that is not "workflow"
     * when node type is "state".
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 8: canHandle rejects non-workflow node types")
    void canHandleReturnsFalseForNonWorkflowNodeTypes(
            @ForAll("nonWorkflowNodeTypes") String nodeType) {
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", nodeType);

        Map<String, Object> node = new HashMap<>();
        node.put("type", "state");
        node.put("config", config);

        boolean result = processor.canHandle(node);

        assert !result : "canHandle should return false for nodeType: " + nodeType;
    }

    /**
     * Property: canHandle returns false when node type is not "state",
     * regardless of config.nodeType value.
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 8: canHandle rejects non-workflow node types")
    void canHandleReturnsFalseForNonStateTypes(
            @ForAll("nonStateTypes") String type) {
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");

        Map<String, Object> node = new HashMap<>();
        node.put("type", type);
        node.put("config", config);

        boolean result = processor.canHandle(node);

        assert !result : "canHandle should return false for node type: " + type;
    }

    /**
     * Property: canHandle returns false when config is null.
     *
     * Validates: Requirements 6.3
     */
    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 8: canHandle rejects non-workflow node types")
    void canHandleReturnsFalseWhenConfigIsNull(
            @ForAll("allNodeTypes") String type) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", type);
        node.put("config", null);

        boolean result = processor.canHandle(node);

        assert !result : "canHandle should return false when config is null for type: " + type;
    }

    @Provide
    Arbitrary<String> nonWorkflowNodeTypes() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(s -> !"workflow".equals(s));
    }

    @Provide
    Arbitrary<String> nonStateTypes() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(s -> !"state".equals(s));
    }

    @Provide
    Arbitrary<String> allNodeTypes() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30);
    }
}
