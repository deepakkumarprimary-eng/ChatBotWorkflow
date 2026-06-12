package com.chatbot.workflow.engine;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

class WaitStateProcessorTest {

    private WaitStateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WaitStateProcessor();
    }

    @Test
    void testGetType() {
        assertEquals(StateType.WAIT, processor.getType());
    }

    // === Valid duration pauses execution ===

    @Test
    void testValidDurationPausesExecution() {
        StateDefinition state = createWaitState(60);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.isPaused());
    }

    @Test
    void testValidDurationMediumValue() {
        StateDefinition state = createWaitState(3600);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.isPaused());
    }

    // === Stores duration and start time in output ===

    @Test
    void testStoresDurationInOutput() {
        StateDefinition state = createWaitState(120);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(120, result.getOutputVariables().get("_waitDuration"));
    }

    @Test
    void testStoresStartTimeInOutput() {
        Instant before = Instant.now();

        StateDefinition state = createWaitState(60);
        StateProcessorResult result = processor.process(state, context());

        Instant after = Instant.now();

        Object startTimeObj = result.getOutputVariables().get("_waitStartTime");
        assertNotNull(startTimeObj);
        Instant startTime = Instant.parse(startTimeObj.toString());
        assertFalse(startTime.isBefore(before));
        assertFalse(startTime.isAfter(after));
    }

    // === Missing duration → FAILED ===

    @Test
    void testMissingDurationFails() {
        Map<String, Object> config = new HashMap<>();
        // No duration field
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.WAIT, "WaitStep", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("duration"));
    }

    @Test
    void testNullConfigFails() {
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.WAIT, "WaitStep", null, null, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    // === Duration below 1 → FAILED ===

    @Test
    void testDurationBelowMinFails() {
        StateDefinition state = createWaitState(0);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("duration"));
    }

    @Test
    void testNegativeDurationFails() {
        StateDefinition state = createWaitState(-10);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    // === Duration above 86400 → FAILED ===

    @Test
    void testDurationAboveMaxFails() {
        StateDefinition state = createWaitState(86401);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("duration"));
    }

    @Test
    void testVeryLargeDurationFails() {
        StateDefinition state = createWaitState(100000);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
    }

    // === Non-numeric duration → FAILED ===

    @Test
    void testNonNumericDurationFails() {
        Map<String, Object> config = new HashMap<>();
        config.put("duration", "not-a-number");
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.WAIT, "WaitStep", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("non-numeric"));
    }

    @Test
    void testFloatingPointDurationFails() {
        Map<String, Object> config = new HashMap<>();
        config.put("duration", "30.5");
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.WAIT, "WaitStep", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
    }

    // === Boundary values (1 and 86400) → valid ===

    @Test
    void testMinBoundaryValueSucceeds() {
        StateDefinition state = createWaitState(1);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.isPaused());
        assertEquals(1, result.getOutputVariables().get("_waitDuration"));
    }

    @Test
    void testMaxBoundaryValueSucceeds() {
        StateDefinition state = createWaitState(86400);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.isPaused());
        assertEquals(86400, result.getOutputVariables().get("_waitDuration"));
    }

    // === Helpers ===

    private StateDefinition createWaitState(int duration) {
        Map<String, Object> config = new HashMap<>();
        config.put("duration", duration);
        return new StateDefinition(
                UUID.randomUUID(), StateType.WAIT, "WaitStep", null, config, null, null);
    }

    private ExecutionContext context() {
        return new ExecutionContext(UUID.randomUUID(), new HashMap<>());
    }
}
