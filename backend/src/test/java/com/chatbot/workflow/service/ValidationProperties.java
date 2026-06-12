package com.chatbot.workflow.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.workflow.model.Position;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.TransitionDefinition;
import com.chatbot.workflow.model.WorkflowDefinition;
import com.chatbot.workflow.model.WorkflowMetadata;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for the ValidationEngine.
 * Pure unit tests — no Spring context needed.
 *
 * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6
 */
class ValidationProperties {

    private final ValidationEngine engine = new ValidationEngine();

    // ========================================================================
    // Helper methods
    // ========================================================================

    private static WorkflowMetadata defaultMetadata() {
        return new WorkflowMetadata("Test Workflow", "desc", 1, Instant.now(), Instant.now());
    }

    private static StateDefinition makeState(UUID id, StateType type, String name, Map<String, Object> config) {
        return new StateDefinition(id, type, name, new Position(0, 0), config, null, null);
    }

    private static TransitionDefinition makeTransition(UUID source, UUID target, String condition) {
        return new TransitionDefinition(UUID.randomUUID(), source, target, condition);
    }

    private static WorkflowDefinition makeWorkflow(List<StateDefinition> states, List<TransitionDefinition> transitions) {
        return new WorkflowDefinition(defaultMetadata(), states, transitions, Collections.emptyList());
    }

    private static Map<String, Object> apiCallConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "https://example.com/api");
        return config;
    }

    private static Map<String, Object> conditionConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("expression", "x == 1");
        return config;
    }

    private static Map<String, Object> responseConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("template", "Hello {{name}}");
        return config;
    }

    private static Map<String, Object> inputConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "Enter your name");
        config.put("variableName", "userName");
        return config;
    }

    private static Map<String, Object> waitConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("duration", 10);
        return config;
    }

    private static Map<String, Object> parallelConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("branches", Arrays.asList("branch1", "branch2"));
        return config;
    }

    private static Map<String, Object> configForType(StateType type) {
        switch (type) {
            case API_CALL: return apiCallConfig();
            case CONDITION: return conditionConfig();
            case RESPONSE: return responseConfig();
            case INPUT: return inputConfig();
            case WAIT: return waitConfig();
            case PARALLEL: return parallelConfig();
            case END: return Collections.emptyMap();
            default: return Collections.emptyMap();
        }
    }

    /**
     * Builds a valid linear workflow: start -> s1 -> s2 -> ... -> end
     * with the given non-end state types in the middle.
     */
    private static WorkflowDefinition buildValidLinearWorkflow(List<StateType> middleTypes) {
        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        // Start state (API_CALL by default, has no incoming transitions)
        UUID startId = UUID.randomUUID();
        states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));

        UUID prevId = startId;
        for (int i = 0; i < middleTypes.size(); i++) {
            StateType type = middleTypes.get(i);
            UUID stateId = UUID.randomUUID();
            states.add(makeState(stateId, type, "State_" + i, configForType(type)));

            if (type == StateType.CONDITION) {
                // For condition states, we need true/false transitions
                // true goes to this state's next, false goes to end (handled below)
            }
            transitions.add(makeTransition(prevId, stateId, null));
            prevId = stateId;
        }

        // End state
        UUID endId = UUID.randomUUID();
        states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));
        transitions.add(makeTransition(prevId, endId, null));

        return makeWorkflow(states, transitions);
    }

    // ========================================================================
    // Property 13: Single start state validation
    // For any workflow graph, validation should pass (regarding start state)
    // if and only if exactly one state has no incoming transitions.
    // ========================================================================

    /**
     * Property 13: A workflow with exactly one state having no incoming transitions
     * should not produce NO_START_STATE or MULTIPLE_START_STATES errors.
     *
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 13: Single start state validation")
    void singleStartStateShouldPassValidation(@ForAll @IntRange(min = 1, max = 5) int chainLength) {
        // Build a linear chain: start -> middle states -> end
        // Only the first state has no incoming transitions
        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        UUID startId = UUID.randomUUID();
        states.add(makeState(startId, StateType.RESPONSE, "Start", responseConfig()));

        UUID prevId = startId;
        for (int i = 0; i < chainLength - 1; i++) {
            UUID id = UUID.randomUUID();
            states.add(makeState(id, StateType.RESPONSE, "Mid_" + i, responseConfig()));
            transitions.add(makeTransition(prevId, id, null));
            prevId = id;
        }

        UUID endId = UUID.randomUUID();
        states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));
        transitions.add(makeTransition(prevId, endId, null));

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        // Should not have start state errors
        List<ValidationError> startErrors = result.getErrors().stream()
                .filter(e -> e.getErrorType() == ValidationErrorType.NO_START_STATE
                        || e.getErrorType() == ValidationErrorType.MULTIPLE_START_STATES)
                .collect(Collectors.toList());
        assertThat(startErrors).isEmpty();
    }

    /**
     * Property 13: A workflow where all states have incoming transitions (0 start states)
     * should produce a NO_START_STATE error.
     *
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 13: Single start state validation")
    void zeroStartStatesShouldFailValidation(@ForAll @IntRange(min = 2, max = 5) int stateCount) {
        // Create a cycle: every state has at least one incoming transition
        List<StateDefinition> states = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < stateCount; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            states.add(makeState(id, StateType.RESPONSE, "State_" + i, responseConfig()));
        }

        // Create a cycle so every state has an incoming transition
        List<TransitionDefinition> transitions = new ArrayList<>();
        for (int i = 0; i < stateCount; i++) {
            UUID source = ids.get(i);
            UUID target = ids.get((i + 1) % stateCount);
            transitions.add(makeTransition(source, target, null));
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();
        boolean hasNoStartError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.NO_START_STATE);
        assertThat(hasNoStartError).isTrue();
    }

    /**
     * Property 13: A workflow with 2+ states having no incoming transitions
     * should produce a MULTIPLE_START_STATES error.
     *
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 13: Single start state validation")
    void multipleStartStatesShouldFailValidation(@ForAll @IntRange(min = 2, max = 5) int startCount) {
        // Create multiple states with no incoming transitions
        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        // Each "start" state points to a shared end state
        UUID endId = UUID.randomUUID();
        states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));

        for (int i = 0; i < startCount; i++) {
            UUID id = UUID.randomUUID();
            states.add(makeState(id, StateType.RESPONSE, "Start_" + i, responseConfig()));
            transitions.add(makeTransition(id, endId, null));
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();
        boolean hasMultipleStartError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.MULTIPLE_START_STATES);
        assertThat(hasMultipleStartError).isTrue();
    }

    // ========================================================================
    // Property 14: Non-End states require outgoing transitions
    // For any workflow graph, validation should fail if and only if there exists
    // a non-End_State with zero outgoing transitions.
    // ========================================================================

    /**
     * Property 14: A non-End state with no outgoing transitions should produce
     * a NO_OUTGOING_TRANSITION error.
     *
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 14: Non-End states require outgoing transitions")
    void nonEndStateWithNoOutgoingShouldFail(@ForAll("nonEndStateTypes") StateType stateType) {
        // Create a workflow with a start state that transitions to an orphan non-End state
        // The orphan state has no outgoing transitions
        UUID startId = UUID.randomUUID();
        UUID orphanId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                makeState(startId, StateType.API_CALL, "Start", apiCallConfig()),
                makeState(orphanId, stateType, "Orphan", configForType(stateType)),
                makeState(endId, StateType.END, "End", Collections.emptyMap())
        );

        // Start -> End, Start -> Orphan (Orphan has no outgoing)
        List<TransitionDefinition> transitions = Arrays.asList(
                makeTransition(startId, endId, null),
                makeTransition(startId, orphanId, null)
        );

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();
        boolean hasNoOutgoingError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.NO_OUTGOING_TRANSITION
                        && orphanId.equals(e.getStateId()));
        assertThat(hasNoOutgoingError).isTrue();
    }

    /**
     * Property 14: End states with no outgoing transitions should NOT produce errors.
     *
     * **Validates: Requirements 4.2**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 14: Non-End states require outgoing transitions")
    void endStateWithNoOutgoingShouldPass(@ForAll @IntRange(min = 1, max = 3) int endStateCount) {
        UUID startId = UUID.randomUUID();
        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));

        // Multiple end states, all without outgoing
        for (int i = 0; i < endStateCount; i++) {
            UUID endId = UUID.randomUUID();
            states.add(makeState(endId, StateType.END, "End_" + i, Collections.emptyMap()));
            transitions.add(makeTransition(startId, endId, null));
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        // Should NOT have NO_OUTGOING_TRANSITION errors
        boolean hasNoOutgoingError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.NO_OUTGOING_TRANSITION);
        assertThat(hasNoOutgoingError).isFalse();
    }

    @Provide
    Arbitrary<StateType> nonEndStateTypes() {
        return Arbitraries.of(
                StateType.API_CALL, StateType.CONDITION, StateType.RESPONSE,
                StateType.INPUT, StateType.WAIT, StateType.PARALLEL
        );
    }

    // ========================================================================
    // Property 15: Condition state transition structure
    // For any workflow containing Condition_State nodes, validation should pass
    // if and only if each Condition_State has exactly two outgoing transitions
    // labeled "true" and "false".
    // ========================================================================

    /**
     * Property 15: A condition state with exactly "true" and "false" transitions
     * should not produce CONDITION_TRANSITIONS errors.
     *
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 15: Condition state transition structure")
    void conditionWithTrueFalseShouldPass(@ForAll @IntRange(min = 1, max = 3) int conditionCount) {
        UUID startId = UUID.randomUUID();
        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));

        UUID prevId = startId;
        for (int i = 0; i < conditionCount; i++) {
            UUID condId = UUID.randomUUID();
            UUID trueTarget = UUID.randomUUID();
            UUID falseTarget = UUID.randomUUID();

            states.add(makeState(condId, StateType.CONDITION, "Cond_" + i, conditionConfig()));
            states.add(makeState(trueTarget, StateType.END, "TrueEnd_" + i, Collections.emptyMap()));
            states.add(makeState(falseTarget, StateType.END, "FalseEnd_" + i, Collections.emptyMap()));

            transitions.add(makeTransition(prevId, condId, null));
            transitions.add(makeTransition(condId, trueTarget, "true"));
            transitions.add(makeTransition(condId, falseTarget, "false"));

            prevId = trueTarget; // Won't be used after last iteration
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        boolean hasConditionError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.CONDITION_TRANSITIONS);
        assertThat(hasConditionError).isFalse();
    }

    /**
     * Property 15: A condition state with wrong number of transitions should
     * produce a CONDITION_TRANSITIONS error.
     *
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 15: Condition state transition structure")
    void conditionWithWrongTransitionCountShouldFail(@ForAll @IntRange(min = 3, max = 5) int transitionCount) {
        UUID startId = UUID.randomUUID();
        UUID condId = UUID.randomUUID();

        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));
        states.add(makeState(condId, StateType.CONDITION, "Cond", conditionConfig()));
        transitions.add(makeTransition(startId, condId, null));

        // Create more than 2 outgoing transitions from the condition state
        for (int i = 0; i < transitionCount; i++) {
            UUID targetId = UUID.randomUUID();
            states.add(makeState(targetId, StateType.END, "End_" + i, Collections.emptyMap()));
            transitions.add(makeTransition(condId, targetId, i == 0 ? "true" : (i == 1 ? "false" : null)));
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();
        boolean hasConditionError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.CONDITION_TRANSITIONS
                        && condId.equals(e.getStateId()));
        assertThat(hasConditionError).isTrue();
    }

    /**
     * Property 15: A condition state with 2 transitions but wrong labels
     * should produce a CONDITION_TRANSITIONS error.
     *
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 15: Condition state transition structure")
    void conditionWithWrongLabelsShouldFail(@ForAll("wrongLabelPairs") List<String> labels) {
        UUID startId = UUID.randomUUID();
        UUID condId = UUID.randomUUID();
        UUID target1Id = UUID.randomUUID();
        UUID target2Id = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                makeState(startId, StateType.API_CALL, "Start", apiCallConfig()),
                makeState(condId, StateType.CONDITION, "Cond", conditionConfig()),
                makeState(target1Id, StateType.END, "End1", Collections.emptyMap()),
                makeState(target2Id, StateType.END, "End2", Collections.emptyMap())
        );

        List<TransitionDefinition> transitions = Arrays.asList(
                makeTransition(startId, condId, null),
                makeTransition(condId, target1Id, labels.get(0)),
                makeTransition(condId, target2Id, labels.get(1))
        );

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();
        boolean hasConditionError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.CONDITION_TRANSITIONS);
        assertThat(hasConditionError).isTrue();
    }

    @Provide
    Arbitrary<List<String>> wrongLabelPairs() {
        // Generate pairs of labels that are NOT exactly {"true", "false"}
        return Arbitraries.of(
                Arrays.asList("true", "true"),
                Arrays.asList("false", "false"),
                Arrays.asList("yes", "no"),
                Arrays.asList("true", null),
                Arrays.asList(null, "false"),
                Arrays.asList("error", "timeout"),
                Arrays.asList(null, null)
        );
    }

    // ========================================================================
    // Property 16: All states reachable from start
    // For any workflow graph, validation should fail if and only if there exists
    // at least one state unreachable from the start state.
    // ========================================================================

    /**
     * Property 16: A workflow where all states are reachable from start should
     * NOT produce UNREACHABLE_STATE errors.
     *
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 16: All states reachable from start")
    void fullyConnectedWorkflowShouldHaveNoUnreachableErrors(@ForAll @IntRange(min = 1, max = 5) int chainLength) {
        // Linear chain: start -> mid1 -> mid2 -> ... -> end (all reachable)
        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        UUID startId = UUID.randomUUID();
        states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));

        UUID prevId = startId;
        for (int i = 0; i < chainLength; i++) {
            UUID id = UUID.randomUUID();
            states.add(makeState(id, StateType.RESPONSE, "Mid_" + i, responseConfig()));
            transitions.add(makeTransition(prevId, id, null));
            prevId = id;
        }

        UUID endId = UUID.randomUUID();
        states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));
        transitions.add(makeTransition(prevId, endId, null));

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        boolean hasUnreachableError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.UNREACHABLE_STATE);
        assertThat(hasUnreachableError).isFalse();
    }

    /**
     * Property 16: A workflow with isolated (disconnected) states should produce
     * UNREACHABLE_STATE errors for those states.
     *
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 16: All states reachable from start")
    void isolatedStatesShouldProduceUnreachableErrors(@ForAll @IntRange(min = 2, max = 5) int isolatedCount) {
        // Create a valid main chain plus isolated states that form a disconnected cycle.
        // The isolated states have incoming transitions from each other (so they are NOT
        // additional start states), but none are reachable from the real start state.
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));
        states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));
        transitions.add(makeTransition(startId, endId, null));

        // Add isolated states forming a cycle (each has an incoming from the previous)
        List<UUID> isolatedIds = new ArrayList<>();
        for (int i = 0; i < isolatedCount; i++) {
            UUID id = UUID.randomUUID();
            isolatedIds.add(id);
            states.add(makeState(id, StateType.RESPONSE, "Isolated_" + i, responseConfig()));
        }
        // Create a cycle among isolated states so each has incoming AND outgoing
        for (int i = 0; i < isolatedCount; i++) {
            UUID source = isolatedIds.get(i);
            UUID target = isolatedIds.get((i + 1) % isolatedCount);
            transitions.add(makeTransition(source, target, null));
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();
        List<ValidationError> unreachableErrors = result.getErrors().stream()
                .filter(e -> e.getErrorType() == ValidationErrorType.UNREACHABLE_STATE)
                .collect(Collectors.toList());
        assertThat(unreachableErrors).hasSizeGreaterThanOrEqualTo(isolatedCount);

        // All isolated state IDs should appear in unreachable errors
        for (UUID isolatedId : isolatedIds) {
            boolean reported = unreachableErrors.stream()
                    .anyMatch(e -> isolatedId.equals(e.getStateId()));
            assertThat(reported).isTrue();
        }
    }

    // ========================================================================
    // Property 17: Required configuration fields validation
    // For any state with a given State_Type, validation should pass if and only
    // if all required config fields are populated.
    // ========================================================================

    /**
     * Property 17: States with all required config fields populated should not
     * produce MISSING_CONFIG errors.
     *
     * **Validates: Requirements 4.5**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 17: Required configuration fields validation")
    void statesWithCompleteConfigShouldPass(@ForAll("allStateTypes") StateType stateType) {
        UUID startId = UUID.randomUUID();
        UUID stateId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        if (stateType == StateType.END) {
            // For END type, it IS the end state
            states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));
            states.add(makeState(stateId, StateType.END, "TestEnd", Collections.emptyMap()));
            transitions.add(makeTransition(startId, stateId, null));
        } else if (stateType == StateType.CONDITION) {
            // Condition needs true/false transitions
            UUID trueId = UUID.randomUUID();
            UUID falseId = UUID.randomUUID();
            states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));
            states.add(makeState(stateId, StateType.CONDITION, "TestCond", conditionConfig()));
            states.add(makeState(trueId, StateType.END, "TrueEnd", Collections.emptyMap()));
            states.add(makeState(falseId, StateType.END, "FalseEnd", Collections.emptyMap()));
            transitions.add(makeTransition(startId, stateId, null));
            transitions.add(makeTransition(stateId, trueId, "true"));
            transitions.add(makeTransition(stateId, falseId, "false"));
        } else {
            states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));
            states.add(makeState(stateId, stateType, "TestState", configForType(stateType)));
            states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));
            transitions.add(makeTransition(startId, stateId, null));
            transitions.add(makeTransition(stateId, endId, null));
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        boolean hasMissingConfigError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.MISSING_CONFIG
                        && stateId.equals(e.getStateId()));
        assertThat(hasMissingConfigError).isFalse();
    }

    /**
     * Property 17: States with missing required config fields should produce
     * MISSING_CONFIG errors.
     *
     * **Validates: Requirements 4.5**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 17: Required configuration fields validation")
    void statesWithMissingConfigShouldFail(@ForAll("nonEndStateTypes") StateType stateType) {
        UUID startId = UUID.randomUUID();
        UUID stateId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        // Create the state with an empty config (missing required fields)
        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        if (stateType == StateType.CONDITION) {
            // Condition with empty config and true/false transitions
            UUID trueId = UUID.randomUUID();
            UUID falseId = UUID.randomUUID();
            states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));
            states.add(makeState(stateId, StateType.CONDITION, "BadCond", Collections.emptyMap()));
            states.add(makeState(trueId, StateType.END, "TrueEnd", Collections.emptyMap()));
            states.add(makeState(falseId, StateType.END, "FalseEnd", Collections.emptyMap()));
            transitions.add(makeTransition(startId, stateId, null));
            transitions.add(makeTransition(stateId, trueId, "true"));
            transitions.add(makeTransition(stateId, falseId, "false"));
        } else {
            states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));
            states.add(makeState(stateId, stateType, "BadState", Collections.emptyMap()));
            states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));
            transitions.add(makeTransition(startId, stateId, null));
            transitions.add(makeTransition(stateId, endId, null));
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();
        boolean hasMissingConfigError = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.MISSING_CONFIG
                        && stateId.equals(e.getStateId()));
        assertThat(hasMissingConfigError).isTrue();
    }

    @Provide
    Arbitrary<StateType> allStateTypes() {
        return Arbitraries.of(StateType.values());
    }

    // ========================================================================
    // Property 18: Complete validation error reporting
    // For any workflow with N distinct validation errors, the validator should
    // report all N errors simultaneously.
    // ========================================================================

    /**
     * Property 18: A workflow with multiple intentional errors should report all
     * of them simultaneously.
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 18: Complete validation error reporting")
    void allErrorsShouldBeReportedSimultaneously(@ForAll @IntRange(min = 2, max = 5) int orphanCount) {
        // Build a workflow with multiple non-End states missing outgoing transitions
        // Each should produce its own error, and all should be reported together
        UUID startId = UUID.randomUUID();
        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));

        // Add an end state connected from start
        UUID endId = UUID.randomUUID();
        states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));
        transitions.add(makeTransition(startId, endId, null));

        // Add multiple orphan states (reachable from start but with no outgoing)
        List<UUID> orphanIds = new ArrayList<>();
        for (int i = 0; i < orphanCount; i++) {
            UUID orphanId = UUID.randomUUID();
            orphanIds.add(orphanId);
            states.add(makeState(orphanId, StateType.RESPONSE, "Orphan_" + i, responseConfig()));
            transitions.add(makeTransition(startId, orphanId, null)); // reachable from start
        }

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();

        // Each orphan state should have a NO_OUTGOING_TRANSITION error
        List<ValidationError> noOutgoingErrors = result.getErrors().stream()
                .filter(e -> e.getErrorType() == ValidationErrorType.NO_OUTGOING_TRANSITION)
                .collect(Collectors.toList());

        // All orphan errors should be present simultaneously
        assertThat(noOutgoingErrors).hasSize(orphanCount);
        for (UUID orphanId : orphanIds) {
            boolean found = noOutgoingErrors.stream()
                    .anyMatch(e -> orphanId.equals(e.getStateId()));
            assertThat(found).isTrue();
        }
    }

    /**
     * Property 18: A workflow with different types of errors should report all types.
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 18: Complete validation error reporting")
    void differentErrorTypesShouldAllBeReported(@ForAll @IntRange(min = 1, max = 3) int extraOrphans) {
        // Create a workflow with:
        // 1. A non-End state with no outgoing (NO_OUTGOING_TRANSITION)
        // 2. A state with missing config (MISSING_CONFIG)
        // Both error types should be reported simultaneously

        UUID startId = UUID.randomUUID();
        UUID noOutgoingId = UUID.randomUUID();
        UUID missingConfigId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = new ArrayList<>();
        List<TransitionDefinition> transitions = new ArrayList<>();

        states.add(makeState(startId, StateType.API_CALL, "Start", apiCallConfig()));
        states.add(makeState(endId, StateType.END, "End", Collections.emptyMap()));
        transitions.add(makeTransition(startId, endId, null));

        // State with no outgoing transitions (reachable from start)
        states.add(makeState(noOutgoingId, StateType.RESPONSE, "NoOutgoing", responseConfig()));
        transitions.add(makeTransition(startId, noOutgoingId, null));

        // State with missing config (reachable from start, has outgoing)
        states.add(makeState(missingConfigId, StateType.API_CALL, "MissingConfig", Collections.emptyMap()));
        transitions.add(makeTransition(startId, missingConfigId, null));
        transitions.add(makeTransition(missingConfigId, endId, null));

        WorkflowDefinition workflow = makeWorkflow(states, transitions);
        ValidationResult result = engine.validate(workflow);

        assertThat(result.isValid()).isFalse();

        // Should have both NO_OUTGOING_TRANSITION and MISSING_CONFIG errors
        boolean hasNoOutgoing = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.NO_OUTGOING_TRANSITION
                        && noOutgoingId.equals(e.getStateId()));
        boolean hasMissingConfig = result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.MISSING_CONFIG
                        && missingConfigId.equals(e.getStateId()));

        assertThat(hasNoOutgoing).isTrue();
        assertThat(hasMissingConfig).isTrue();
    }
}
