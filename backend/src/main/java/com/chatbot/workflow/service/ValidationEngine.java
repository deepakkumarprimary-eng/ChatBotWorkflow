package com.chatbot.workflow.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.TransitionDefinition;
import com.chatbot.workflow.model.WorkflowDefinition;

/**
 * Validation engine for workflow definitions.
 * Performs graph-based validation using BFS traversal to ensure
 * workflow structural correctness before execution.
 * <p>
 * All validation checks are applied simultaneously — errors are collected
 * and returned together rather than stopping at the first failure.
 */
@Service
public class ValidationEngine {

    /**
     * Validates a workflow definition and returns all errors found.
     * Validation does not stop at the first error — all checks are performed
     * and all errors are collected simultaneously.
     *
     * @param workflow the workflow definition to validate
     * @return ValidationResult containing validity flag and all errors
     */
    public ValidationResult validate(WorkflowDefinition workflow) {
        List<ValidationError> errors = new ArrayList<>();

        // Rule 1: Empty workflow check
        if (workflow.getStates() == null || workflow.getStates().isEmpty()) {
            errors.add(ValidationError.workflowError("Workflow is empty", ValidationErrorType.EMPTY_WORKFLOW));
            return ValidationResult.failure(errors);
        }

        List<StateDefinition> states = workflow.getStates();
        List<TransitionDefinition> transitions = workflow.getTransitions() != null
                ? workflow.getTransitions()
                : Collections.emptyList();

        // Compute incoming and outgoing transitions per state
        Set<UUID> statesWithIncoming = new HashSet<>();
        Map<UUID, List<TransitionDefinition>> outgoingByState = new HashMap<>();
        for (TransitionDefinition transition : transitions) {
            statesWithIncoming.add(transition.getTarget());
            outgoingByState
                    .computeIfAbsent(transition.getSource(), k -> new ArrayList<>())
                    .add(transition);
        }

        // Rule 2: Single start state (no incoming transitions)
        List<StateDefinition> startStates = states.stream()
                .filter(s -> !statesWithIncoming.contains(s.getId()))
                .collect(Collectors.toList());

        StateDefinition startState = null;
        if (startStates.isEmpty()) {
            errors.add(ValidationError.workflowError("No start state found", ValidationErrorType.NO_START_STATE));
        } else if (startStates.size() > 1) {
            String ids = startStates.stream()
                    .map(s -> s.getId().toString())
                    .collect(Collectors.joining(", "));
            errors.add(ValidationError.workflowError(
                    "Multiple start states found: " + ids, ValidationErrorType.MULTIPLE_START_STATES));
        } else {
            startState = startStates.get(0);
        }

        // Rule 3: Every non-END state must have at least one outgoing transition
        for (StateDefinition state : states) {
            if (state.getType() != StateType.END) {
                List<TransitionDefinition> outgoing = outgoingByState.get(state.getId());
                if (outgoing == null || outgoing.isEmpty()) {
                    errors.add(ValidationError.stateError(
                            state.getId(),
                            state.getName(),
                            "State '" + state.getName() + "' has no outgoing transitions",
                            ValidationErrorType.NO_OUTGOING_TRANSITION));
                }
            }
        }

        // Rule 4: Condition state must have exactly 2 outgoing transitions (true/false)
        for (StateDefinition state : states) {
            if (state.getType() == StateType.CONDITION) {
                List<TransitionDefinition> outgoing = outgoingByState.get(state.getId());
                if (outgoing == null) {
                    // Already reported as missing outgoing in Rule 3
                    continue;
                }
                if (outgoing.size() != 2) {
                    errors.add(ValidationError.stateError(
                            state.getId(),
                            state.getName(),
                            "Condition state '" + state.getName() + "' must have exactly 2 outgoing transitions, found " + outgoing.size(),
                            ValidationErrorType.CONDITION_TRANSITIONS));
                } else {
                    boolean hasTrue = outgoing.stream()
                            .anyMatch(t -> "true".equals(t.getCondition()));
                    boolean hasFalse = outgoing.stream()
                            .anyMatch(t -> "false".equals(t.getCondition()));
                    if (!hasTrue || !hasFalse) {
                        errors.add(ValidationError.stateError(
                                state.getId(),
                                state.getName(),
                                "Condition state '" + state.getName() + "' must have transitions labeled 'true' and 'false'",
                                ValidationErrorType.CONDITION_TRANSITIONS));
                    }
                }
            }
        }

        // Rule 5: Reachability — BFS from start state
        if (startState != null) {
            Set<UUID> reachable = bfsReachable(startState.getId(), outgoingByState);
            for (StateDefinition state : states) {
                if (!reachable.contains(state.getId())) {
                    errors.add(ValidationError.stateError(
                            state.getId(),
                            state.getName(),
                            "State '" + state.getName() + "' is not reachable from the start state",
                            ValidationErrorType.UNREACHABLE_STATE));
                }
            }
        }

        // Rule 6: Required configuration fields per state type
        for (StateDefinition state : states) {
            validateStateConfig(state, errors);
        }

        if (errors.isEmpty()) {
            return ValidationResult.success();
        }
        return ValidationResult.failure(errors);
    }

    /**
     * Performs BFS from the given start state and returns all reachable state IDs.
     */
    private Set<UUID> bfsReachable(UUID startId, Map<UUID, List<TransitionDefinition>> outgoingByState) {
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(startId);
        visited.add(startId);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            List<TransitionDefinition> outgoing = outgoingByState.get(current);
            if (outgoing != null) {
                for (TransitionDefinition transition : outgoing) {
                    if (visited.add(transition.getTarget())) {
                        queue.add(transition.getTarget());
                    }
                }
            }
        }
        return visited;
    }

    /**
     * Validates that a state's config map contains all required fields for its type.
     */
    private void validateStateConfig(StateDefinition state, List<ValidationError> errors) {
        Map<String, Object> config = state.getConfig();
        StateType type = state.getType();

        switch (type) {
            case API_CALL:
                validateRequiredField(state, config, "method", errors);
                validateRequiredField(state, config, "url", errors);
                break;
            case CONDITION:
                validateRequiredField(state, config, "expression", errors);
                break;
            case RESPONSE:
                validateRequiredField(state, config, "template", errors);
                break;
            case INPUT:
                validateRequiredField(state, config, "prompt", errors);
                validateRequiredField(state, config, "variableName", errors);
                break;
            case WAIT:
                validateWaitDuration(state, config, errors);
                break;
            case PARALLEL:
                validateParallelBranches(state, config, errors);
                break;
            case END:
                // No required config
                break;
        }
    }

    private void validateRequiredField(StateDefinition state, Map<String, Object> config,
                                       String fieldName, List<ValidationError> errors) {
        if (config == null || !config.containsKey(fieldName) || config.get(fieldName) == null) {
            errors.add(ValidationError.stateError(
                    state.getId(),
                    state.getName(),
                    "State '" + state.getName() + "' is missing required config field '" + fieldName + "'",
                    ValidationErrorType.MISSING_CONFIG));
        }
    }

    private void validateWaitDuration(StateDefinition state, Map<String, Object> config,
                                      List<ValidationError> errors) {
        if (config == null || !config.containsKey("duration")) {
            errors.add(ValidationError.stateError(
                    state.getId(),
                    state.getName(),
                    "State '" + state.getName() + "' is missing required config field 'duration'",
                    ValidationErrorType.MISSING_CONFIG));
            return;
        }

        Object durationObj = config.get("duration");
        if (durationObj == null) {
            errors.add(ValidationError.stateError(
                    state.getId(),
                    state.getName(),
                    "State '" + state.getName() + "' is missing required config field 'duration'",
                    ValidationErrorType.MISSING_CONFIG));
            return;
        }

        int duration;
        try {
            if (durationObj instanceof Number) {
                duration = ((Number) durationObj).intValue();
            } else {
                duration = Integer.parseInt(durationObj.toString());
            }
        } catch (NumberFormatException e) {
            errors.add(ValidationError.stateError(
                    state.getId(),
                    state.getName(),
                    "State '" + state.getName() + "' config field 'duration' must be an integer",
                    ValidationErrorType.INVALID_CONFIG));
            return;
        }

        if (duration < 1 || duration > 86400) {
            errors.add(ValidationError.stateError(
                    state.getId(),
                    state.getName(),
                    "State '" + state.getName() + "' config field 'duration' must be between 1 and 86400",
                    ValidationErrorType.INVALID_CONFIG));
        }
    }

    private void validateParallelBranches(StateDefinition state, Map<String, Object> config,
                                          List<ValidationError> errors) {
        if (config == null || !config.containsKey("branches") || config.get("branches") == null) {
            errors.add(ValidationError.stateError(
                    state.getId(),
                    state.getName(),
                    "State '" + state.getName() + "' is missing required config field 'branches'",
                    ValidationErrorType.MISSING_CONFIG));
            return;
        }

        Object branchesObj = config.get("branches");
        if (!(branchesObj instanceof List)) {
            errors.add(ValidationError.stateError(
                    state.getId(),
                    state.getName(),
                    "State '" + state.getName() + "' config field 'branches' must be a list",
                    ValidationErrorType.INVALID_CONFIG));
            return;
        }

        List<?> branches = (List<?>) branchesObj;
        if (branches.size() < 2 || branches.size() > 10) {
            errors.add(ValidationError.stateError(
                    state.getId(),
                    state.getName(),
                    "State '" + state.getName() + "' config field 'branches' must have between 2 and 10 items",
                    ValidationErrorType.INVALID_CONFIG));
        }
    }
}
