package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;

import java.util.HashMap;
import java.util.Map;

/**
 * Property-based tests for PlaceholderService covering:
 * - Property 2: Placeholder Substitution Correctness
 * - Property 3: Placeholder Pattern Strictness
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 */
class PlaceholderServicePropertyTest {

    private final PlaceholderService placeholderService = new PlaceholderService();

    // ========================================================================
    // Property 2: Placeholder Substitution Correctness
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 2: Placeholder Substitution Correctness")
    void tokensWithMatchingKeysAreReplacedWithStringValueOf(
            @ForAll("validVariableNames") String varName,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.1, 3.4
        String template = "prefix {{" + varName + "}} suffix";
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        String result = placeholderService.resolve(template, context);

        String expected = "prefix " + String.valueOf(value) + " suffix";
        assert result.equals(expected) :
                "Expected '" + expected + "' but got '" + result + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 2: Placeholder Substitution Correctness")
    void tokensWithoutMatchingKeysRemainUnchanged(
            @ForAll("validVariableNames") String varName,
            @ForAll("validVariableNames") String otherKey,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.4
        Assume.that(!varName.equals(otherKey));

        String template = "hello {{" + varName + "}} world";
        Map<String, Object> context = new HashMap<>();
        context.put(otherKey, value); // Key does not match the token variable

        String result = placeholderService.resolve(template, context);

        assert result.equals(template) :
                "Token should remain unchanged when key is absent. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 2: Placeholder Substitution Correctness")
    void resolveAndResolvePayloadExhibitSameSubstitutionForStringValues(
            @ForAll("validVariableNames") String varName,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.1, 3.2
        String template = "value is {{" + varName + "}}";
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        // resolve() result
        String resolveResult = placeholderService.resolve(template, context);

        // resolvePayload() with same string in a map
        Map<String, Object> payload = new HashMap<>();
        payload.put("key1", template);
        Map<String, Object> payloadResult = placeholderService.resolvePayload(payload, context);

        assert payloadResult.get("key1").equals(resolveResult) :
                "resolvePayload should produce same substitution as resolve for string values. " +
                "resolve='" + resolveResult + "', payload='" + payloadResult.get("key1") + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 2: Placeholder Substitution Correctness")
    void nullTemplateReturnsNull() {
        // Validates: Requirements 3.1
        Map<String, Object> context = Map.of("key", "value");

        String result = placeholderService.resolve(null, context);

        assert result == null : "Should return null for null template. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 2: Placeholder Substitution Correctness")
    void nullContextReturnsTemplateUnchanged(
            @ForAll("validVariableNames") String varName) {
        // Validates: Requirements 3.1
        String template = "test {{" + varName + "}} end";

        String result = placeholderService.resolve(template, null);

        assert result.equals(template) :
                "Should return template unchanged when context is null. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 2: Placeholder Substitution Correctness")
    void resolvePayloadNullPayloadReturnsNull() {
        // Validates: Requirements 3.2
        Map<String, Object> context = Map.of("key", "value");

        Map<String, Object> result = placeholderService.resolvePayload(null, context);

        assert result == null : "Should return null for null payload template";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 2: Placeholder Substitution Correctness")
    void resolvePayloadNullContextReturnsCopy(
            @ForAll("validVariableNames") String varName) {
        // Validates: Requirements 3.2
        Map<String, Object> payload = new HashMap<>();
        payload.put("field", "hello {{" + varName + "}}");

        Map<String, Object> result = placeholderService.resolvePayload(payload, null);

        assert result != null : "Should return non-null map for null context";
        assert result.get("field").equals("hello {{" + varName + "}}") :
                "Should leave tokens unchanged when context is null. Got: " + result.get("field");
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 2: Placeholder Substitution Correctness")
    void resolvePayloadHandlesNestedMaps(
            @ForAll("validVariableNames") String varName,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.2
        Map<String, Object> nested = new HashMap<>();
        nested.put("inner", "nested {{" + varName + "}}");

        Map<String, Object> payload = new HashMap<>();
        payload.put("outer", nested);

        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = placeholderService.resolvePayload(payload, context);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultNested = (Map<String, Object>) result.get("outer");

        String expected = "nested " + String.valueOf(value);
        assert resultNested.get("inner").equals(expected) :
                "Nested string values should be substituted. Expected: '" + expected +
                "', Got: '" + resultNested.get("inner") + "'";
    }

    // ========================================================================
    // Property 3: Placeholder Pattern Strictness
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 3: Placeholder Pattern Strictness")
    void validPatternsAreSubstituted(
            @ForAll("validVariableNames") String varName,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.5
        String template = "start {{" + varName + "}} end";
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        String result = placeholderService.resolve(template, context);

        assert !result.contains("{{" + varName + "}}") :
                "Valid pattern {{" + varName + "}} should be substituted. Got: " + result;
        assert result.contains(String.valueOf(value)) :
                "Result should contain the substituted value. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 3: Placeholder Pattern Strictness")
    void invalidPatternsWithHyphenAreNotSubstituted(
            @ForAll("validVariableNames") String part1,
            @ForAll("validVariableNames") String part2,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.5
        // {{a-b}} contains a hyphen which is not in [a-zA-Z0-9_]
        String invalidVar = part1 + "-" + part2;
        String template = "text {{" + invalidVar + "}} more";
        Map<String, Object> context = new HashMap<>();
        context.put(invalidVar, value);

        String result = placeholderService.resolve(template, context);

        assert result.equals(template) :
                "Invalid pattern with hyphen should NOT be substituted. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 3: Placeholder Pattern Strictness")
    void emptyBracesAreNotSubstituted() {
        // Validates: Requirements 3.5
        // {{}} has zero characters inside, pattern requires one or more [a-zA-Z0-9_]
        String template = "before {{}} after";
        Map<String, Object> context = new HashMap<>();
        context.put("", "replacement");

        String result = placeholderService.resolve(template, context);

        assert result.equals(template) :
                "Empty braces {{}} should NOT be substituted. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 3: Placeholder Pattern Strictness")
    void patternsWithSpacesAreNotSubstituted(
            @ForAll("validVariableNames") String varName,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.5
        // {{ x }} has spaces which are not in [a-zA-Z0-9_]
        String template = "text {{ " + varName + " }} more";
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);
        context.put(" " + varName + " ", value);

        String result = placeholderService.resolve(template, context);

        assert result.equals(template) :
                "Pattern with spaces {{ " + varName + " }} should NOT be substituted. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 3: Placeholder Pattern Strictness")
    void patternsWithDotsAreNotSubstituted(
            @ForAll("validVariableNames") String part1,
            @ForAll("validVariableNames") String part2,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.5
        // {{a.b}} contains a dot which is not in [a-zA-Z0-9_]
        String invalidVar = part1 + "." + part2;
        String template = "data {{" + invalidVar + "}} end";
        Map<String, Object> context = new HashMap<>();
        context.put(invalidVar, value);

        String result = placeholderService.resolve(template, context);

        assert result.equals(template) :
                "Pattern with dot should NOT be substituted. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 3: Placeholder Pattern Strictness")
    void singleBracePatternsAreNotSubstituted(
            @ForAll("validVariableNames") String varName,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.5
        // {var} with single braces is NOT the {{var}} pattern
        String template = "text {" + varName + "} more";
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        String result = placeholderService.resolve(template, context);

        assert result.equals(template) :
                "Single brace pattern should NOT be substituted. Got: " + result;
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 3: Placeholder Pattern Strictness")
    void dollarSignPatternsAreNotSubstituted(
            @ForAll("validVariableNames") String varName,
            @ForAll("contextValues") Object value) {
        // Validates: Requirements 3.5
        // $var or ${var} patterns should not be recognized
        String template = "text $" + varName + " and ${" + varName + "} end";
        Map<String, Object> context = new HashMap<>();
        context.put(varName, value);

        String result = placeholderService.resolve(template, context);

        assert result.equals(template) :
                "Dollar sign patterns should NOT be substituted. Got: " + result;
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<String> validVariableNames() {
        // Variable names must match [a-zA-Z0-9_]+ and be non-empty
        Arbitrary<String> alphaStart = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .ofMinLength(1).ofMaxLength(1);
        Arbitrary<String> rest = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_')
                .ofMinLength(0).ofMaxLength(10);
        return Combinators.combine(alphaStart, rest).as((start, tail) -> start + tail);
    }

    @Provide
    Arbitrary<Object> contextValues() {
        return Arbitraries.oneOf(
                Arbitraries.strings().ofMinLength(1).ofMaxLength(20).map(s -> (Object) s),
                Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
                Arbitraries.doubles().between(-1000.0, 1000.0).map(d -> (Object) d),
                Arbitraries.of(true, false).map(b -> (Object) b),
                Arbitraries.longs().between(-10000L, 10000L).map(l -> (Object) l)
        );
    }
}
