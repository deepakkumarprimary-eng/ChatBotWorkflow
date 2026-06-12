package com.chatbot.workflow.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

class ParallelStateProcessorTest {

    private ParallelStateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ParallelStateProcessor();
    }

    @Test
    void testGetType() {
        assertEquals(StateType.PARALLEL, processor.getType());
    }

    // === All branches succeed → merged output in order ===

    @Test
    void testAllBranchesSucceedMergedOutput() {
        Map<String, Object> branch0Output = new HashMap<>();
        branch0Output.put("key1", "value1");
        branch0Output.put("shared", "from_branch0");

        Map<String, Object> branch1Output = new HashMap<>();
        branch1Output.put("key2", "value2");
        branch1Output.put("shared", "from_branch1");

        StateDefinition state = createParallelState(Arrays.asList(
                createBranch("branch-0", branch0Output),
                createBranch("branch-1", branch1Output)
        ));

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("value1", result.getOutputVariables().get("key1"));
        assertEquals("value2", result.getOutputVariables().get("key2"));
        // Later branch overwrites earlier for conflicting keys
        assertEquals("from_branch1", result.getOutputVariables().get("shared"));
    }

    @Test
    void testThreeBranchesSucceedMergedOutput() {
        Map<String, Object> branch0Output = new HashMap<>();
        branch0Output.put("a", 1);

        Map<String, Object> branch1Output = new HashMap<>();
        branch1Output.put("b", 2);

        Map<String, Object> branch2Output = new HashMap<>();
        branch2Output.put("c", 3);

        StateDefinition state = createParallelState(Arrays.asList(
                createBranch("branch-0", branch0Output),
                createBranch("branch-1", branch1Output),
                createBranch("branch-2", branch2Output)
        ));

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals(1, result.getOutputVariables().get("a"));
        assertEquals(2, result.getOutputVariables().get("b"));
        assertEquals(3, result.getOutputVariables().get("c"));
    }

    // === Output merge order: later branches overwrite earlier ===

    @Test
    void testMergeOrderLaterOverwritesEarlier() {
        Map<String, Object> branch0Output = new HashMap<>();
        branch0Output.put("x", "first");
        branch0Output.put("y", "first");

        Map<String, Object> branch1Output = new HashMap<>();
        branch1Output.put("x", "second");

        Map<String, Object> branch2Output = new HashMap<>();
        branch2Output.put("x", "third");
        branch2Output.put("y", "third");

        StateDefinition state = createParallelState(Arrays.asList(
                createBranch("b0", branch0Output),
                createBranch("b1", branch1Output),
                createBranch("b2", branch2Output)
        ));

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        // branch2 (index 2) should overwrite
        assertEquals("third", result.getOutputVariables().get("x"));
        assertEquals("third", result.getOutputVariables().get("y"));
    }

    // === Branch failure → FAILED with error transition ===

    @Test
    void testBranchFailureReturnsFailed() {
        Map<String, Object> branch0Output = new HashMap<>();
        branch0Output.put("key1", "value1");

        StateDefinition state = createParallelState(Arrays.asList(
                createBranch("branch-0", branch0Output),
                createFailingBranch("branch-1", "Something went wrong")
        ));

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("failed"));
    }

    @Test
    void testFirstBranchFailsCancelsRemaining() {
        StateDefinition state = createParallelState(Arrays.asList(
                createFailingBranch("branch-0", "First branch error"),
                createBranch("branch-1", new HashMap<>()),
                createBranch("branch-2", new HashMap<>())
        ));

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertTrue(result.getErrorMessage().contains("Branch 0"));
    }

    // === Invalid branch count (< 2) → FAILED ===

    @Test
    void testSingleBranchFails() {
        StateDefinition state = createParallelState(Arrays.asList(
                createBranch("branch-0", new HashMap<>())
        ));

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("2"));
    }

    @Test
    void testZeroBranchesFails() {
        StateDefinition state = createParallelState(new ArrayList<>());

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
    }

    // === Invalid branch count (> 10) → FAILED ===

    @Test
    void testElevenBranchesFails() {
        List<Map<String, Object>> branches = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            branches.add(createBranch("branch-" + i, new HashMap<>()));
        }

        StateDefinition state = createParallelState(branches);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("10"));
    }

    // === Missing config → FAILED ===

    @Test
    void testNullConfigFails() {
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.PARALLEL, "ParallelStep", null, null, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testEmptyConfigFails() {
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.PARALLEL, "ParallelStep", null, new HashMap<>(), null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("branches"));
    }

    // === Boundary: exactly 2 branches succeeds ===

    @Test
    void testExactlyTwoBranchesSucceeds() {
        Map<String, Object> out0 = new HashMap<>();
        out0.put("a", "1");
        Map<String, Object> out1 = new HashMap<>();
        out1.put("b", "2");

        StateDefinition state = createParallelState(Arrays.asList(
                createBranch("b0", out0),
                createBranch("b1", out1)
        ));

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("1", result.getOutputVariables().get("a"));
        assertEquals("2", result.getOutputVariables().get("b"));
    }

    // === Boundary: exactly 10 branches succeeds ===

    @Test
    void testExactlyTenBranchesSucceeds() {
        List<Map<String, Object>> branches = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> output = new HashMap<>();
            output.put("key" + i, "value" + i);
            branches.add(createBranch("branch-" + i, output));
        }

        StateDefinition state = createParallelState(branches);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        for (int i = 0; i < 10; i++) {
            assertEquals("value" + i, result.getOutputVariables().get("key" + i));
        }
    }

    // === Branches with no output variables produce empty merged output ===

    @Test
    void testBranchesWithNoOutputProduceEmptyMerge() {
        StateDefinition state = createParallelState(Arrays.asList(
                createBranch("b0", new HashMap<>()),
                createBranch("b1", new HashMap<>())
        ));

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.getOutputVariables().isEmpty());
    }

    // === Helpers ===

    private StateDefinition createParallelState(List<Map<String, Object>> branches) {
        Map<String, Object> config = new HashMap<>();
        config.put("branches", branches);
        return new StateDefinition(
                UUID.randomUUID(), StateType.PARALLEL, "ParallelStep", null, config, null, null);
    }

    private Map<String, Object> createBranch(String name, Map<String, Object> outputVariables) {
        Map<String, Object> branch = new HashMap<>();
        branch.put("name", name);
        branch.put("outputVariables", outputVariables);
        // Include minimal states/transitions for structure (not executed in simplified model)
        branch.put("states", new ArrayList<>());
        branch.put("transitions", new ArrayList<>());
        return branch;
    }

    private Map<String, Object> createFailingBranch(String name, String errorMessage) {
        Map<String, Object> branch = new HashMap<>();
        branch.put("name", name);
        branch.put("simulateFailure", true);
        branch.put("errorMessage", errorMessage);
        branch.put("states", new ArrayList<>());
        branch.put("transitions", new ArrayList<>());
        return branch;
    }

    private ExecutionContext context() {
        return new ExecutionContext(UUID.randomUUID(), new HashMap<>());
    }
}
