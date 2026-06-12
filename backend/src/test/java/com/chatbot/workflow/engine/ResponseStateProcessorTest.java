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

class ResponseStateProcessorTest {

    private ResponseStateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ResponseStateProcessor();
    }

    @Test
    void testGetType() {
        assertEquals(StateType.RESPONSE, processor.getType());
    }

    // === Template with variables → correct interpolation ===

    @Test
    void testTemplateWithSingleVariable() {
        ExecutionContext context = createContext("userName", "Alice");
        StateDefinition state = createResponseState("Hello {{userName}}!");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("Hello Alice!", result.getOutputVariables().get("_responseMessage"));
    }

    @Test
    void testTemplateWithMultipleVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", "Bob");
        vars.put("orderId", "ORD-12345");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), vars);
        StateDefinition state = createResponseState("Hello {{userName}}, your order {{orderId}} is confirmed.");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("Hello Bob, your order ORD-12345 is confirmed.",
                result.getOutputVariables().get("_responseMessage"));
    }

    @Test
    void testTemplateWithNumericVariable() {
        ExecutionContext context = createContext("count", 42);
        StateDefinition state = createResponseState("You have {{count}} items.");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("You have 42 items.", result.getOutputVariables().get("_responseMessage"));
    }

    @Test
    void testTemplateWithRepeatedVariable() {
        ExecutionContext context = createContext("name", "Eve");
        StateDefinition state = createResponseState("{{name}} said hello to {{name}}.");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("Eve said hello to Eve.", result.getOutputVariables().get("_responseMessage"));
    }

    // === Template with undefined variable → "null" replacement ===

    @Test
    void testTemplateWithUndefinedVariable() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        StateDefinition state = createResponseState("Hello {{unknownVar}}!");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("Hello null!", result.getOutputVariables().get("_responseMessage"));
    }

    @Test
    void testTemplateWithMixOfDefinedAndUndefinedVariables() {
        ExecutionContext context = createContext("userName", "Charlie");
        StateDefinition state = createResponseState("Hello {{userName}}, order {{orderId}} status.");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("Hello Charlie, order null status.",
                result.getOutputVariables().get("_responseMessage"));
    }

    // === Template with no variables → unchanged ===

    @Test
    void testTemplateWithNoVariables() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        StateDefinition state = createResponseState("This is a plain message with no variables.");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("This is a plain message with no variables.",
                result.getOutputVariables().get("_responseMessage"));
    }

    @Test
    void testEmptyTemplate() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        StateDefinition state = createResponseState("");

        StateProcessorResult result = processor.process(state, context);

        assertEquals(StateOutcome.SUCCEEDED, result.getOutcome());
        assertEquals("", result.getOutputVariables().get("_responseMessage"));
    }

    // === Missing template in config → FAILED ===

    @Test
    void testMissingTemplateInConfig() {
        Map<String, Object> config = new HashMap<>();
        // No "template" key
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.RESPONSE, "SendMsg", null, config, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testNullConfig() {
        StateDefinition state = new StateDefinition(
                UUID.randomUUID(), StateType.RESPONSE, "SendMsg", null, null, null, null);

        StateProcessorResult result = processor.process(state, context());

        assertEquals(StateOutcome.FAILED, result.getOutcome());
        assertEquals("error", result.getNextTransitionCondition());
        assertNotNull(result.getErrorMessage());
    }

    // === Helpers ===

    private StateDefinition createResponseState(String template) {
        Map<String, Object> config = new HashMap<>();
        config.put("template", template);
        return new StateDefinition(
                UUID.randomUUID(), StateType.RESPONSE, "Response", null, config, null, null);
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
