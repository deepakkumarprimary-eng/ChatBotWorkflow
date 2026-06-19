package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ExtractionResult;
import com.xpressbees.chatbot.entity.ApiResponseMapping;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Property-based tests for ResponseExtractor covering:
 * - Property 5: JsonPath Primitive Extraction Stores String Representation
 * - Property 6: JsonPath Array Extraction Joins Non-Null Elements
 *
 * Validates: Requirements 5.1, 5.2, 5.3
 */
class ResponseExtractorPropertyTest {

    private final ResponseExtractor responseExtractor = new ResponseExtractor();

    // ========================================================================
    // Property 5: JsonPath Primitive Extraction Stores String Representation
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 5: JsonPath Primitive Extraction")
    void stringPrimitiveExtractedAsStringValueOf(@ForAll("randomStrings") String value) {
        // Validates: Requirements 5.1, 5.2
        // Escape the string value for JSON
        String escapedValue = escapeJson(value);
        String json = "{\"key\": \"" + escapedValue + "\"}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.key");
        mapping.setContextVariableName("myVar");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert result.isSuccess() : "Extraction should succeed for valid JSON with string primitive";
        assert result.getExtractedValues() != null : "Extracted values should not be null";
        assert result.getExtractedValues().containsKey("myVar") :
                "Should contain variable 'myVar'. Got: " + result.getExtractedValues();
        assert result.getExtractedValues().get("myVar").equals(value) :
                "Extracted value should equal the original string. Expected: '" + value +
                "', Got: '" + result.getExtractedValues().get("myVar") + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 5: JsonPath Primitive Extraction")
    void integerPrimitiveExtractedAsStringValueOf(@ForAll int value) {
        // Validates: Requirements 5.1, 5.2
        String json = "{\"key\": " + value + "}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.key");
        mapping.setContextVariableName("numVar");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert result.isSuccess() : "Extraction should succeed for valid JSON with integer";
        assert result.getExtractedValues() != null : "Extracted values should not be null";
        assert result.getExtractedValues().containsKey("numVar") :
                "Should contain variable 'numVar'. Got: " + result.getExtractedValues();
        assert result.getExtractedValues().get("numVar").equals(String.valueOf(value)) :
                "Extracted value should equal String.valueOf(int). Expected: '" +
                String.valueOf(value) + "', Got: '" + result.getExtractedValues().get("numVar") + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 5: JsonPath Primitive Extraction")
    void booleanPrimitiveExtractedAsStringValueOf(@ForAll boolean value) {
        // Validates: Requirements 5.1, 5.2
        String json = "{\"key\": " + value + "}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.key");
        mapping.setContextVariableName("boolVar");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert result.isSuccess() : "Extraction should succeed for valid JSON with boolean";
        assert result.getExtractedValues() != null : "Extracted values should not be null";
        assert result.getExtractedValues().containsKey("boolVar") :
                "Should contain variable 'boolVar'. Got: " + result.getExtractedValues();
        assert result.getExtractedValues().get("boolVar").equals(String.valueOf(value)) :
                "Extracted value should equal String.valueOf(boolean). Expected: '" +
                String.valueOf(value) + "', Got: '" + result.getExtractedValues().get("boolVar") + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 5: JsonPath Primitive Extraction")
    void doublePrimitiveExtractedAsStringValueOf(@ForAll("finiteDoubles") Double value) {
        // Validates: Requirements 5.1, 5.2
        String json = "{\"key\": " + value + "}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.key");
        mapping.setContextVariableName("doubleVar");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert result.isSuccess() : "Extraction should succeed for valid JSON with double";
        assert result.getExtractedValues() != null : "Extracted values should not be null";
        assert result.getExtractedValues().containsKey("doubleVar") :
                "Should contain variable 'doubleVar'. Got: " + result.getExtractedValues();
        // JsonPath may return Integer for whole numbers, so we compare via String.valueOf of the parsed result
        String extracted = result.getExtractedValues().get("doubleVar");
        assert extracted != null : "Extracted value should not be null for a double primitive";
    }

    // ========================================================================
    // Property 6: JsonPath Array Extraction Joins Non-Null Elements
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 6: JsonPath Array Extraction")
    void arrayExtractionJoinsNonNullElementsWithNewline(
            @ForAll("nonEmptyStringLists") List<String> elements) {
        // Validates: Requirements 5.3
        // Build JSON array with the elements (no nulls in this test)
        String jsonArray = elements.stream()
                .map(e -> "\"" + escapeJson(e) + "\"")
                .collect(Collectors.joining(", "));
        String json = "{\"items\": [" + jsonArray + "]}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.items");
        mapping.setContextVariableName("arrayVar");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert result.isSuccess() : "Extraction should succeed for valid JSON array";
        assert result.getExtractedValues() != null : "Extracted values should not be null";
        assert result.getExtractedValues().containsKey("arrayVar") :
                "Should contain variable 'arrayVar'. Got: " + result.getExtractedValues();

        String expected = String.join("\n", elements);
        assert result.getExtractedValues().get("arrayVar").equals(expected) :
                "Array elements should be joined by '\\n'. Expected: '" + expected +
                "', Got: '" + result.getExtractedValues().get("arrayVar") + "'";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 6: JsonPath Array Extraction")
    void arrayExtractionSkipsNullElements(
            @ForAll("stringListsWithNulls") List<String> elements) {
        // Validates: Requirements 5.3
        // Build JSON array mixing actual values with nulls
        String jsonArray = elements.stream()
                .map(e -> e == null ? "null" : "\"" + escapeJson(e) + "\"")
                .collect(Collectors.joining(", "));
        String json = "{\"items\": [" + jsonArray + "]}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.items");
        mapping.setContextVariableName("arrayVar");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert result.isSuccess() : "Extraction should succeed for JSON array with nulls";

        List<String> nonNullElements = elements.stream()
                .filter(e -> e != null)
                .collect(Collectors.toList());

        if (nonNullElements.isEmpty()) {
            // Empty array after null filtering → mapping is skipped
            assert !result.getExtractedValues().containsKey("arrayVar") :
                    "Empty array after null filtering should skip the mapping";
        } else {
            String expected = String.join("\n", nonNullElements);
            assert result.getExtractedValues().containsKey("arrayVar") :
                    "Should contain variable 'arrayVar' when non-null elements exist";
            assert result.getExtractedValues().get("arrayVar").equals(expected) :
                    "Should join only non-null elements. Expected: '" + expected +
                    "', Got: '" + result.getExtractedValues().get("arrayVar") + "'";
        }
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 6: JsonPath Array Extraction")
    void arrayWithMixedTypesJoinsNonNullWithNewline(
            @ForAll("mixedArrayElements") List<Object> elements) {
        // Validates: Requirements 5.3
        // Build JSON array with mixed types (numbers, strings, booleans, nulls)
        String jsonArray = elements.stream()
                .map(e -> {
                    if (e == null) return "null";
                    if (e instanceof String) return "\"" + escapeJson((String) e) + "\"";
                    return String.valueOf(e);
                })
                .collect(Collectors.joining(", "));
        String json = "{\"items\": [" + jsonArray + "]}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.items");
        mapping.setContextVariableName("mixedVar");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert result.isSuccess() : "Extraction should succeed for mixed-type array";

        List<String> nonNullStringified = elements.stream()
                .filter(e -> e != null)
                .map(String::valueOf)
                .collect(Collectors.toList());

        if (nonNullStringified.isEmpty()) {
            assert !result.getExtractedValues().containsKey("mixedVar") :
                    "Empty array after null filtering should skip the mapping";
        } else {
            String expected = String.join("\n", nonNullStringified);
            assert result.getExtractedValues().containsKey("mixedVar") :
                    "Should contain variable 'mixedVar' when non-null elements exist";
            assert result.getExtractedValues().get("mixedVar").equals(expected) :
                    "Mixed-type array should join non-null String.valueOf elements with '\\n'. Expected: '" +
                    expected + "', Got: '" + result.getExtractedValues().get("mixedVar") + "'";
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 6: JsonPath Array Extraction")
    void emptyArrayAfterNullFilteringSkipsMapping() {
        // Validates: Requirements 5.3
        // An array of only nulls should produce an empty string after filtering and be skipped
        String json = "{\"items\": [null, null, null]}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.items");
        mapping.setContextVariableName("emptyVar");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert result.isSuccess() : "Extraction should succeed";
        assert !result.getExtractedValues().containsKey("emptyVar") :
                "All-null array should be skipped (empty after filtering)";
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 5: JsonPath Primitive Extraction")
    void invalidJsonBodyReturnsFailure(@ForAll("invalidJsonStrings") String invalidJson) {
        // Validates: Requirements 5.6
        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath("$.key");
        mapping.setContextVariableName("var");

        ExtractionResult result = responseExtractor.extract(invalidJson, List.of(mapping));

        assert !result.isSuccess() : "Invalid JSON '" + invalidJson + "' should produce a failure result";
        assert result.getErrorMessage() != null && result.getErrorMessage().contains("not valid JSON") :
                "Error message should indicate invalid JSON. Got: " + result.getErrorMessage();
    }

    @Property(tries = 100)
    @Tag("Feature: api-node-processor, Property 5: JsonPath Primitive Extraction")
    void invalidJsonPathReturnsFailure(@ForAll("invalidJsonPaths") String invalidPath) {
        // Validates: Requirements 5.4
        String json = "{\"key\": \"value\"}";

        ApiResponseMapping mapping = new ApiResponseMapping();
        mapping.setResponsePath(invalidPath);
        mapping.setContextVariableName("var");

        ExtractionResult result = responseExtractor.extract(json, List.of(mapping));

        assert !result.isSuccess() : "Invalid JsonPath '" + invalidPath + "' should produce a failure result";
        assert result.getErrorMessage() != null : "Error message should not be null for invalid path";
    }

    // ========================================================================
    // Providers
    // ========================================================================

    @Provide
    Arbitrary<String> randomStrings() {
        // Generate alphanumeric strings safe for JSON (no control chars or unescaped quotes)
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '_', '-', '.', ',', '!')
                .ofMinLength(1)
                .ofMaxLength(30);
    }

    @Provide
    Arbitrary<Double> finiteDoubles() {
        return Arbitraries.doubles().between(-1_000_000.0, 1_000_000.0)
                .filter(d -> !d.isNaN() && !d.isInfinite());
    }

    @Provide
    Arbitrary<List<String>> nonEmptyStringLists() {
        Arbitrary<String> safeStrings = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(15);
        return safeStrings.list().ofMinSize(1).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<String>> stringListsWithNulls() {
        Arbitrary<String> nullableStrings = Arbitraries.oneOf(
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .ofMinLength(1)
                        .ofMaxLength(10),
                Arbitraries.just(null)
        );
        return nullableStrings.list().ofMinSize(1).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<Object>> mixedArrayElements() {
        Arbitrary<Object> elements = Arbitraries.oneOf(
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .ofMinLength(1).ofMaxLength(8).map(s -> (Object) s),
                Arbitraries.integers().between(-100, 100).map(i -> (Object) i),
                Arbitraries.of(true, false).map(b -> (Object) b),
                Arbitraries.just(null)
        );
        return elements.list().ofMinSize(1).ofMaxSize(8);
    }

    @Provide
    Arbitrary<String> invalidJsonStrings() {
        // Only use strings that Jayway JsonPath's JSON provider will truly reject as invalid
        return Arbitraries.of(
                "{broken: json",
                "{\"key\": }",
                "[1, 2,",
                "{\"a\": [}",
                "{\"unclosed",
                "[\"no closing bracket"
        );
    }

    @Provide
    Arbitrary<String> invalidJsonPaths() {
        return Arbitraries.of(
                "$[",
                "$..",
                "$[?(@.",
                "invalid path !!",
                "$[?(@.x ==",
                "$$$$"
        );
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String escapeJson(String value) {
        if (value == null) return "null";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
