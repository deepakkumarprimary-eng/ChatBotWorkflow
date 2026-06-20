package com.xpressbees.chatbot.service;

import net.jqwik.api.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Property-based tests for ConditionEvaluator covering:
 * - Property 7: Condition Expression Evaluation Correctness
 * - Property 8: Compound Condition Precedence
 *
 * Validates: Requirements 7.2, 7.3, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9
 */
class ConditionEvaluatorPropertyTest {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

    // ========================================================================
    // Property 7: Condition Expression Evaluation Correctness
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void equalsOperatorTrueWhenContextValueEqualsLiteralCaseSensitive(
            @ForAll("validVariableNames") String varName,
            @ForAll("stringValues") String value) {
        // Validates: Requirements 8.1, 8.2
        String expression = varName + " == " + value;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "== should return true when context value equals literal. " +
                "Expression: '" + expression + "', Context value: '" + value + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void equalsOperatorFalseWhenCaseDiffers(
            @ForAll("validVariableNames") String varName,
            @ForAll("lowerCaseStrings") String base) {
        // Validates: Requirements 8.2
        String contextValue = base;
        String literal = base.substring(0, 1).toUpperCase() + base.substring(1);
        Assume.that(!contextValue.equals(literal));

        String expression = varName + " == " + literal;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextValue);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "== should be case-sensitive. Context='" + contextValue +
                "', Literal='" + literal + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void notEqualsOperatorTrueWhenValuesDiffer(
            @ForAll("validVariableNames") String varName,
            @ForAll("stringValues") String contextValue,
            @ForAll("stringValues") String literal) {
        // Validates: Requirements 8.1, 8.2
        Assume.that(!contextValue.equals(literal));

        String expression = varName + " != " + literal;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, contextValue);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "!= should return true when values differ. " +
                "Context='" + contextValue + "', Literal='" + literal + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void notEqualsOperatorFalseWhenValuesEqual(
            @ForAll("validVariableNames") String varName,
            @ForAll("stringValues") String value) {
        // Validates: Requirements 8.1, 8.2
        String expression = varName + " != " + value;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "!= should return false when values are equal. Value='" + value + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void lessThanOperatorComparesAsDoubles(
            @ForAll("numericValues") double left,
            @ForAll("numericValues") double right) {
        // Validates: Requirements 8.3
        Assume.that(left != right);
        String varName = "num";
        String expression = varName + " < " + right;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, left);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = left < right;

        assert result == expected : "< comparison failed. Left=" + left +
                ", Right=" + right + ", Expected=" + expected + ", Got=" + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void greaterThanOperatorComparesAsDoubles(
            @ForAll("numericValues") double left,
            @ForAll("numericValues") double right) {
        // Validates: Requirements 8.3
        Assume.that(left != right);
        String varName = "num";
        String expression = varName + " > " + right;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, left);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = left > right;

        assert result == expected : "> comparison failed. Left=" + left +
                ", Right=" + right + ", Expected=" + expected + ", Got=" + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void lessThanOrEqualOperatorComparesAsDoubles(
            @ForAll("numericValues") double left,
            @ForAll("numericValues") double right) {
        // Validates: Requirements 8.3
        String varName = "num";
        String expression = varName + " <= " + right;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, left);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = left <= right;

        assert result == expected : "<= comparison failed. Left=" + left +
                ", Right=" + right + ", Expected=" + expected + ", Got=" + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void greaterThanOrEqualOperatorComparesAsDoubles(
            @ForAll("numericValues") double left,
            @ForAll("numericValues") double right) {
        // Validates: Requirements 8.3
        String varName = "num";
        String expression = varName + " >= " + right;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, left);

        boolean result = conditionEvaluator.evaluate(expression, context);
        boolean expected = left >= right;

        assert result == expected : ">= comparison failed. Left=" + left +
                ", Right=" + right + ", Expected=" + expected + ", Got=" + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void numericOperatorsReturnFalseWhenLeftIsNotANumber(
            @ForAll("validVariableNames") String varName,
            @ForAll("nonNumericStrings") String nonNumeric,
            @ForAll("numericValues") double rightNum,
            @ForAll("numericOperators") String operator) {
        // Validates: Requirements 8.4
        String expression = varName + " " + operator + " " + rightNum;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, nonNumeric);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Numeric operator '" + operator + "' should return false when left side " +
                "is not a number. Left='" + nonNumeric + "', Right=" + rightNum;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void numericOperatorsReturnFalseWhenRightIsNotANumber(
            @ForAll("validVariableNames") String varName,
            @ForAll("numericValues") double leftNum,
            @ForAll("nonNumericStrings") String nonNumeric,
            @ForAll("numericOperators") String operator) {
        // Validates: Requirements 8.4
        String expression = varName + " " + operator + " " + nonNumeric;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, leftNum);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Numeric operator '" + operator + "' should return false when right side " +
                "is not a number. Left=" + leftNum + ", Right='" + nonNumeric + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void missingVariableInContextReturnsFalse(
            @ForAll("validVariableNames") String varName,
            @ForAll("validVariableNames") String otherVar,
            @ForAll("allOperators") String operator,
            @ForAll("stringValues") String literal) {
        // Validates: Requirements 7.3 (missing variable yields false)
        Assume.that(!varName.equals(otherVar));

        String expression = varName + " " + operator + " " + literal;
        Map<String, Object> context = new HashMap<>();
        context.put(otherVar, "someValue"); // varName is NOT in context

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Should return false when variable '" + varName +
                "' is not in context. Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void expressionWithWrongNumberOfTokensReturnsFalse(
            @ForAll("malformedExpressions") String expression) {
        // Validates: Requirements 8.9
        Map<String, Object> context = new HashMap<>();
        context.put("x", "value");
        context.put("y", "100");
        context.put("status", "active");

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Expression with wrong number of tokens should return false. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 7: Condition Expression Evaluation Correctness")
    void unknownOperatorReturnsFalse(
            @ForAll("validVariableNames") String varName,
            @ForAll("unknownOperators") String operator,
            @ForAll("stringValues") String literal) {
        // Validates: Requirements 8.1
        String expression = varName + " " + operator + " " + literal;
        Map<String, Object> context = new HashMap<>();
        context.put(varName, "someValue");

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "Unknown operator '" + operator + "' should return false. " +
                "Expression: '" + expression + "'";
    }

    // ========================================================================
    // Property 8: Compound Condition Precedence
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 8: Compound Condition Precedence")
    void andOperatorTrueOnlyIfBothSidesTrue(
            @ForAll("validVariableNames") String varA,
            @ForAll("validVariableNames") String varB,
            @ForAll("stringValues") String valA,
            @ForAll("stringValues") String valB) {
        // Validates: Requirements 8.6
        Assume.that(!varA.equals(varB));

        String expression = varA + " == " + valA + " and " + varB + " == " + valB;
        Map<String, Object> context = new HashMap<>();
        context.put(varA, valA);
        context.put(varB, valB);

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "'A and B' should be true when both A and B are true. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 8: Compound Condition Precedence")
    void andOperatorFalseWhenEitherSideFalse(
            @ForAll("validVariableNames") String varA,
            @ForAll("validVariableNames") String varB,
            @ForAll("stringValues") String valA,
            @ForAll("stringValues") String valB,
            @ForAll("stringValues") String wrongVal) {
        // Validates: Requirements 8.6
        Assume.that(!varA.equals(varB));
        Assume.that(!valA.equals(wrongVal));
        Assume.that(!valB.equals(wrongVal));

        // A is true, B is false
        String expression = varA + " == " + valA + " and " + varB + " == " + valB;
        Map<String, Object> context = new HashMap<>();
        context.put(varA, valA);
        context.put(varB, wrongVal); // B won't match

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "'A and B' should be false when B is false. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 8: Compound Condition Precedence")
    void orOperatorTrueIfAtLeastOneIsTrue(
            @ForAll("validVariableNames") String varA,
            @ForAll("validVariableNames") String varB,
            @ForAll("stringValues") String valA,
            @ForAll("stringValues") String valB,
            @ForAll("stringValues") String wrongVal) {
        // Validates: Requirements 8.7
        Assume.that(!varA.equals(varB));
        Assume.that(!valB.equals(wrongVal));

        // A is true, B is false
        String expression = varA + " == " + valA + " or " + varB + " == " + valB;
        Map<String, Object> context = new HashMap<>();
        context.put(varA, valA);     // A matches
        context.put(varB, wrongVal); // B doesn't match

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert result : "'A or B' should be true when at least one is true. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 8: Compound Condition Precedence")
    void orOperatorFalseWhenBothAreFalse(
            @ForAll("validVariableNames") String varA,
            @ForAll("validVariableNames") String varB,
            @ForAll("stringValues") String valA,
            @ForAll("stringValues") String valB,
            @ForAll("stringValues") String wrongA,
            @ForAll("stringValues") String wrongB) {
        // Validates: Requirements 8.7
        Assume.that(!varA.equals(varB));
        Assume.that(!valA.equals(wrongA));
        Assume.that(!valB.equals(wrongB));

        String expression = varA + " == " + valA + " or " + varB + " == " + valB;
        Map<String, Object> context = new HashMap<>();
        context.put(varA, wrongA); // A doesn't match
        context.put(varB, wrongB); // B doesn't match

        boolean result = conditionEvaluator.evaluate(expression, context);

        assert !result : "'A or B' should be false when both are false. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 8: Compound Condition Precedence")
    void andBindsTighterThanOr_AorBandC(
            @ForAll("validVariableNames") String varA,
            @ForAll("validVariableNames") String varB,
            @ForAll("validVariableNames") String varC,
            @ForAll("stringValues") String valA,
            @ForAll("stringValues") String valB,
            @ForAll("stringValues") String valC,
            @ForAll("stringValues") String wrongB,
            @ForAll("stringValues") String wrongC) {
        // Validates: Requirements 8.8
        // A or B and C evaluates as A or (B and C)
        // Test: A is true, B is false, C is false → should be true (because A is true)
        Assume.that(!varA.equals(varB) && !varA.equals(varC) && !varB.equals(varC));
        Assume.that(!valB.equals(wrongB));
        Assume.that(!valC.equals(wrongC));

        String expression = varA + " == " + valA + " or " + varB + " == " + valB + " and " + varC + " == " + valC;
        Map<String, Object> context = new HashMap<>();
        context.put(varA, valA);    // A is true
        context.put(varB, wrongB);  // B is false
        context.put(varC, wrongC);  // C is false

        boolean result = conditionEvaluator.evaluate(expression, context);

        // If "and" binds tighter: A or (B and C) = true or (false and false) = true or false = true
        // If "or" binds tighter: (A or B) and C = (true or false) and false = true and false = false
        assert result : "'A or B and C' should evaluate as 'A or (B and C)'. " +
                "When A=true, B=false, C=false, result should be true. Got false. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 8: Compound Condition Precedence")
    void andBindsTighterThanOr_AandBorCandD(
            @ForAll("validVariableNames") String varA,
            @ForAll("validVariableNames") String varB,
            @ForAll("validVariableNames") String varC,
            @ForAll("validVariableNames") String varD,
            @ForAll("stringValues") String valA,
            @ForAll("stringValues") String valB,
            @ForAll("stringValues") String valC,
            @ForAll("stringValues") String valD,
            @ForAll("stringValues") String wrongA,
            @ForAll("stringValues") String wrongB) {
        // Validates: Requirements 8.8
        // A and B or C and D evaluates as (A and B) or (C and D)
        // Test: A=false, B=false, C=true, D=true → (false and false) or (true and true) = true
        Assume.that(!varA.equals(varB) && !varA.equals(varC) && !varA.equals(varD));
        Assume.that(!varB.equals(varC) && !varB.equals(varD) && !varC.equals(varD));
        Assume.that(!valA.equals(wrongA));
        Assume.that(!valB.equals(wrongB));

        String expression = varA + " == " + valA + " and " + varB + " == " + valB +
                " or " + varC + " == " + valC + " and " + varD + " == " + valD;
        Map<String, Object> context = new HashMap<>();
        context.put(varA, wrongA);  // A is false
        context.put(varB, wrongB);  // B is false
        context.put(varC, valC);    // C is true
        context.put(varD, valD);    // D is true

        boolean result = conditionEvaluator.evaluate(expression, context);

        // (A and B) or (C and D) = (false and false) or (true and true) = false or true = true
        assert result : "'A and B or C and D' should evaluate as '(A and B) or (C and D)'. " +
                "When A=false, B=false, C=true, D=true, result should be true. " +
                "Expression: '" + expression + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 8: Compound Condition Precedence")
    void complexCompoundPrecedenceVerification(
            @ForAll("validVariableNames") String varA,
            @ForAll("validVariableNames") String varB,
            @ForAll("validVariableNames") String varC,
            @ForAll("stringValues") String valA,
            @ForAll("stringValues") String valB,
            @ForAll("stringValues") String valC,
            @ForAll("stringValues") String wrongC) {
        // Validates: Requirements 8.8
        // A and B or C evaluates as (A and B) or C
        // Test: A=true, B=true, C=false → (true and true) or false = true
        Assume.that(!varA.equals(varB) && !varA.equals(varC) && !varB.equals(varC));
        Assume.that(!valC.equals(wrongC));

        String expression = varA + " == " + valA + " and " + varB + " == " + valB +
                " or " + varC + " == " + valC;
        Map<String, Object> context = new HashMap<>();
        context.put(varA, valA);    // A is true
        context.put(varB, valB);    // B is true
        context.put(varC, wrongC);  // C is false

        boolean result = conditionEvaluator.evaluate(expression, context);

        // (A and B) or C = (true and true) or false = true
        assert result : "'A and B or C' should evaluate as '(A and B) or C'. " +
                "When A=true, B=true, C=false, result should be true. " +
                "Expression: '" + expression + "'";
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<String> validVariableNames() {
        Arbitrary<String> alphaStart = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1).ofMaxLength(1);
        Arbitrary<String> rest = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(1).ofMaxLength(8);
        return Combinators.combine(alphaStart, rest).as((start, tail) -> start + tail);
    }

    @Provide
    Arbitrary<String> stringValues() {
        // Single-token values (no spaces) to avoid breaking the 3-token parser
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1).ofMaxLength(10);
    }

    @Provide
    Arbitrary<String> lowerCaseStrings() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2).ofMaxLength(8);
    }

    @Provide
    Arbitrary<Double> numericValues() {
        return Arbitraries.doubles().between(-1000.0, 1000.0)
                .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d));
    }

    @Provide
    Arbitrary<String> numericOperators() {
        return Arbitraries.of("<", ">", "<=", ">=");
    }

    @Provide
    Arbitrary<String> allOperators() {
        return Arbitraries.of("==", "!=", "<", ">", "<=", ">=");
    }

    @Provide
    Arbitrary<String> nonNumericStrings() {
        // Strings that cannot be parsed as a double and have no spaces
        return Arbitraries.of("abc", "hello", "xyz", "notANumber", "NaN_text", "twelve", "foo");
    }

    @Provide
    Arbitrary<String> malformedExpressions() {
        // Expressions that don't have exactly 3 tokens when treated as a simple condition
        return Arbitraries.of(
                "x",                    // 1 token
                "x ==",                 // 2 tokens
                "x == y extra",         // 4 tokens
                "a b c d e",            // 5 tokens
                "",                     // empty
                "   "                   // whitespace only
        );
    }

    @Provide
    Arbitrary<String> unknownOperators() {
        return Arbitraries.of("===", "!==", "<>", "~=", "=~", "in", "like", "%%");
    }
}
