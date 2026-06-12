package com.chatbot.workflow.service;

import com.chatbot.workflow.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Property-based tests for workflow serialization and import validation.
 *
 * Feature: chatbot-workflow-builder, Property 24: Workflow serialization round-trip
 * Feature: chatbot-workflow-builder, Property 25: Import validation rejects malformed definitions
 */
class WorkflowSerializationProperties {

    private final ObjectMapper objectMapper;
    private final WorkflowImportValidator validator;

    WorkflowSerializationProperties() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.validator = new WorkflowImportValidator();
    }

    // ==================== Property 24: Workflow serialization round-trip ====================

    /**
     * Property 24: Workflow serialization round-trip
     *
     * For any valid workflow, serializing to JSON and then deserializing should produce
     * an identical workflow.
     *
     * Validates: Requirements 7.1, 7.2, 7.6
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 24: Workflow serialization round-trip")
    void serializationRoundTripPreservesWorkflow(@ForAll("validWorkflowDefinitions") WorkflowDefinition original)
            throws JsonProcessingException {
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Deserialize back
        WorkflowDefinition deserialized = objectMapper.readValue(json, WorkflowDefinition.class);

        // Assert equality
        assert original.equals(deserialized) :
                "Round-trip failed. Original: " + original + ", Deserialized: " + deserialized;
    }

    @Provide
    Arbitrary<WorkflowDefinition> validWorkflowDefinitions() {
        return Combinators.combine(
                validMetadata(),
                validStates(),
                Arbitraries.lazy(() -> validStates().flatMap(this::validTransitionsForStates)),
                validContextVariables()
        ).as((metadata, states, transitions, contextVars) ->
                new WorkflowDefinition(metadata, states, transitions, contextVars)
        ).flatMap(def -> {
            // Generate transitions based on the actual states in the definition
            return validTransitionsForStates(def.getStates())
                    .map(transitions -> new WorkflowDefinition(
                            def.getMetadata(), def.getStates(), transitions, def.getContextVariables()));
        });
    }

    private Arbitrary<WorkflowMetadata> validMetadata() {
        return Combinators.combine(
                validWorkflowName(),
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(200),
                Arbitraries.integers().between(1, 100),
                validInstant(),
                validInstant()
        ).as(WorkflowMetadata::new);
    }

    private Arbitrary<String> validWorkflowName() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(100);
    }

    private Arbitrary<Instant> validInstant() {
        // Generate instants within a reasonable range (truncated to seconds for stable round-trip)
        return Arbitraries.longs()
                .between(0L, 2000000000L)
                .map(Instant::ofEpochSecond);
    }

    private Arbitrary<List<StateDefinition>> validStates() {
        return validStateDefinition()
                .list()
                .ofMinSize(1)
                .ofMaxSize(10);
    }

    private Arbitrary<StateDefinition> validStateDefinition() {
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.of(StateType.values()),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                validPosition(),
                validConfig(),
                validRetryPolicy(),
                validOutputMapping()
        ).as(StateDefinition::new);
    }

    private Arbitrary<Position> validPosition() {
        return Combinators.combine(
                Arbitraries.doubles().between(0.0, 2000.0),
                Arbitraries.doubles().between(0.0, 2000.0)
        ).as(Position::new);
    }

    private Arbitrary<Map<String, Object>> validConfig() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                validConfigValue()
        ).ofMinSize(0).ofMaxSize(5);
    }

    private Arbitrary<Object> validConfigValue() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(50).map(s -> (Object) s),
                Arbitraries.integers().between(0, 10000).map(i -> (Object) i),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
    }

    private Arbitrary<RetryPolicy> validRetryPolicy() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Combinators.combine(
                        Arbitraries.integers().between(0, 10),
                        Arbitraries.integers().between(1, 300)
                ).as(RetryPolicy::new)
        );
    }

    private Arbitrary<Map<String, String>> validOutputMapping() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.maps(
                        validVariableName(),
                        Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(30)
                                .map(s -> "$." + s)
                ).ofMinSize(0).ofMaxSize(3)
        );
    }

    private Arbitrary<String> validVariableName() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(64)
                .filter(s -> s.matches("^[a-zA-Z0-9_]{1,64}$"));
    }

    private Arbitrary<List<TransitionDefinition>> validTransitionsForStates(List<StateDefinition> states) {
        if (states == null || states.size() < 2) {
            return Arbitraries.just(Collections.emptyList());
        }

        List<UUID> stateIds = states.stream()
                .map(StateDefinition::getId)
                .collect(Collectors.toList());

        Arbitrary<TransitionDefinition> transitionArb = Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.of(stateIds),
                Arbitraries.of(stateIds),
                Arbitraries.oneOf(
                        Arbitraries.just(null),
                        Arbitraries.of("true", "false", "error", "timeout", "fallback")
                )
        ).as(TransitionDefinition::new)
                .filter(t -> !t.getSource().equals(t.getTarget())); // no self-loops

        return transitionArb.list().ofMinSize(0).ofMaxSize(Math.min(states.size() * 2, 10));
    }

    private Arbitrary<List<ContextVariable>> validContextVariables() {
        return Combinators.combine(
                validVariableName(),
                validConfigValue().injectNull(0.3)
        ).as(ContextVariable::new)
                .list()
                .ofMinSize(0)
                .ofMaxSize(5)
                .filter(list -> list.stream().map(ContextVariable::getName).distinct().count() == list.size());
    }

    // ==================== Property 25: Import validation rejects malformed definitions ====================

    /**
     * Property 25: Import validation rejects malformed definitions
     *
     * For any JSON input missing required fields or with invalid state types,
     * import should reject it with an error message indicating the reason.
     *
     * Validates: Requirements 7.4
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 25: Import validation rejects malformed definitions")
    void importValidationRejectsMissingMetadata(@ForAll("workflowJsonMissingMetadata") String json)
            throws JsonProcessingException {
        WorkflowDefinition definition = objectMapper.readValue(json, WorkflowDefinition.class);
        List<String> errors = validator.validate(definition);

        assert !errors.isEmpty() :
                "Expected validation errors for missing metadata, but got none. JSON: " + json;
        assert errors.stream().anyMatch(e -> e.toLowerCase().contains("metadata")) :
                "Expected error about metadata, got: " + errors;
    }

    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 25: Import validation rejects malformed definitions")
    void importValidationRejectsMissingStates(@ForAll("workflowJsonMissingStates") String json)
            throws JsonProcessingException {
        WorkflowDefinition definition = objectMapper.readValue(json, WorkflowDefinition.class);
        List<String> errors = validator.validate(definition);

        assert !errors.isEmpty() :
                "Expected validation errors for missing states, but got none. JSON: " + json;
        assert errors.stream().anyMatch(e -> e.toLowerCase().contains("states")) :
                "Expected error about states, got: " + errors;
    }

    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 25: Import validation rejects malformed definitions")
    void importValidationRejectsMissingTransitions(@ForAll("workflowJsonMissingTransitions") String json)
            throws JsonProcessingException {
        WorkflowDefinition definition = objectMapper.readValue(json, WorkflowDefinition.class);
        List<String> errors = validator.validate(definition);

        assert !errors.isEmpty() :
                "Expected validation errors for missing transitions, but got none. JSON: " + json;
        assert errors.stream().anyMatch(e -> e.toLowerCase().contains("transitions")) :
                "Expected error about transitions, got: " + errors;
    }

    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 25: Import validation rejects malformed definitions")
    void importValidationRejectsInvalidStateType(@ForAll("workflowJsonWithInvalidStateType") String json) {
        // Invalid state types should cause deserialization to fail or produce null type
        try {
            WorkflowDefinition definition = objectMapper.readValue(json, WorkflowDefinition.class);
            // If deserialization succeeds, the type should be null and validator catches it
            List<String> errors = validator.validate(definition);
            assert !errors.isEmpty() :
                    "Expected validation errors for invalid state type, but got none. JSON: " + json;
        } catch (JsonProcessingException e) {
            // Deserialization failure is also acceptable - invalid type is rejected
            assert e.getMessage().contains("Unknown StateType") ||
                    e.getMessage().contains("not one of the values accepted") ||
                    e.getMessage().contains("Cannot deserialize") :
                    "Unexpected error: " + e.getMessage();
        }
    }

    @Provide
    Arbitrary<String> workflowJsonMissingMetadata() {
        return validStates().map(states -> {
            try {
                // Build JSON without metadata field
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("states", objectMapper.convertValue(states, List.class));
                jsonMap.put("transitions", Collections.emptyList());
                jsonMap.put("contextVariables", Collections.emptyList());
                return objectMapper.writeValueAsString(jsonMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Provide
    Arbitrary<String> workflowJsonMissingStates() {
        return validMetadata().map(metadata -> {
            try {
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("metadata", objectMapper.convertValue(metadata, Map.class));
                jsonMap.put("transitions", Collections.emptyList());
                jsonMap.put("contextVariables", Collections.emptyList());
                return objectMapper.writeValueAsString(jsonMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Provide
    Arbitrary<String> workflowJsonMissingTransitions() {
        return Combinators.combine(validMetadata(), validStates()).as((metadata, states) -> {
            try {
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("metadata", objectMapper.convertValue(metadata, Map.class));
                jsonMap.put("states", objectMapper.convertValue(states, List.class));
                jsonMap.put("contextVariables", Collections.emptyList());
                return objectMapper.writeValueAsString(jsonMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Provide
    Arbitrary<String> workflowJsonWithInvalidStateType() {
        return Combinators.combine(
                validMetadata(),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                        .filter(s -> !isValidStateType(s))
        ).as((metadata, invalidType) -> {
            try {
                String stateId = UUID.randomUUID().toString();
                Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("metadata", objectMapper.convertValue(metadata, Map.class));

                Map<String, Object> state = new HashMap<>();
                state.put("id", stateId);
                state.put("type", invalidType);
                state.put("name", "test");
                state.put("position", Map.of("x", 0.0, "y", 0.0));

                jsonMap.put("states", Collections.singletonList(state));
                jsonMap.put("transitions", Collections.emptyList());
                jsonMap.put("contextVariables", Collections.emptyList());
                return objectMapper.writeValueAsString(jsonMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private boolean isValidStateType(String value) {
        try {
            StateType.fromValue(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
