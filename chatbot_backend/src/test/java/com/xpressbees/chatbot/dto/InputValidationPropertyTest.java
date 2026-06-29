package com.xpressbees.chatbot.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: security-hardening, Property 5: Whitespace-only strings are rejected by NotBlank validation
// Feature: security-hardening, Property 6: Strings exceeding maximum length are rejected by Size validation

/**
 * Property-based tests for input validation on request DTOs.
 *
 * Property 5: For any string composed entirely of whitespace characters (spaces, tabs, newlines),
 * when submitted as a @NotBlank-annotated field, Jakarta Bean Validation SHALL produce a constraint violation.
 *
 * Property 6: For any string whose length exceeds the configured @Size(max=N) constraint
 * (255 for name, 1024 for url), Jakarta Bean Validation SHALL produce a constraint violation.
 *
 * Validates: Requirements 4.1, 4.2, 4.4, 4.5
 */
class InputValidationPropertyTest {

    private static final Validator validator;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ========================================================================
    // Property 5: Whitespace-only strings are rejected by NotBlank validation
    // ========================================================================

    /**
     * Property 5: Whitespace-only strings are rejected by NotBlank validation on ApiConfigRequest.name.
     * For all whitespace-only strings of length 1-100, validation SHALL produce a constraint violation.
     *
     * Validates: Requirements 4.1, 4.2
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 5: Whitespace-only strings are rejected by NotBlank validation")
    void whitespaceOnlyStringsAreRejectedByNotBlankOnName(
            @ForAll("whitespaceOnlyStrings") String whitespaceInput) {

        ApiConfigRequest request = new ApiConfigRequest();
        request.setName(whitespaceInput);
        request.setUrl("http://valid.example.com");
        request.setMethod("GET");

        Set<ConstraintViolation<ApiConfigRequest>> violations = validator.validate(request);

        Set<ConstraintViolation<ApiConfigRequest>> nameViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("name"))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(nameViolations)
                .as("Whitespace-only string '%s' should be rejected by @NotBlank on name", whitespaceInput)
                .isNotEmpty();
    }

    /**
     * Property 5: Whitespace-only strings are rejected by NotBlank validation on ApiConfigRequest.url.
     * For all whitespace-only strings, validation SHALL produce a constraint violation on url.
     *
     * Validates: Requirements 4.1, 4.5
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 5: Whitespace-only strings are rejected by NotBlank validation")
    void whitespaceOnlyStringsAreRejectedByNotBlankOnUrl(
            @ForAll("whitespaceOnlyStrings") String whitespaceInput) {

        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("ValidName");
        request.setUrl(whitespaceInput);
        request.setMethod("GET");

        Set<ConstraintViolation<ApiConfigRequest>> violations = validator.validate(request);

        Set<ConstraintViolation<ApiConfigRequest>> urlViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("url"))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(urlViolations)
                .as("Whitespace-only string should be rejected by @NotBlank on url")
                .isNotEmpty();
    }

    /**
     * Property 5: Whitespace-only strings are rejected by NotBlank validation on ApiConfigRequest.method.
     * For all whitespace-only strings, validation SHALL produce a constraint violation on method.
     *
     * Validates: Requirements 4.1
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 5: Whitespace-only strings are rejected by NotBlank validation")
    void whitespaceOnlyStringsAreRejectedByNotBlankOnMethod(
            @ForAll("whitespaceOnlyStrings") String whitespaceInput) {

        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("ValidName");
        request.setUrl("http://valid.example.com");
        request.setMethod(whitespaceInput);

        Set<ConstraintViolation<ApiConfigRequest>> violations = validator.validate(request);

        Set<ConstraintViolation<ApiConfigRequest>> methodViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("method"))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(methodViolations)
                .as("Whitespace-only string should be rejected by @NotBlank on method")
                .isNotEmpty();
    }

    // ========================================================================
    // Property 6: Strings exceeding maximum length are rejected by Size validation
    // ========================================================================

    /**
     * Property 6: Strings exceeding max length (255) are rejected by Size validation on name.
     * For all strings of length 256-500, validation SHALL produce a constraint violation mentioning "size".
     *
     * Validates: Requirements 4.4
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 6: Strings exceeding maximum length are rejected by Size validation")
    void stringsExceedingMaxLengthAreRejectedBySizeOnName(
            @ForAll("oversizedNameStrings") String oversizedInput) {

        ApiConfigRequest request = new ApiConfigRequest();
        request.setName(oversizedInput);
        request.setUrl("http://valid.example.com");
        request.setMethod("GET");

        Set<ConstraintViolation<ApiConfigRequest>> violations = validator.validate(request);

        Set<ConstraintViolation<ApiConfigRequest>> nameViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("name"))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(nameViolations)
                .as("String of length %d should be rejected by @Size(max=255) on name", oversizedInput.length())
                .isNotEmpty();

        assertThat(nameViolations)
                .anyMatch(v -> v.getMessage().toLowerCase().contains("size"));
    }

    /**
     * Property 6: Strings exceeding max length (1024) are rejected by Size validation on url.
     * For all strings of length 1025-2000, validation SHALL produce a constraint violation mentioning "size".
     *
     * Validates: Requirements 4.4
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 6: Strings exceeding maximum length are rejected by Size validation")
    void stringsExceedingMaxLengthAreRejectedBySizeOnUrl(
            @ForAll("oversizedUrlStrings") String oversizedInput) {

        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("ValidName");
        request.setUrl(oversizedInput);
        request.setMethod("GET");

        Set<ConstraintViolation<ApiConfigRequest>> violations = validator.validate(request);

        Set<ConstraintViolation<ApiConfigRequest>> urlViolations = violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals("url"))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(urlViolations)
                .as("String of length %d should be rejected by @Size(max=1024) on url", oversizedInput.length())
                .isNotEmpty();

        assertThat(urlViolations)
                .anyMatch(v -> v.getMessage().toLowerCase().contains("size"));
    }

    // ========================================================================
    // Generators
    // ========================================================================

    /**
     * Generates whitespace-only strings of length 1-100.
     * Includes spaces, tabs, newlines, carriage returns, and combinations.
     */
    @Provide
    Arbitrary<String> whitespaceOnlyStrings() {
        return Arbitraries.strings()
                .withChars(' ', '\t', '\n', '\r', '\u000B', '\f')
                .ofMinLength(1)
                .ofMaxLength(100);
    }

    /**
     * Generates strings of length 256-500 (exceeds @Size(max=255) on name).
     * Uses alphanumeric characters to avoid confusing the validator.
     */
    @Provide
    Arbitrary<String> oversizedNameStrings() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(256)
                .ofMaxLength(500);
    }

    /**
     * Generates strings of length 1025-2000 (exceeds @Size(max=1024) on url).
     * Uses alphanumeric characters to avoid confusing the validator.
     */
    @Provide
    Arbitrary<String> oversizedUrlStrings() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1025)
                .ofMaxLength(2000);
    }
}
