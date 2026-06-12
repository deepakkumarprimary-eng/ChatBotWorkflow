package com.chatbot.workflow.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.workflow.engine.ExecutionContext;
import com.chatbot.workflow.model.ContextVariable;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for ContextVariableService.
 * Pure unit tests — no Spring context needed.
 *
 * **Validates: Requirements 10.1, 10.2, 10.3, 10.5**
 */
class ContextVariableProperties {

    private final ContextVariableService service = new ContextVariableService();

    // ========================================================================
    // Property 32: Context variable name validation
    // For any string, it should be accepted as a context variable name if and
    // only if it matches ^[a-zA-Z0-9_]{1,64}$. Max 100 variables per workflow.
    // ========================================================================

    /**
     * Property 32: Valid variable names (matching ^[a-zA-Z0-9_]{1,64}$) should
     * be accepted by validateVariableName.
     *
     * **Validates: Requirements 10.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 32: Context variable name validation")
    void validVariableNamesShouldBeAccepted(@ForAll("validVariableNames") String name) {
        assertThat(service.validateVariableName(name)).isTrue();
    }

    /**
     * Property 32: Invalid variable names (not matching ^[a-zA-Z0-9_]{1,64}$) should
     * be rejected by validateVariableName.
     *
     * **Validates: Requirements 10.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 32: Context variable name validation")
    void invalidVariableNamesShouldBeRejected(@ForAll("invalidVariableNames") String name) {
        assertThat(service.validateVariableName(name)).isFalse();
    }

    /**
     * Property 32: A list of exactly 100 variables should pass count validation,
     * but 101 variables should fail.
     *
     * **Validates: Requirements 10.1**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 32: Context variable name validation")
    void variableCountValidation(@ForAll("validVariableNames") String baseName) {
        // 100 variables should pass
        List<ContextVariable> hundredVars = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            hundredVars.add(new ContextVariable("var_" + i, null));
        }
        assertThat(service.validateVariableCount(hundredVars)).isTrue();

        // 101 variables should fail
        List<ContextVariable> tooManyVars = new ArrayList<>(hundredVars);
        tooManyVars.add(new ContextVariable(baseName, null));
        assertThat(service.validateVariableCount(tooManyVars)).isFalse();
    }

    // ========================================================================
    // Property 33: Context variable propagation
    // For any execution where state A writes value V to variable X via output
    // mapping, all subsequent states should read value V from variable X.
    // ========================================================================

    /**
     * Property 33: After applying output mapping with a value, resolveVariable
     * should return that exact value.
     *
     * **Validates: Requirements 10.2, 10.3**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 33: Context variable propagation")
    void writtenVariableShouldBePropagated(
            @ForAll("validVariableNames") String variableName,
            @ForAll("arbitraryValues") Object value) {

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        // State A writes value V to variable X via output mapping
        Map<String, String> outputMapping = new HashMap<>();
        outputMapping.put(variableName, "outputField");

        Map<String, Object> stateOutput = new HashMap<>();
        stateOutput.put("outputField", value);

        service.applyOutputMapping(context, outputMapping, stateOutput);

        // Subsequent state should read value V from variable X
        UUID subsequentStateId = UUID.randomUUID();
        Object resolved = service.resolveVariable(context, variableName, subsequentStateId);
        assertThat(resolved).isEqualTo(value);
    }

    /**
     * Property 33: Multiple writes to the same variable should propagate the
     * last written value (overwrite semantics).
     *
     * **Validates: Requirements 10.2, 10.3**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 33: Context variable propagation")
    void lastWriteWins(@ForAll("validVariableNames") String variableName,
                       @ForAll("arbitraryValues") Object firstValue,
                       @ForAll("arbitraryValues") Object secondValue) {

        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        // First write
        Map<String, String> mapping1 = Collections.singletonMap(variableName, "field1");
        Map<String, Object> output1 = Collections.singletonMap("field1", firstValue);
        service.applyOutputMapping(context, mapping1, output1);

        // Second write overwrites
        Map<String, String> mapping2 = Collections.singletonMap(variableName, "field2");
        Map<String, Object> output2 = Collections.singletonMap("field2", secondValue);
        service.applyOutputMapping(context, mapping2, output2);

        // Should read the second value
        Object resolved = service.resolveVariable(context, variableName, UUID.randomUUID());
        assertThat(resolved).isEqualTo(secondValue);
    }

    // ========================================================================
    // Property 34: Undefined variable returns null with warning
    // For any state that references a variable not in context, the value should
    // be null and a warning should be logged.
    // ========================================================================

    /**
     * Property 34: Resolving a variable that does not exist in the context should
     * return null.
     *
     * **Validates: Requirements 10.5**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 34: Undefined variable returns null with warning")
    void undefinedVariableShouldReturnNull(@ForAll("validVariableNames") String variableName) {
        // Create context with no variables
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), new HashMap<>());

        UUID stateId = UUID.randomUUID();
        Object result = service.resolveVariable(context, variableName, stateId);

        assertThat(result).isNull();
    }

    /**
     * Property 34: Resolving a variable that was never written while other
     * variables exist should still return null.
     *
     * **Validates: Requirements 10.5**
     */
    @Property(tries = 100)
    @Tag("Feature: chatbot-workflow-builder, Property 34: Undefined variable returns null with warning")
    void undefinedVariableAmongExistingVarsShouldReturnNull(
            @ForAll("validVariableNames") String undefinedName,
            @ForAll("validVariableNames") String existingName,
            @ForAll("arbitraryValues") Object existingValue) {

        // Make sure names differ
        String actualUndefined = undefinedName.equals(existingName)
                ? undefinedName + "_x"
                : undefinedName;

        Map<String, Object> initialVars = new HashMap<>();
        initialVars.put(existingName, existingValue);
        ExecutionContext context = new ExecutionContext(UUID.randomUUID(), initialVars);

        UUID stateId = UUID.randomUUID();
        Object result = service.resolveVariable(context, actualUndefined, stateId);

        assertThat(result).isNull();
    }

    // ========================================================================
    // Providers (Generators)
    // ========================================================================

    @Provide
    Arbitrary<String> validVariableNames() {
        // Generate strings matching ^[a-zA-Z0-9_]{1,64}$
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(1)
                .ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> invalidVariableNames() {
        return Arbitraries.oneOf(
                // Empty string
                Arbitraries.just(""),
                // Too long (65+ characters)
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .ofMinLength(65)
                        .ofMaxLength(100),
                // Contains invalid characters (spaces, special chars)
                Arbitraries.strings()
                        .withChars(' ', '-', '.', '!', '@', '#', '$', '%')
                        .withCharRange('a', 'z')
                        .ofMinLength(1)
                        .ofMaxLength(64)
                        .filter(s -> !s.matches("^[a-zA-Z0-9_]{1,64}$"))
        );
    }

    @Provide
    Arbitrary<Object> arbitraryValues() {
        return Arbitraries.oneOf(
                Arbitraries.strings().ofMinLength(0).ofMaxLength(50).map(s -> (Object) s),
                Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
                Arbitraries.doubles().between(-1000.0, 1000.0).map(d -> (Object) d),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
    }
}
