package com.chatbot.workflow.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chatbot.workflow.engine.ExecutionContext;
import com.chatbot.workflow.model.ContextVariable;

/**
 * Unit tests for the ContextVariableService.
 */
class ContextVariableServiceTest {

    private ContextVariableService service;

    @BeforeEach
    void setUp() {
        service = new ContextVariableService();
    }

    // ===== Variable name validation =====

    @Test
    void validateVariableName_validSimpleName() {
        assertTrue(service.validateVariableName("userName"));
    }

    @Test
    void validateVariableName_validWithNumbers() {
        assertTrue(service.validateVariableName("var123"));
    }

    @Test
    void validateVariableName_validWithUnderscore() {
        assertTrue(service.validateVariableName("my_variable_name"));
    }

    @Test
    void validateVariableName_validSingleChar() {
        assertTrue(service.validateVariableName("x"));
    }

    @Test
    void validateVariableName_validAllDigits() {
        assertTrue(service.validateVariableName("123"));
    }

    @Test
    void validateVariableName_validExactly64Chars() {
        String name = "a".repeat(64);
        assertTrue(service.validateVariableName(name));
    }

    @Test
    void validateVariableName_invalidNull() {
        assertFalse(service.validateVariableName(null));
    }

    @Test
    void validateVariableName_invalidEmpty() {
        assertFalse(service.validateVariableName(""));
    }

    @Test
    void validateVariableName_invalidTooLong() {
        String name = "a".repeat(65);
        assertFalse(service.validateVariableName(name));
    }

    @Test
    void validateVariableName_invalidWithSpaces() {
        assertFalse(service.validateVariableName("my variable"));
    }

    @Test
    void validateVariableName_invalidWithHyphen() {
        assertFalse(service.validateVariableName("my-variable"));
    }

    @Test
    void validateVariableName_invalidWithDot() {
        assertFalse(service.validateVariableName("my.variable"));
    }

    @Test
    void validateVariableName_invalidWithSpecialChars() {
        assertFalse(service.validateVariableName("var@name"));
        assertFalse(service.validateVariableName("var!name"));
        assertFalse(service.validateVariableName("var#name"));
    }

    // ===== Variable count validation =====

    @Test
    void validateVariableCount_nullList() {
        assertTrue(service.validateVariableCount(null));
    }

    @Test
    void validateVariableCount_emptyList() {
        assertTrue(service.validateVariableCount(Collections.emptyList()));
    }

    @Test
    void validateVariableCount_withinLimit() {
        List<ContextVariable> variables = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            variables.add(new ContextVariable("var" + i, null));
        }
        assertTrue(service.validateVariableCount(variables));
    }

    @Test
    void validateVariableCount_exactlyAtLimit() {
        List<ContextVariable> variables = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            variables.add(new ContextVariable("var" + i, null));
        }
        assertTrue(service.validateVariableCount(variables));
    }

    @Test
    void validateVariableCount_exceedsLimit() {
        List<ContextVariable> variables = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            variables.add(new ContextVariable("var" + i, null));
        }
        assertFalse(service.validateVariableCount(variables));
    }

    // ===== Workflow variable validation =====

    @Test
    void validateWorkflowVariables_nullList() {
        List<String> errors = service.validateWorkflowVariables(null);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateWorkflowVariables_emptyList() {
        List<String> errors = service.validateWorkflowVariables(Collections.emptyList());
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateWorkflowVariables_allValid() {
        List<ContextVariable> variables = List.of(
                new ContextVariable("name", "default"),
                new ContextVariable("age", 25),
                new ContextVariable("is_active", true)
        );
        List<String> errors = service.validateWorkflowVariables(variables);
        assertTrue(errors.isEmpty());
    }

    @Test
    void validateWorkflowVariables_invalidName() {
        List<ContextVariable> variables = List.of(
                new ContextVariable("valid_name", null),
                new ContextVariable("invalid-name", null)
        );
        List<String> errors = service.validateWorkflowVariables(variables);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("invalid-name"));
    }

    @Test
    void validateWorkflowVariables_exceedsCount() {
        List<ContextVariable> variables = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            variables.add(new ContextVariable("var" + i, null));
        }
        List<String> errors = service.validateWorkflowVariables(variables);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds maximum")));
    }

    @Test
    void validateWorkflowVariables_bothCountAndNameErrors() {
        List<ContextVariable> variables = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            variables.add(new ContextVariable("var" + i, null));
        }
        // Replace one with an invalid name
        variables.set(50, new ContextVariable("invalid name!", null));

        List<String> errors = service.validateWorkflowVariables(variables);
        assertTrue(errors.size() >= 2);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds maximum")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("invalid name!")));
    }

    // ===== Output mapping =====

    @Test
    void applyOutputMapping_basicMapping() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put("userId", "id");
        outputMapping.put("userName", "name");

        Map<String, Object> stateOutput = new HashMap<>();
        stateOutput.put("id", 42);
        stateOutput.put("name", "Alice");

        service.applyOutputMapping(context, outputMapping, stateOutput);

        assertEquals(42, context.getVariable("userId"));
        assertEquals("Alice", context.getVariable("userName"));
    }

    @Test
    void applyOutputMapping_overwritesExistingValues() {
        Map<String, Object> initialVars = new HashMap<>();
        initialVars.put("result", "old_value");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), initialVars);

        Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put("result", "newResult");

        Map<String, Object> stateOutput = new HashMap<>();
        stateOutput.put("newResult", "new_value");

        service.applyOutputMapping(context, outputMapping, stateOutput);

        assertEquals("new_value", context.getVariable("result"));
    }

    @Test
    void applyOutputMapping_missingFieldSetsNull() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put("result", "nonexistentField");

        Map<String, Object> stateOutput = new HashMap<>();
        stateOutput.put("otherField", "value");

        service.applyOutputMapping(context, outputMapping, stateOutput);

        assertNull(context.getVariable("result"));
        assertTrue(context.getContextVariables().containsKey("result"));
    }

    @Test
    void applyOutputMapping_nullContext_noException() {
        Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put("result", "value");
        Map<String, Object> stateOutput = new HashMap<>();
        stateOutput.put("value", "test");

        // Should not throw
        service.applyOutputMapping(null, outputMapping, stateOutput);
    }

    @Test
    void applyOutputMapping_nullMapping_noException() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        Map<String, Object> stateOutput = new HashMap<>();
        stateOutput.put("value", "test");

        service.applyOutputMapping(context, null, stateOutput);
        assertTrue(context.getContextVariables().isEmpty());
    }

    @Test
    void applyOutputMapping_emptyMapping_noChanges() {
        Map<String, Object> initialVars = new HashMap<>();
        initialVars.put("existing", "value");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), initialVars);

        service.applyOutputMapping(context, Collections.emptyMap(), new HashMap<>());

        assertEquals("value", context.getVariable("existing"));
        assertEquals(1, context.getContextVariables().size());
    }

    @Test
    void applyOutputMapping_nullStateOutput_setsNullForAllMappedVars() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put("result", "data");

        service.applyOutputMapping(context, outputMapping, null);

        assertNull(context.getVariable("result"));
        assertTrue(context.getContextVariables().containsKey("result"));
    }

    // ===== Resolve variable =====

    @Test
    void resolveVariable_existingVariable() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", "Bob");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), vars);

        Object result = service.resolveVariable(context, "userName", UUID.randomUUID());
        assertEquals("Bob", result);
    }

    @Test
    void resolveVariable_existingVariableWithNullValue() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", null);
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), vars);

        Object result = service.resolveVariable(context, "userName", UUID.randomUUID());
        assertNull(result);
    }

    @Test
    void resolveVariable_undefinedVariable_returnsNull() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        Object result = service.resolveVariable(context, "nonExistent", UUID.randomUUID());
        assertNull(result);
    }

    @Test
    void resolveVariable_nullContext_returnsNull() {
        Object result = service.resolveVariable(null, "variableName", UUID.randomUUID());
        assertNull(result);
    }

    @Test
    void resolveVariable_nullVariableName_returnsNull() {
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());
        Object result = service.resolveVariable(context, null, UUID.randomUUID());
        assertNull(result);
    }

    @Test
    void resolveVariable_variableReadableBySubsequentStates() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("step1Result", "data_from_step1");
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), vars);

        // Simulate a subsequent state reading the variable
        UUID stateId = UUID.randomUUID();
        Object result = service.resolveVariable(context, "step1Result", stateId);
        assertEquals("data_from_step1", result);
    }
}
