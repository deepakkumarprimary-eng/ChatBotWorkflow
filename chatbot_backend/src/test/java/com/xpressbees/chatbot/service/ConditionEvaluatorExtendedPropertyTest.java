package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extended property-based tests for ConditionEvaluator covering:
 * - Property 11: Single Comparison Expression Correctness
 * - Property 12: Conjunction Semantics (AND)
 * - Property 13: Disjunction Semantics (OR)
 * - Property 14: Missing Variable Evaluates to False
 *
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4
 */
class ConditionEvaluatorExtendedPropertyTest {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

    // ========================================================================
    // Property 11: Single Comparison Expression Correctness
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 11: Single Comparison Expression Correctness")
    void equalityOperatorMatchesJavaStringEquality(
            @ForAll("variableNames") String varName,
            @ForAll("stringValues") String value) {
        // Validates: Requirements 8.1
        String expression = varName + " == " + value;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        boolean result = conditionEvaluator.evaluate(expression, context);

        // Context value as string equals the literal → should be true
        assert result : "Equality should return true when context value matches literal. " +
                "Expression: '" + expression + "', Context value: '" + value + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 11: Single Comparison Expression Correctness")
    void equalityOperatorReturnsFalseOnMismatch(
            @ForAll("variableNames") String varName,
            @ForAll("stringValues") String contextValue,
            @ForAll("stringValues") String literal) {
        // Validates: Requirements 8.1
        Assume.that(!contextValue.equals(literal));

        String expression = varName + " == " + literal;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextValue);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Equality should return false when context value differs from literal. " +
                "Expression: '" + expression + "', Context value: '" + contextValue + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 11: Single Comparison Expression Correctness")
    void inequalityOperatorMatchesJavaSemantics(
            @ForAll("variableNames") String varName,
            @ForAll("stringValues") String contextValue,
            @ForAll("stringValues") String literal) {
        // Validates: Requirements 8.1
        String expression = varName + " != " + literal;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextValue);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = !contextValue.equals(literal);

        assert result == expected : "Inequality should match Java != semantics. " +
                "Expression: '" + expression + "', Context value: '" + contextValue +
                "', Expected: " + expected + ", Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 11: Single Comparison Expression Correctness")
    void numericComparisonLessThanMatchesJavaSemantics(
            @ForAll("variableNames") String varName,
            @ForAll @IntRange(min = -1000, max = 1000) int contextNum,
            @ForAll @IntRange(min = -1000, max = 1000) int literalNum) {
        // Validates: Requirements 8.1
        String expression = varName + " < " + literalNum;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextNum);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = (double) contextNum < (double) literalNum;

        assert result == expected : "Less-than should match Java numeric comparison. " +
                "Expression: '" + expression + "', Context: " + contextNum +
                ", Expected: " + expected + ", Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 11: Single Comparison Expression Correctness")
    void numericComparisonGreaterThanMatchesJavaSemantics(
            @ForAll("variableNames") String varName,
            @ForAll @IntRange(min = -1000, max = 1000) int contextNum,
            @ForAll @IntRange(min = -1000, max = 1000) int literalNum) {
        // Validates: Requirements 8.1
        String expression = varName + " > " + literalNum;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextNum);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = (double) contextNum > (double) literalNum;

        assert result == expected : "Greater-than should match Java numeric comparison. " +
                "Expression: '" + expression + "', Context: " + contextNum +
                ", Expected: " + expected + ", Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 11: Single Comparison Expression Correctness")
    void numericComparisonLessThanOrEqualMatchesJavaSemantics(
            @ForAll("variableNames") String varName,
            @ForAll @IntRange(min = -1000, max = 1000) int contextNum,
            @ForAll @IntRange(min = -1000, max = 1000) int literalNum) {
        // Validates: Requirements 8.1
        String expression = varName + " <= " + literalNum;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextNum);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = (double) contextNum <= (double) literalNum;

        assert result == expected : "Less-than-or-equal should match Java numeric comparison. " +
                "Expression: '" + expression + "', Context: " + contextNum +
                ", Expected: " + expected + ", Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 11: Single Comparison Expression Correctness")
    void numericComparisonGreaterThanOrEqualMatchesJavaSemantics(
            @ForAll("variableNames") String varName,
            @ForAll @IntRange(min = -1000, max = 1000) int contextNum,
            @ForAll @IntRange(min = -1000, max = 1000) int literalNum) {
        // Validates: Requirements 8.1
        String expression = varName + " >= " + literalNum;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextNum);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = (double) contextNum >= (double) literalNum;

        assert result == expected : "Greater-than-or-equal should match Java numeric comparison. " +
                "Expression: '" + expression + "', Context: " + contextNum +
                ", Expected: " + expected + ", Got: " + result;
    }

    // ========================================================================
    // Property 12: Conjunction Semantics (AND)
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 12: Conjunction Semantics (AND)")
    void conjunctionReturnsTrueOnlyWhenAllSubConditionsAreTrue(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2) {
        // Validates: Requirements 8.2
        Assume.that(!var1.equals(var2));

        // Build "var1 == val1 and var2 == val2"
        String expression = var1 + " == " + val1 + " and " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, val1);
        context.put(var2, val2);

        // Both conditions are true, so AND should be true
        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "Conjunction should return true when all sub-conditions are true. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 12: Conjunction Semantics (AND)")
    void conjunctionReturnsFalseWhenFirstSubConditionIsFalse(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2,
            @ForAll("stringValues") String wrongVal) {
        // Validates: Requirements 8.2
        Assume.that(!var1.equals(var2));
        Assume.that(!val1.equals(wrongVal));

        // First sub-condition is false (wrong value for var1)
        String expression = var1 + " == " + val1 + " and " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, wrongVal); // mismatch
        context.put(var2, val2);     // match

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Conjunction should return false when first sub-condition is false. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 12: Conjunction Semantics (AND)")
    void conjunctionReturnsFalseWhenSecondSubConditionIsFalse(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2,
            @ForAll("stringValues") String wrongVal) {
        // Validates: Requirements 8.2
        Assume.that(!var1.equals(var2));
        Assume.that(!val2.equals(wrongVal));

        String expression = var1 + " == " + val1 + " and " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, val1);     // match
        context.put(var2, wrongVal); // mismatch

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Conjunction should return false when second sub-condition is false. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 12: Conjunction Semantics (AND)")
    void conjunctionReturnsFalseWhenAllSubConditionsAreFalse(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2,
            @ForAll("stringValues") String wrongVal1,
            @ForAll("stringValues") String wrongVal2) {
        // Validates: Requirements 8.2
        Assume.that(!var1.equals(var2));
        Assume.that(!val1.equals(wrongVal1));
        Assume.that(!val2.equals(wrongVal2));

        String expression = var1 + " == " + val1 + " and " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, wrongVal1);
        context.put(var2, wrongVal2);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Conjunction should return false when all sub-conditions are false. " +
                "Expression: '" + expression + "'";
    }

    // ========================================================================
    // Property 13: Disjunction Semantics (OR)
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 13: Disjunction Semantics (OR)")
    void disjunctionReturnsTrueWhenAtLeastOneSubConditionIsTrue(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2,
            @ForAll("stringValues") String wrongVal) {
        // Validates: Requirements 8.3
        Assume.that(!var1.equals(var2));
        Assume.that(!val2.equals(wrongVal));

        // First is true, second is false
        String expression = var1 + " == " + val1 + " or " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, val1);     // match
        context.put(var2, wrongVal); // mismatch

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "Disjunction should return true when first sub-condition is true. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 13: Disjunction Semantics (OR)")
    void disjunctionReturnsTrueWhenSecondSubConditionIsTrue(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2,
            @ForAll("stringValues") String wrongVal) {
        // Validates: Requirements 8.3
        Assume.that(!var1.equals(var2));
        Assume.that(!val1.equals(wrongVal));

        // First is false, second is true
        String expression = var1 + " == " + val1 + " or " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, wrongVal); // mismatch
        context.put(var2, val2);     // match

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "Disjunction should return true when second sub-condition is true. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 13: Disjunction Semantics (OR)")
    void disjunctionReturnsTrueWhenAllSubConditionsAreTrue(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2) {
        // Validates: Requirements 8.3
        Assume.that(!var1.equals(var2));

        String expression = var1 + " == " + val1 + " or " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, val1);
        context.put(var2, val2);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "Disjunction should return true when all sub-conditions are true. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 13: Disjunction Semantics (OR)")
    void disjunctionReturnsFalseWhenNoSubConditionIsTrue(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2,
            @ForAll("stringValues") String wrongVal1,
            @ForAll("stringValues") String wrongVal2) {
        // Validates: Requirements 8.3
        Assume.that(!var1.equals(var2));
        Assume.that(!val1.equals(wrongVal1));
        Assume.that(!val2.equals(wrongVal2));

        String expression = var1 + " == " + val1 + " or " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, wrongVal1);
        context.put(var2, wrongVal2);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Disjunction should return false when no sub-condition is true. " +
                "Expression: '" + expression + "'";
    }

    // ========================================================================
    // Property 14: Missing Variable Evaluates to False
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 14: Missing Variable Evaluates to False")
    void missingVariableReturnsFalseWithoutException(
            @ForAll("variableNames") String varName,
            @ForAll("stringValues") String value,
            @ForAll("operators") String operator) {
        // Validates: Requirements 8.4
        String expression = varName + " " + operator + " " + value;
        Map<String, Object> context = new HashMap<>();
        // Context does NOT contain varName

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Condition with missing variable should return false. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 14: Missing Variable Evaluates to False")
    void missingVariableInConjunctionReturnsFalse(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2) {
        // Validates: Requirements 8.4
        Assume.that(!var1.equals(var2));

        // var1 is present, var2 is missing
        String expression = var1 + " == " + val1 + " and " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, val1); // only var1 present

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Conjunction with missing variable should return false. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 14: Missing Variable Evaluates to False")
    void missingVariableInDisjunctionDoesNotPreventTrueResult(
            @ForAll("variableNames") String var1,
            @ForAll("variableNames") String var2,
            @ForAll("stringValues") String val1,
            @ForAll("stringValues") String val2) {
        // Validates: Requirements 8.4
        Assume.that(!var1.equals(var2));

        // var1 is present and matches, var2 is missing
        String expression = var1 + " == " + val1 + " or " + var2 + " == " + val2;
        Map<String, Object> context = new HashMap<>();
        context.put(var1, val1); // matches first sub-condition

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "Disjunction should still return true when one condition matches, " +
                "even if other variable is missing. Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 14: Missing Variable Evaluates to False")
    void emptyContextEvaluatesToFalse(
            @ForAll("variableNames") String varName,
            @ForAll("stringValues") String value,
            @ForAll("operators") String operator) {
        // Validates: Requirements 8.4
        String expression = varName + " " + operator + " " + value;
        Map<String, Object> context = new HashMap<>(); // empty

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Condition with empty context should return false. " +
                "Expression: '" + expression + "'";
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<String> variableNames() {
        // Variable names: start with letter, followed by alphanumeric/underscore
        Arbitrary<String> start = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .ofMinLength(1).ofMaxLength(1);
        Arbitrary<String> rest = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(0).ofMaxLength(8);
        return Combinators.combine(start, rest).as((s, r) -> s + r);
    }

    @Provide
    Arbitrary<String> stringValues() {
        // Simple non-whitespace values that won't interfere with expression parsing
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1).ofMaxLength(10);
    }

    @Provide
    Arbitrary<String> operators() {
        return Arbitraries.of("==", "!=", "<", ">", "<=", ">=");
    }
}
