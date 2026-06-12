package com.chatbot.workflow.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatbot.workflow.model.Position;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.TransitionDefinition;
import com.chatbot.workflow.model.WorkflowDefinition;

/**
 * Unit tests for the ValidationEngine service.
 */
class ValidationEngineTest {

    private ValidationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ValidationEngine();
    }

    // ===== Rule 1: Empty workflow =====

    @Test
    void validate_emptyWorkflow_nullStates() {
        WorkflowDefinition workflow = new WorkflowDefinition(null, null, null, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Workflow is empty", result.getErrors().get(0).getMessage());
        assertEquals(ValidationErrorType.EMPTY_WORKFLOW, result.getErrors().get(0).getErrorType());
        assertNull(result.getErrors().get(0).getStateId());
    }

    @Test
    void validate_emptyWorkflow_emptyList() {
        WorkflowDefinition workflow = new WorkflowDefinition(null, Collections.emptyList(), null, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Workflow is empty", result.getErrors().get(0).getMessage());
        assertEquals(ValidationErrorType.EMPTY_WORKFLOW, result.getErrors().get(0).getErrorType());
    }

    // ===== Rule 2: Single start state =====

    @Test
    void validate_singleStartState_valid() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hello")),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validate_noStartState_allHaveIncoming() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(id1, StateType.RESPONSE, "State1", configWith("template", "hi")),
                createState(id2, StateType.END, "State2", null)
        );
        // Both have incoming: creates a cycle scenario
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(id1, id2, null),
                createTransition(id2, id1, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().equals("No start state found")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.NO_START_STATE));
    }

    @Test
    void validate_multipleStartStates() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(id1, StateType.RESPONSE, "Start1", configWith("template", "hi")),
                createState(id2, StateType.RESPONSE, "Start2", configWith("template", "hello")),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(id1, endId, null),
                createTransition(id2, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("Multiple start states found")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.MULTIPLE_START_STATES));
    }

    // ===== Rule 3: Outgoing transitions for non-END states =====

    @Test
    void validate_nonEndStateWithoutOutgoing() {
        UUID startId = UUID.randomUUID();
        UUID deadEndId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(deadEndId, StateType.RESPONSE, "DeadEnd", configWith("template", "stuck")),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, deadEndId, null),
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("DeadEnd") && e.getMessage().contains("no outgoing")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.NO_OUTGOING_TRANSITION));
    }

    @Test
    void validate_endStateWithoutOutgoing_valid() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
    }

    // ===== Rule 4: Condition state structure =====

    @Test
    void validate_conditionState_validTrueFalse() {
        UUID startId = UUID.randomUUID();
        UUID condId = UUID.randomUUID();
        UUID trueId = UUID.randomUUID();
        UUID falseId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(condId, StateType.CONDITION, "Check", configWith("expression", "x > 5")),
                createState(trueId, StateType.END, "TrueEnd", null),
                createState(falseId, StateType.END, "FalseEnd", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, condId, null),
                createTransition(condId, trueId, "true"),
                createTransition(condId, falseId, "false")
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
    }

    @Test
    void validate_conditionState_wrongTransitionCount() {
        UUID startId = UUID.randomUUID();
        UUID condId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(condId, StateType.CONDITION, "Check", configWith("expression", "x > 5")),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, condId, null),
                createTransition(condId, endId, "true")
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("must have exactly 2")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.CONDITION_TRANSITIONS));
    }

    @Test
    void validate_conditionState_missingLabels() {
        UUID startId = UUID.randomUUID();
        UUID condId = UUID.randomUUID();
        UUID end1 = UUID.randomUUID();
        UUID end2 = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(condId, StateType.CONDITION, "Check", configWith("expression", "x > 5")),
                createState(end1, StateType.END, "End1", null),
                createState(end2, StateType.END, "End2", null)
        );
        // Two transitions but not labeled true/false
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, condId, null),
                createTransition(condId, end1, "true"),
                createTransition(condId, end2, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("labeled 'true' and 'false'")));
    }

    // ===== Rule 5: Reachability =====

    @Test
    void validate_unreachableState() {
        UUID startId = UUID.randomUUID();
        UUID middleId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();
        UUID isolatedId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(middleId, StateType.RESPONSE, "Middle", configWith("template", "mid")),
                createState(endId, StateType.END, "End", null),
                createState(isolatedId, StateType.END, "Isolated", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null),
                createTransition(middleId, isolatedId, null),
                createTransition(isolatedId, middleId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("Isolated") && e.getMessage().contains("not reachable")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("Middle") && e.getMessage().contains("not reachable")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.UNREACHABLE_STATE));
    }

    @Test
    void validate_allReachable_linearChain() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(id1, StateType.RESPONSE, "First", configWith("template", "1")),
                createState(id2, StateType.RESPONSE, "Second", configWith("template", "2")),
                createState(id3, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(id1, id2, null),
                createTransition(id2, id3, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
    }

    // ===== Rule 6: Required configuration fields =====

    @Test
    void validate_apiCall_missingMethod() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        config.put("url", "http://example.com");

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.API_CALL, "CallAPI", config),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("method")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.MISSING_CONFIG));
    }

    @Test
    void validate_apiCall_missingUrl() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.API_CALL, "CallAPI", config),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("url")));
    }

    @Test
    void validate_apiCall_valid() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        config.put("method", "GET");
        config.put("url", "http://example.com/api");

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.API_CALL, "CallAPI", config),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
    }

    @Test
    void validate_conditionState_missingExpression() {
        UUID startId = UUID.randomUUID();
        UUID condId = UUID.randomUUID();
        UUID trueEnd = UUID.randomUUID();
        UUID falseEnd = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(condId, StateType.CONDITION, "Check", new HashMap<>()),
                createState(trueEnd, StateType.END, "TrueEnd", null),
                createState(falseEnd, StateType.END, "FalseEnd", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, condId, null),
                createTransition(condId, trueEnd, "true"),
                createTransition(condId, falseEnd, "false")
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("expression")));
    }

    @Test
    void validate_responseState_missingTemplate() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Respond", new HashMap<>()),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("template")));
    }

    @Test
    void validate_inputState_missingPromptAndVariable() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.INPUT, "GetInput", new HashMap<>()),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("prompt")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("variableName")));
    }

    @Test
    void validate_inputState_valid() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "What is your name?");
        config.put("variableName", "userName");

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.INPUT, "GetInput", config),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
    }

    @Test
    void validate_waitState_validDuration() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.WAIT, "Wait5", configWith("duration", 5)),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
    }

    @Test
    void validate_waitState_durationTooLow() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.WAIT, "Wait0", configWith("duration", 0)),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("between 1 and 86400")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getErrorType() == ValidationErrorType.INVALID_CONFIG));
    }

    @Test
    void validate_waitState_durationTooHigh() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.WAIT, "WaitTooLong", configWith("duration", 86401)),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("between 1 and 86400")));
    }

    @Test
    void validate_waitState_missingDuration() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.WAIT, "WaitNoDuration", new HashMap<>()),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("duration")));
    }

    @Test
    void validate_waitState_boundaryValues() {
        // Duration = 1 (min valid)
        UUID startId1 = UUID.randomUUID();
        UUID endId1 = UUID.randomUUID();
        List<StateDefinition> states1 = Arrays.asList(
                createState(startId1, StateType.WAIT, "WaitMin", configWith("duration", 1)),
                createState(endId1, StateType.END, "End", null)
        );
        WorkflowDefinition workflow1 = new WorkflowDefinition(null, states1,
                Arrays.asList(createTransition(startId1, endId1, null)), null);
        assertTrue(engine.validate(workflow1).isValid());

        // Duration = 86400 (max valid)
        UUID startId2 = UUID.randomUUID();
        UUID endId2 = UUID.randomUUID();
        List<StateDefinition> states2 = Arrays.asList(
                createState(startId2, StateType.WAIT, "WaitMax", configWith("duration", 86400)),
                createState(endId2, StateType.END, "End", null)
        );
        WorkflowDefinition workflow2 = new WorkflowDefinition(null, states2,
                Arrays.asList(createTransition(startId2, endId2, null)), null);
        assertTrue(engine.validate(workflow2).isValid());
    }

    @Test
    void validate_parallelState_validBranches() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        config.put("branches", Arrays.asList("branch1", "branch2", "branch3"));

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.PARALLEL, "Parallel", config),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
    }

    @Test
    void validate_parallelState_tooFewBranches() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        config.put("branches", Arrays.asList("only-one"));

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.PARALLEL, "Parallel", config),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("between 2 and 10")));
    }

    @Test
    void validate_parallelState_tooManyBranches() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        List<String> branches = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            branches.add("branch" + i);
        }
        config.put("branches", branches);

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.PARALLEL, "Parallel", config),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("between 2 and 10")));
    }

    @Test
    void validate_parallelState_missingBranches() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.PARALLEL, "Parallel", new HashMap<>()),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("branches")));
    }

    @Test
    void validate_parallelState_branchesNotAList() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        config.put("branches", "not-a-list");

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.PARALLEL, "Parallel", config),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("must be a list")));
    }

    // ===== Rule: Collect all errors simultaneously =====

    @Test
    void validate_multipleErrors_allCollected() {
        UUID startId = UUID.randomUUID();
        UUID condId = UUID.randomUUID();
        UUID isolatedId = UUID.randomUUID();

        // Start: missing template, Condition: missing expression, Isolated: unreachable + no outgoing
        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", new HashMap<>()),
                createState(condId, StateType.CONDITION, "Check", new HashMap<>()),
                createState(isolatedId, StateType.RESPONSE, "Isolated", configWith("template", "hi"))
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, condId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        // Should have multiple errors: missing template, missing expression,
        // condition wrong transitions, isolated no outgoing, unreachable, multiple start states
        assertTrue(result.getErrors().size() >= 4,
                "Expected at least 4 errors but got " + result.getErrors().size() + ": " + result.getErrors());
    }

    @Test
    void validate_errorsIncludeStateId() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", new HashMap<>()),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        // The missing template error should reference the start state's ID
        ValidationError configError = result.getErrors().stream()
                .filter(e -> e.getMessage().contains("template"))
                .findFirst()
                .orElse(null);
        assertNotNull(configError);
        assertEquals(startId, configError.getStateId());
    }

    @Test
    void validate_nullConfig_treatedAsMissing() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        // null config instead of empty map
        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.API_CALL, "Call", null),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("method")));
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("url")));
    }

    @Test
    void validate_endState_noConfigRequired() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(endId, StateType.END, "End", null)
        );
        List<TransitionDefinition> transitions = Arrays.asList(
                createTransition(startId, endId, null)
        );

        WorkflowDefinition workflow = new WorkflowDefinition(null, states, transitions, null);
        ValidationResult result = engine.validate(workflow);

        assertTrue(result.isValid());
    }

    @Test
    void validate_nullTransitions_treatedAsEmpty() {
        UUID startId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        List<StateDefinition> states = Arrays.asList(
                createState(startId, StateType.RESPONSE, "Start", configWith("template", "hi")),
                createState(endId, StateType.END, "End", null)
        );

        // null transitions
        WorkflowDefinition workflow = new WorkflowDefinition(null, states, null, null);
        ValidationResult result = engine.validate(workflow);

        assertFalse(result.isValid());
        // Start has no outgoing transitions and multiple start states (both have no incoming)
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("no outgoing") ||
                        e.getMessage().contains("Multiple start states")));
    }

    // ===== Helper methods =====

    private StateDefinition createState(UUID id, StateType type, String name, Map<String, Object> config) {
        return new StateDefinition(id, type, name, new Position(0, 0), config, null, null);
    }

    private TransitionDefinition createTransition(UUID source, UUID target, String condition) {
        return new TransitionDefinition(UUID.randomUUID(), source, target, condition);
    }

    private Map<String, Object> configWith(String key, Object value) {
        Map<String, Object> config = new HashMap<>();
        config.put(key, value);
        return config;
    }
}
