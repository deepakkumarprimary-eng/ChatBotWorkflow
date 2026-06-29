package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extended property-based tests for PlaceholderService covering:
 * - Property 8: Placeholder Resolution Termination
 * - Property 9: No-Placeholder String Preservation (Idempotence)
 * - Property 10: Complete Resolution When All Keys Present
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4
 */
class PlaceholderServiceExtendedPropertyTest {

    private final PlaceholderService placeholderService = new PlaceholderService();
    private static final Pattern UNRESOLVED_PATTERN = Pattern.compile("\\{\\{[a-zA-Z0-9_]+\\}\\}");

    // ========================================================================
    // Property 8: Placeholder Resolution Termination
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 8: Placeholder Resolution Termination")
    void resolutionTerminatesWithNestedPlaceholderReferences(
            @ForAll("validVariableNames") String key1,
            @ForAll("validVariableNames") String key2) {
        // Validates: Requirements 7.1, 7.3
        // Context where key1's value references key2, and key2's value references key1 (circular)
        Assume.that(!key1.equals(key2));

        Map<String, Object> context = new HashMap<>();
        context.put(key1, "{{" + key2 + "}}");
        context.put(key2, "{{" + key1 + "}}");

        String template = "Hello {{" + key1 + "}} world";

        // Must terminate without infinite recursion - resolve is single-pass
        String result = placeholderService.resolve(template, context);

        // The service performs single-pass resolution, so it should terminate
        assert result != null : "Resolution must terminate and return a non-null result";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 8: Placeholder Resolution Termination")
    void resolutionTerminatesWithSelfReferencingPlaceholder(
            @ForAll("validVariableNames") String key) {
        // Validates: Requirements 7.1, 7.3
        // Context where a key's value references itself
        Map<String, Object> context = new HashMap<>();
        context.put(key, "{{" + key + "}}");

        String template = "Start {{" + key + "}} end";

        // Must terminate without infinite loop
        String result = placeholderService.resolve(template, context);

        assert result != null : "Self-referencing placeholder must terminate and return non-null";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 8: Placeholder Resolution Termination")
    void resolutionTerminatesWithDeeplyNestedChain(
            @ForAll @IntRange(min = 3, max = 10) int chainLength) {
        // Validates: Requirements 7.1, 7.3
        // Create a chain: key0 -> "{{key1}}", key1 -> "{{key2}}", ..., keyN -> "{{key0}}" (circular)
        Map<String, Object> context = new HashMap<>();
        for (int i = 0; i < chainLength; i++) {
            String nextKey = "key" + ((i + 1) % chainLength);
            context.put("key" + i, "{{" + nextKey + "}}");
        }

        String template = "value: {{key0}}";

        // Must terminate
        String result = placeholderService.resolve(template, context);

        assert result != null : "Deeply nested circular references must terminate";
    }

    // ========================================================================
    // Property 9: No-Placeholder String Preservation (Idempotence)
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 9: No-Placeholder String Preservation")
    void stringWithoutPlaceholderSyntaxIsReturnedUnchanged(
            @ForAll("stringsWithoutPlaceholders") String input) {
        // Validates: Requirements 7.2
        Map<String, Object> context = new HashMap<>();
        context.put("someKey", "someValue");
        context.put("another", "val");

        String result = placeholderService.resolve(input, context);

        assert result.equals(input) :
                "String without placeholder syntax should be returned byte-for-byte unchanged. " +
                "Input: '" + input + "', Got: '" + result + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 9: No-Placeholder String Preservation")
    void plainTextWithSpecialCharactersIsPreserved(
            @ForAll("plainTextWithSpecialChars") String input) {
        // Validates: Requirements 7.2
        Map<String, Object> context = new HashMap<>();
        context.put("x", "replaced");

        String result = placeholderService.resolve(input, context);

        assert result.equals(input) :
                "Plain text with special characters but no placeholder syntax should be preserved. " +
                "Input: '" + input + "', Got: '" + result + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 9: No-Placeholder String Preservation")
    void emptyStringIsPreserved() {
        // Validates: Requirements 7.2
        Map<String, Object> context = new HashMap<>();
        context.put("key", "value");

        String result = placeholderService.resolve("", context);

        assert result.equals("") : "Empty string should be returned unchanged. Got: '" + result + "'";
    }

    // ========================================================================
    // Property 10: Complete Resolution When All Keys Present
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 10: Complete Resolution When All Keys Present")
    void noUnresolvedPatternsWhenAllKeysPresent(
            @ForAll("validVariableNames") String key1,
            @ForAll("validVariableNames") String key2,
            @ForAll("simpleValues") String value1,
            @ForAll("simpleValues") String value2) {
        // Validates: Requirements 7.4
        Assume.that(!key1.equals(key2));

        String template = "Hello {{" + key1 + "}} and {{" + key2 + "}} done";
        Map<String, Object> context = new HashMap<>();
        context.put(key1, value1);
        context.put(key2, value2);

        String result = placeholderService.resolve(template, context);

        Matcher matcher = UNRESOLVED_PATTERN.matcher(result);
        assert !matcher.find() :
                "No unresolved {{...}} patterns should remain when all keys are present. " +
                "Template: '" + template + "', Result: '" + result + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 10: Complete Resolution When All Keys Present")
    void singlePlaceholderFullyResolved(
            @ForAll("validVariableNames") String key,
            @ForAll("simpleValues") String value) {
        // Validates: Requirements 7.4
        String template = "prefix {{" + key + "}} suffix";
        Map<String, Object> context = new HashMap<>();
        context.put(key, value);

        String result = placeholderService.resolve(template, context);

        Matcher matcher = UNRESOLVED_PATTERN.matcher(result);
        assert !matcher.find() :
                "Single placeholder should be fully resolved. Template: '" + template + "', Result: '" + result + "'";
        assert result.equals("prefix " + value + " suffix") :
                "Result should be 'prefix " + value + " suffix'. Got: '" + result + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: testing-coverage, Property 10: Complete Resolution When All Keys Present")
    void multipleSamePlaceholdersAllResolved(
            @ForAll("validVariableNames") String key,
            @ForAll("simpleValues") String value) {
        // Validates: Requirements 7.4
        String template = "{{" + key + "}} middle {{" + key + "}} end {{" + key + "}}";
        Map<String, Object> context = new HashMap<>();
        context.put(key, value);

        String result = placeholderService.resolve(template, context);

        Matcher matcher = UNRESOLVED_PATTERN.matcher(result);
        assert !matcher.find() :
                "All occurrences of the same placeholder should be resolved. " +
                "Template: '" + template + "', Result: '" + result + "'";
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
    Arbitrary<String> stringsWithoutPlaceholders() {
        // Generate strings that do NOT contain "{{" and "}}" pairs
        // Use alphanumeric + common chars, explicitly excluding braces
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '.', ',', '!', '?', '-', '_', ':', ';', '/', '@', '#')
                .ofMinLength(0).ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> plainTextWithSpecialChars() {
        // Generate strings with special chars but no valid placeholder pattern
        // Include single braces, partial patterns, but never complete {{validName}}
        return Arbitraries.oneOf(
                // Single braces
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withChars('{', '}', ' ')
                        .ofMinLength(1).ofMaxLength(20)
                        .filter(s -> !UNRESOLVED_PATTERN.matcher(s).find()),
                // Text with newlines and tabs
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withChars('\n', '\t', ' ')
                        .ofMinLength(1).ofMaxLength(30),
                // Unicode-safe alphanumeric
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .withChars('$', '%', '^', '&', '*', '(', ')')
                        .ofMinLength(1).ofMaxLength(25)
        );
    }

    @Provide
    Arbitrary<String> simpleValues() {
        // Values that don't contain placeholder syntax themselves
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_')
                .ofMinLength(1).ofMaxLength(15);
    }
}
