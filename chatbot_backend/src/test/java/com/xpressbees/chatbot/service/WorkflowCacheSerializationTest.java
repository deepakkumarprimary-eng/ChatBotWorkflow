package com.xpressbees.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xpressbees.chatbot.entity.Workflow;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.LocalDateTime;
import java.util.*;

// Feature: redis-caching-and-performance, Property 1: Workflow cache serialization round-trip

/**
 * Property-based test for Workflow cache serialization round-trip.
 *
 * Validates: Requirements 1.1, 1.2
 *
 * For any valid Workflow entity (with arbitrary ID, name, and workflowJson content),
 * serializing it to JSON and then deserializing it back SHALL produce an equivalent
 * Workflow object with identical field values.
 */
class WorkflowCacheSerializationTest {

    private final ObjectMapper objectMapper;

    WorkflowCacheSerializationTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Property(tries = 100)
    @Tag("Feature: redis-caching-and-performance, Property 1: Workflow cache serialization round-trip")
    void workflowSerializationRoundTripPreservesAllFields(
            @ForAll("workflows") Workflow original) throws JsonProcessingException {
        // Validates: Requirements 1.1, 1.2

        // Serialize to JSON (same as WorkflowCacheService does)
        String json = objectMapper.writeValueAsString(original);

        // Deserialize back to Workflow
        Workflow deserialized = objectMapper.readValue(json, Workflow.class);

        // Assert all fields are equal
        assert Objects.equals(original.getId(), deserialized.getId()) :
                "ID mismatch: original=" + original.getId() + ", deserialized=" + deserialized.getId();

        assert Objects.equals(original.getName(), deserialized.getName()) :
                "Name mismatch: original='" + original.getName() + "', deserialized='" + deserialized.getName() + "'";

        assert Objects.equals(original.getWorkflowJson(), deserialized.getWorkflowJson()) :
                "workflowJson mismatch: original=" + original.getWorkflowJson() +
                        ", deserialized=" + deserialized.getWorkflowJson();

        assert Objects.equals(original.getCreatedAt(), deserialized.getCreatedAt()) :
                "createdAt mismatch: original=" + original.getCreatedAt() +
                        ", deserialized=" + deserialized.getCreatedAt();

        assert Objects.equals(original.getUpdatedAt(), deserialized.getUpdatedAt()) :
                "updatedAt mismatch: original=" + original.getUpdatedAt() +
                        ", deserialized=" + deserialized.getUpdatedAt();
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<Workflow> workflows() {
        Arbitrary<Long> ids = Arbitraries.longs().between(1L, Long.MAX_VALUE);
        Arbitrary<String> names = workflowNames();
        Arbitrary<Map<String, Object>> jsonMaps = workflowJsonMaps();
        Arbitrary<LocalDateTime> dateTimes = localDateTimes();

        return Combinators.combine(ids, names, jsonMaps, dateTimes, dateTimes)
                .as((id, name, workflowJson, createdAt, updatedAt) -> {
                    Workflow w = new Workflow();
                    w.setId(id);
                    w.setName(name);
                    w.setWorkflowJson(workflowJson);
                    w.setCreatedAt(createdAt);
                    w.setUpdatedAt(updatedAt);
                    return w;
                });
    }

    @Provide
    Arbitrary<String> workflowNames() {
        // Random strings of varying lengths including empty
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_')
                .ofMinLength(0).ofMaxLength(50);
    }

    @Provide
    Arbitrary<Map<String, Object>> workflowJsonMaps() {
        // Generate nested Map<String, Object> structures with nodes/transitions
        return Arbitraries.oneOf(
                simpleWorkflowJson(),
                complexWorkflowJson()
        );
    }

    private Arbitrary<Map<String, Object>> simpleWorkflowJson() {
        return Combinators.combine(
                nodeList(1, 3),
                transitionList(0, 2)
        ).as((nodes, transitions) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("nodes", nodes);
            map.put("transitions", transitions);
            return map;
        });
    }

    private Arbitrary<Map<String, Object>> complexWorkflowJson() {
        return Combinators.combine(
                nodeList(2, 8),
                transitionList(1, 6),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.integers().between(1, 10)
        ).as((nodes, transitions, description, version) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("nodes", nodes);
            map.put("transitions", transitions);
            map.put("description", description);
            map.put("version", version);
            map.put("metadata", Map.of("author", "test", "active", true));
            return map;
        });
    }

    private Arbitrary<List<Map<String, Object>>> nodeList(int min, int max) {
        return nodeMap().list().ofMinSize(min).ofMaxSize(max);
    }

    private Arbitrary<Map<String, Object>> nodeMap() {
        Arbitrary<String> nodeIds = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(3).ofMaxLength(10);
        Arbitrary<String> nodeTypes = Arbitraries.of("message", "input", "api", "decision", "workflow");
        Arbitrary<String> labels = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);

        return Combinators.combine(nodeIds, nodeTypes, labels)
                .as((id, type, label) -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", id);
                    node.put("type", type);
                    node.put("label", label);
                    node.put("data", Map.of("text", "Hello from " + label));
                    return node;
                });
    }

    private Arbitrary<List<Map<String, Object>>> transitionList(int min, int max) {
        return transitionMap().list().ofMinSize(min).ofMaxSize(max);
    }

    private Arbitrary<Map<String, Object>> transitionMap() {
        Arbitrary<String> nodeIds = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .ofMinLength(3).ofMaxLength(10);

        return Combinators.combine(nodeIds, nodeIds)
                .as((from, to) -> {
                    Map<String, Object> transition = new LinkedHashMap<>();
                    transition.put("from", from);
                    transition.put("to", to);
                    return transition;
                });
    }

    private Arbitrary<LocalDateTime> localDateTimes() {
        // Generate random LocalDateTime values (without nanos to avoid precision loss in JSON)
        return Combinators.combine(
                Arbitraries.integers().between(2020, 2030),
                Arbitraries.integers().between(1, 12),
                Arbitraries.integers().between(1, 28),
                Arbitraries.integers().between(0, 23),
                Arbitraries.integers().between(0, 59),
                Arbitraries.integers().between(0, 59)
        ).as((year, month, day, hour, minute, second) ->
                LocalDateTime.of(year, month, day, hour, minute, second)
        );
    }
}
