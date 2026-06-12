package com.chatbot.workflow.engine;

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

class InputStateProcessorTest {

    private InputStateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InputStateProcessor();
    }

    @Test
    void testGetType() {
        assertEquals(StateType.INPUT, processor.getType());
    }

    // === Valid config pauses execution ===

    @Test
    void testValidConfigPausesExecution() {
        StateDefinition state = createInputState("Enter your name:", "userName", 300);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.isPaused());
    }

    @Test
    void testValidConfigWithCustomTimeout() {
        StateDefinition state = createInputState("Enter age:", "userAge", 60);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.isPaused());
        assertEquals(60, result.getOutputVariables().get("_inputTimeout"));
    }

    // === Stores prompt and variable name in output ===

    @Test
    void testStoresPromptInOutput() {
        StateDefinition state = createInputState("What is your email?", "userEmail", 300);

        StateProcessorResult result = processor.process(state, context());

        assertEquals("What is your email?", result.getOutputVariables().get("_inputPrompt"));
    }

    @Test
    void testStoresVariableNameInOutput() {
        StateDefinition state = createInputState("Enter your name:", "userName", 300);

        StateProcessorResult result = processor.process(state, context());

        assertEquals("userName", result.getOutputVariables().get("_inputVariableName"));
    }

    @Test
    void testStoresTimeoutInOutput() {
        StateDefinition state = createInputState("Enter code:", "verificationCode", 120);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(120, result.getOutputVariables().get("_inputTimeout"));
    }

    // === Missing prompt → FAILED ===

    @Test
    void testMissingPromptFails() {
        Map<String, Object> config = new HashMap<>();
        config.put("variableName", "userName");
        config.put("timeout", 300);
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.INPUT, "GetInput", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("prompt"));
    }

    @Test
    void testEmptyPromptFails() {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "");
        config.put("variableName", "userName");
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.INPUT, "GetInput", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    // === Missing variableName → FAILED ===

    @Test
    void testMissingVariableNameFails() {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "Enter something:");
        config.put("timeout", 300);
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.INPUT, "GetInput", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("variableName"));
    }

    @Test
    void testEmptyVariableNameFails() {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "Enter something:");
        config.put("variableName", "");
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.INPUT, "GetInput", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    // === Default timeout when not specified ===

    @Test
    void testDefaultTimeoutWhenNotSpecified() {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "Enter your name:");
        config.put("variableName", "userName");
        // No timeout configured
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.INPUT, "GetInput", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.isPaused());
        assertEquals(300, result.getOutputVariables().get("_inputTimeout"));
    }

    @Test
    void testInvalidTimeoutUsesDefault() {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "Enter your name:");
        config.put("variableName", "userName");
        config.put("timeout", "not-a-number");
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.INPUT, "GetInput", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertTrue(result.isPaused());
        assertEquals(300, result.getOutputVariables().get("_inputTimeout"));
    }

    // === Null config → FAILED ===

    @Test
    void testNullConfigFails() {
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.INPUT, "GetInput", null, null, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertFalse(result.isPaused());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    // === Helpers ===

    private StateDefinition createInputState(String prompt, String variableName, int timeout) {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", prompt);
        config.put("variableName", variableName);
        config.put("timeout", timeout);
        return new StateDefinition(
                UUID.randomUUID(), StateType.INPUT, "GetInput", null, config, null, null);
    }

    private ExecutionContext context() {
        return new ExecutionContext(UUID.randomUUID(), new HashMap<>());
    }
}
