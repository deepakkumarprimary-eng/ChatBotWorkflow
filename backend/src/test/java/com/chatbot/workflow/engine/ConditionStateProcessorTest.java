package com.chatbot.workflow.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

class ConditionStateProcessorTest {

    private ConditionStateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ConditionStateProcessor();
    }

    @Test
    void testGetType() {
        assertEquals(StateType.CONDITION, processor.getType());
    }

    // === Simple comparisons ===

    @Test
    void testSimpleGreaterThan_true() {
        ExecutionContext context = createContext("age", 25);
        StateDefinition state = createConditionState("age > 18");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testSimpleGreaterThan_false() {
        ExecutionContext context = createContext("age", 15);
        StateDefinition state = createConditionState("age > 18");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("false", result.getNextTransitionCondition());
    }

    @Test
    void testLessThan() {
        ExecutionContext context = createContext("count", 5);
        StateDefinition state = createConditionState("count < 10");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testGreaterThanOrEqual() {
        ExecutionContext context = createContext("score", 90);
        StateDefinition state = createConditionState("score >= 90");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testLessThanOrEqual() {
        ExecutionContext context = createContext("value", 100);
        StateDefinition state = createConditionState("value <= 100");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    // === String comparisons ===

    @Test
    void testStringEquals_true() {
        ExecutionContext context = createContext("status", "active");
        StateDefinition state = createConditionState("status == \"active\"");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testStringEquals_false() {
        ExecutionContext context = createContext("status", "inactive");
        StateDefinition state = createConditionState("status == \"active\"");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("false", result.getNextTransitionCondition());
    }

    @Test
    void testStringNotEquals() {
        ExecutionContext context = createContext("status", "inactive");
        StateDefinition state = createConditionState("status != \"active\"");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    // === Logical AND ===

    @Test
    void testLogicalAnd_bothTrue() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("age", 25);
        vars.put("status", "active");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), vars);
        StateDefinition state = createConditionState("age > 18 AND status == \"active\"");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testLogicalAnd_oneFalse() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("age", 15);
        vars.put("status", "active");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), vars);
        StateDefinition state = createConditionState("age > 18 AND status == \"active\"");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("false", result.getNextTransitionCondition());
    }

    // === Logical OR ===

    @Test
    void testLogicalOr_firstTrue() {
        ExecutionContext context = createContext("role", "admin");
        StateDefinition state = createConditionState("role == \"admin\" OR role == \"superadmin\"");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testLogicalOr_secondTrue() {
        ExecutionContext context = createContext("role", "superadmin");
        StateDefinition state = createConditionState("role == \"admin\" OR role == \"superadmin\"");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testLogicalOr_bothFalse() {
        ExecutionContext context = createContext("role", "user");
        StateDefinition state = createConditionState("role == \"admin\" OR role == \"superadmin\"");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("false", result.getNextTransitionCondition());
    }

    // === NOT ===

    @Test
    void testNot_negateTrue() {
        ExecutionContext context = createContext("isBlocked", true);
        StateDefinition state = createConditionState("NOT isBlocked");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("false", result.getNextTransitionCondition());
    }

    @Test
    void testNot_negateFalse() {
        ExecutionContext context = createContext("isBlocked", false);
        StateDefinition state = createConditionState("NOT isBlocked");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testNotWithComparison() {
        ExecutionContext context = createContext("age", 25);
        StateDefinition state = createConditionState("NOT age < 18");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    // === Null handling ===

    @Test
    void testUndefinedVariableIsNull() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        StateDefinition state = createConditionState("missingVar == null");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testUndefinedVarNotEqualToValue() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        StateDefinition state = createConditionState("missingVar != null");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("false", result.getNextTransitionCondition());
    }

    @Test
    void testDefinedVarNotNull() {
        ExecutionContext context = createContext("name", "John");
        StateDefinition state = createConditionState("name != null");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    // === Parentheses ===

    @Test
    void testParentheses() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 1);
        vars.put("b", 2);
        vars.put("c", 3);
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), vars);
        // Without parens: a == 1 AND (b == 2 OR c == 99) → true AND (true OR false) → true
        StateDefinition state = createConditionState("a == 1 AND (b == 2 OR c == 99)");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    // === Error handling ===

    @Test
    void testMissingExpression() {
        Map<String, Object> config = new HashMap<>();
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.CONDITION, "Check", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
    }

    @Test
    void testNullConfig() {
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.CONDITION, "Check", null, null, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
    }

    @Test
    void testInvalidExpression() {
        ExecutionContext context = createContext("x", 1);
        StateDefinition state = createConditionState("x ++ 5");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testUnterminatedString() {
        ExecutionContext context = createContext("x", "hello");
        StateDefinition state = createConditionState("x == \"hello");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
    }

    // === Decimal numbers ===

    @Test
    void testDecimalComparison() {
        ExecutionContext context = createContext("price", 9.99);
        StateDefinition state = createConditionState("price < 10.0");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    // === Boolean literals ===

    @Test
    void testBooleanLiteralTrue() {
        ExecutionContext context = createContext("flag", true);
        StateDefinition state = createConditionState("flag == true");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    @Test
    void testBooleanLiteralFalse() {
        ExecutionContext context = createContext("flag", false);
        StateDefinition state = createConditionState("flag == false");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("true", result.getNextTransitionCondition());
    }

    // === Helpers ===

    private StateDefinition createConditionState(String expression) {
        Map<String, Object> config = new HashMap<>();
        config.put("expression", expression);
        return new StateDefinition(
                UUID.randomUUID(), StateType.CONDITION, "Condition", null, config, null, null);
    }

    private ExecutionContext createContext(String varName, Object value) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(varName, value);
        return new ExecutionContext(UUID.randomUUID(), vars);
    }

    private ExecutionContext context() {
        return new ExecutionContext(UUID.randomUUID(), new HashMap<>());
    }
}
