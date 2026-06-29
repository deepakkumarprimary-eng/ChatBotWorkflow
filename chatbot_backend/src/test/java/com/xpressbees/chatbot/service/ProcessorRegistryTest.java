package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;

import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Feature: redis-caching-and-performance, Property 9: Processor registry lookup correctness

/**
 * Property-based tests for ProcessorRegistry.
 *
 * Validates: Requirements 6.2, 6.3
 *
 * For any known node type string (from the set: "message", "input", "api", "decision", "workflow"),
 * the registry SHALL return the processor that handles that type. For any string that is not a known
 * node type, the registry SHALL return the MessageNodeProcessor as fallback.
 */
class ProcessorRegistryTest {

    private static final Set<String> KNOWN_TYPES = Set.of("message", "input", "api", "decision", "workflow");

    private final Map<String, NodeProcessor> mockProcessors;
    private final MessageNodeProcessor fallbackProcessor;
    private final ProcessorRegistry registry;

    ProcessorRegistryTest() {
        // Create mock processors for each known type
        mockProcessors = new HashMap<>();
        for (String type : KNOWN_TYPES) {
            NodeProcessor processor = mock(NodeProcessor.class);
            when(processor.getNodeType()).thenReturn(type);
            mockProcessors.put(type, processor);
        }

        // The fallback is the MessageNodeProcessor mock (also registered for "message")
        fallbackProcessor = mock(MessageNodeProcessor.class);
        when(fallbackProcessor.getNodeType()).thenReturn("message");

        // Replace "message" entry with the fallback processor so it serves both roles
        mockProcessors.put("message", fallbackProcessor);

        // Build the registry from the list of processors
        List<NodeProcessor> processorList = new ArrayList<>(mockProcessors.values());
        registry = new ProcessorRegistry(processorList, fallbackProcessor);
    }

    /**
     * Property A: For any known type from {"message", "input", "api", "decision", "workflow"},
     * getProcessor returns the correct processor for that type.
     *
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 9: Processor registry lookup correctness")
    void knownNodeTypeReturnsCorrectProcessor(@ForAll("knownNodeTypes") String knownType) {
        // **Validates: Requirements 6.2**
        NodeProcessor result = registry.getProcessor(knownType);
        NodeProcessor expected = mockProcessors.get(knownType);

        assert result == expected :
                "For known type '" + knownType + "', expected processor " +
                System.identityHashCode(expected) + " but got " + System.identityHashCode(result);
    }

    /**
     * Property B: For any random string NOT in the known set, getProcessor returns
     * the fallback (MessageNodeProcessor).
     *
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 9: Processor registry lookup correctness")
    void unknownNodeTypeReturnsFallbackProcessor(@ForAll("unknownNodeTypes") String unknownType) {
        // **Validates: Requirements 6.3**
        NodeProcessor result = registry.getProcessor(unknownType);

        assert result == fallbackProcessor :
                "For unknown type '" + unknownType + "', expected fallback MessageNodeProcessor " +
                System.identityHashCode(fallbackProcessor) + " but got " + System.identityHashCode(result);
    }

    /**
     * Property C: For null nodeType, getProcessor returns the fallback (MessageNodeProcessor).
     *
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 9: Processor registry lookup correctness")
    void nullNodeTypeReturnsFallbackProcessor() {
        // **Validates: Requirements 6.3**
        NodeProcessor result = registry.getProcessor(null);

        assert result == fallbackProcessor :
                "For null nodeType, expected fallback MessageNodeProcessor " +
                System.identityHashCode(fallbackProcessor) + " but got " + System.identityHashCode(result);
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<String> knownNodeTypes() {
        return Arbitraries.of("message", "input", "api", "decision", "workflow");
    }

    @Provide
    Arbitrary<String> unknownNodeTypes() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !KNOWN_TYPES.contains(s));
    }
}
