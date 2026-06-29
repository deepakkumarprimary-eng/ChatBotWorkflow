package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InputValidationServiceImpl.
 * Tests each validation rule individually and in combination.
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3,
 *            5.1, 5.2, 5.3, 6.2, 6.3, 7.1, 7.2
 */
class InputValidationServiceImplTest {

    private InputValidationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InputValidationServiceImpl();
    }

    // ===== Required Rule Tests =====

    @Nested
    @DisplayName("Required rule")
    class RequiredRuleTests {

        @Test
        @DisplayName("rejects null input when required is true")
        void rejectsNullInput() {
            Map<String, Object> config = Map.of("required", true);
            ValidationResult result = service.validate(null, config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("This field is required");
        }

        @Test
        @DisplayName("rejects empty string when required is true")
        void rejectsEmptyString() {
            Map<String, Object> config = Map.of("required", true);
            ValidationResult result = service.validate("", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("This field is required");
        }

        @Test
        @DisplayName("rejects whitespace-only input when required is true")
        void rejectsWhitespaceOnly() {
            Map<String, Object> config = Map.of("required", true);
            ValidationResult result = service.validate("   \t\n  ", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("This field is required");
        }

        @Test
        @DisplayName("accepts valid input when required is true")
        void acceptsValidInput() {
            Map<String, Object> config = Map.of("required", true);
            ValidationResult result = service.validate("hello", config);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("accepts empty input when required is false")
        void acceptsEmptyWhenNotRequired() {
            Map<String, Object> config = Map.of("required", false);
            ValidationResult result = service.validate("", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("treats non-boolean required value as not-required (fail-open)")
        void failOpenForNonBoolean() {
            Map<String, Object> config = Map.of("required", "yes");
            ValidationResult result = service.validate("", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("treats integer required value as not-required (fail-open)")
        void failOpenForIntegerRequired() {
            Map<String, Object> config = Map.of("required", 1);
            ValidationResult result = service.validate(null, config);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ===== MinLength Rule Tests =====

    @Nested
    @DisplayName("MinLength rule")
    class MinLengthRuleTests {

        @Test
        @DisplayName("passes when trimmed input length equals minLength (exact boundary)")
        void passesAtExactBoundary() {
            Map<String, Object> config = Map.of("minLength", 5);
            ValidationResult result = service.validate("abcde", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("fails when trimmed input length is one below minLength")
        void failsOneBelowBoundary() {
            Map<String, Object> config = Map.of("minLength", 5);
            ValidationResult result = service.validate("abcd", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Input must be at least 5 characters");
        }

        @Test
        @DisplayName("uses trimmed length - leading/trailing spaces are not counted")
        void usesTrimmedLength() {
            Map<String, Object> config = Map.of("minLength", 5);
            // "  abc  " trimmed is "abc" (3 chars) — should fail
            ValidationResult result = service.validate("  abc  ", config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("uses trimmed length - spaces padded input that meets minimum after trim passes")
        void trimmedInputMeetsMinimum() {
            Map<String, Object> config = Map.of("minLength", 5);
            // "  abcde  " trimmed is "abcde" (5 chars) — should pass
            ValidationResult result = service.validate("  abcde  ", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("skips validation for non-integer minLength value")
        void skipsForNonInteger() {
            Map<String, Object> config = Map.of("minLength", "five");
            ValidationResult result = service.validate("a", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("skips validation for negative minLength value")
        void skipsForNegativeValue() {
            Map<String, Object> config = Map.of("minLength", -1);
            ValidationResult result = service.validate("a", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("passes when input exceeds minLength")
        void passesWhenInputExceedsMin() {
            Map<String, Object> config = Map.of("minLength", 3);
            ValidationResult result = service.validate("hello world", config);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ===== MaxLength Rule Tests =====

    @Nested
    @DisplayName("MaxLength rule")
    class MaxLengthRuleTests {

        @Test
        @DisplayName("passes when trimmed input length equals maxLength (exact boundary)")
        void passesAtExactBoundary() {
            Map<String, Object> config = Map.of("maxLength", 5);
            ValidationResult result = service.validate("abcde", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("fails when trimmed input length is one above maxLength")
        void failsOneAboveBoundary() {
            Map<String, Object> config = Map.of("maxLength", 5);
            ValidationResult result = service.validate("abcdef", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Input must not exceed 5 characters");
        }

        @Test
        @DisplayName("uses trimmed length - trailing spaces do not count toward max")
        void usesTrimmedLength() {
            Map<String, Object> config = Map.of("maxLength", 5);
            // "abcde   " trimmed is "abcde" (5 chars) — should pass
            ValidationResult result = service.validate("abcde   ", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("uses trimmed length - content within spaces exceeds max")
        void trimmedContentExceedsMax() {
            Map<String, Object> config = Map.of("maxLength", 3);
            // "  abcde  " trimmed is "abcde" (5 chars) — should fail
            ValidationResult result = service.validate("  abcde  ", config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("skips validation for non-integer maxLength value")
        void skipsForNonInteger() {
            Map<String, Object> config = Map.of("maxLength", "ten");
            ValidationResult result = service.validate("a very long string input", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("skips validation for negative maxLength value")
        void skipsForNegativeValue() {
            Map<String, Object> config = Map.of("maxLength", -5);
            ValidationResult result = service.validate("hello", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("passes when input is shorter than maxLength")
        void passesWhenInputShorterThanMax() {
            Map<String, Object> config = Map.of("maxLength", 100);
            ValidationResult result = service.validate("short", config);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ===== NumericOnly Rule Tests =====

    @Nested
    @DisplayName("NumericOnly rule")
    class NumericOnlyRuleTests {

        @Test
        @DisplayName("accepts digits only")
        void acceptsDigitsOnly() {
            Map<String, Object> config = Map.of("numericOnly", true);
            ValidationResult result = service.validate("1234567890", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("rejects input containing letters")
        void rejectsLetters() {
            Map<String, Object> config = Map.of("numericOnly", true);
            ValidationResult result = service.validate("123abc", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Only numeric characters are allowed");
        }

        @Test
        @DisplayName("rejects input containing special characters")
        void rejectsSpecialChars() {
            Map<String, Object> config = Map.of("numericOnly", true);
            ValidationResult result = service.validate("123!@#", config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("rejects Unicode digit characters (e.g. Arabic-Indic digits)")
        void rejectsUnicodeDigits() {
            Map<String, Object> config = Map.of("numericOnly", true);
            // Arabic-Indic digits ١٢٣ are not ASCII 0-9
            ValidationResult result = service.validate("\u0661\u0662\u0663", config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("rejects decimal point")
        void rejectsDecimalPoint() {
            Map<String, Object> config = Map.of("numericOnly", true);
            ValidationResult result = service.validate("3.14", config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("rejects negative sign")
        void rejectsNegativeSign() {
            Map<String, Object> config = Map.of("numericOnly", true);
            ValidationResult result = service.validate("-123", config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("accepts empty trimmed input (empty check delegated to required rule)")
        void acceptsEmptyInput() {
            Map<String, Object> config = Map.of("numericOnly", true);
            ValidationResult result = service.validate("", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("accepts whitespace-only input (trims to empty)")
        void acceptsWhitespaceOnlyInput() {
            Map<String, Object> config = Map.of("numericOnly", true);
            ValidationResult result = service.validate("   ", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("does not apply when numericOnly is false")
        void doesNotApplyWhenFalse() {
            Map<String, Object> config = Map.of("numericOnly", false);
            ValidationResult result = service.validate("hello", config);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ===== Pattern Rule Tests =====

    @Nested
    @DisplayName("Pattern rule")
    class PatternRuleTests {

        @Test
        @DisplayName("accepts input matching regex pattern")
        void acceptsMatchingInput() {
            Map<String, Object> config = Map.of("pattern", "^[A-Za-z]+$");
            ValidationResult result = service.validate("Hello", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("rejects input not matching regex pattern")
        void rejectsNonMatchingInput() {
            Map<String, Object> config = Map.of("pattern", "^[A-Za-z]+$");
            ValidationResult result = service.validate("Hello123", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Input must match the required format");
        }

        @Test
        @DisplayName("accepts input on invalid regex (fail-open)")
        void failOpenOnInvalidRegex() {
            Map<String, Object> config = Map.of("pattern", "[invalid(regex");
            ValidationResult result = service.validate("anything", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("skips validation when pattern is not a string")
        void skipsNonStringPattern() {
            Map<String, Object> config = Map.of("pattern", 123);
            ValidationResult result = service.validate("anything", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("skips validation when pattern is an empty string")
        void skipsEmptyPattern() {
            Map<String, Object> config = Map.of("pattern", "");
            ValidationResult result = service.validate("anything", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("uses untrimmed input for pattern matching")
        void usesUntrimmedInput() {
            // Pattern requires exactly "hello" with no surrounding whitespace
            Map<String, Object> config = Map.of("pattern", "^hello$");
            // Input has trailing space — should fail because matcher uses untrimmed
            ValidationResult result = service.validate("hello ", config);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("matches phone number pattern")
        void matchesPhonePattern() {
            Map<String, Object> config = Map.of("pattern", "^\\d{10}$");
            ValidationResult result = service.validate("9876543210", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("rejects phone number with wrong length")
        void rejectsWrongLengthPhone() {
            Map<String, Object> config = Map.of("pattern", "^\\d{10}$");
            ValidationResult result = service.validate("12345", config);
            assertThat(result.isValid()).isFalse();
        }
    }

    // ===== Error Messages Tests =====

    @Nested
    @DisplayName("Error messages")
    class ErrorMessageTests {

        @Test
        @DisplayName("uses custom error message when present")
        void usesCustomMessage() {
            Map<String, Object> errorMessages = Map.of("required", "Please provide your name");
            Map<String, Object> config = Map.of(
                    "required", true,
                    "errorMessages", errorMessages
            );
            ValidationResult result = service.validate("", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Please provide your name");
        }

        @Test
        @DisplayName("uses default error message when custom is absent")
        void usesDefaultMessageWhenAbsent() {
            Map<String, Object> config = Map.of("required", true);
            ValidationResult result = service.validate("", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("This field is required");
        }

        @Test
        @DisplayName("uses default message when errorMessages object is missing the rule key")
        void usesDefaultWhenKeyMissing() {
            Map<String, Object> errorMessages = Map.of("pattern", "Wrong format");
            Map<String, Object> config = new HashMap<>();
            config.put("required", true);
            config.put("errorMessages", errorMessages);
            ValidationResult result = service.validate("", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("This field is required");
        }

        @Test
        @DisplayName("uses custom minLength error message with formatted value")
        void usesCustomMinLengthMessage() {
            Map<String, Object> errorMessages = Map.of("minLength", "Too short!");
            Map<String, Object> config = Map.of(
                    "minLength", 10,
                    "errorMessages", errorMessages
            );
            ValidationResult result = service.validate("hi", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Too short!");
        }

        @Test
        @DisplayName("uses default minLength message with length value when no custom")
        void usesDefaultMinLengthMessage() {
            Map<String, Object> config = Map.of("minLength", 10);
            ValidationResult result = service.validate("hi", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Input must be at least 10 characters");
        }

        @Test
        @DisplayName("uses custom maxLength error message")
        void usesCustomMaxLengthMessage() {
            Map<String, Object> errorMessages = Map.of("maxLength", "Way too long!");
            Map<String, Object> config = Map.of(
                    "maxLength", 3,
                    "errorMessages", errorMessages
            );
            ValidationResult result = service.validate("hello world", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Way too long!");
        }

        @Test
        @DisplayName("uses custom numericOnly error message")
        void usesCustomNumericOnlyMessage() {
            Map<String, Object> errorMessages = Map.of("numericOnly", "Digits only please");
            Map<String, Object> config = Map.of(
                    "numericOnly", true,
                    "errorMessages", errorMessages
            );
            ValidationResult result = service.validate("abc", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Digits only please");
        }

        @Test
        @DisplayName("uses custom pattern error message")
        void usesCustomPatternMessage() {
            Map<String, Object> errorMessages = Map.of("pattern", "Must be an email address");
            Map<String, Object> config = Map.of(
                    "pattern", "^[\\w.]+@[\\w.]+$",
                    "errorMessages", errorMessages
            );
            ValidationResult result = service.validate("not-an-email", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Must be an email address");
        }

        @Test
        @DisplayName("mixed: some custom, some default messages")
        void mixedCustomAndDefault() {
            Map<String, Object> errorMessages = Map.of("required", "Name cannot be empty");
            Map<String, Object> config = new HashMap<>();
            config.put("required", true);
            config.put("minLength", 5);
            config.put("errorMessages", errorMessages);

            // Fails required (first rule) — gets custom message
            ValidationResult result = service.validate("", config);
            assertThat(result.getErrorMessage()).isEqualTo("Name cannot be empty");
        }
    }

    // ===== Full Validation / Null/Empty Config Tests =====

    @Nested
    @DisplayName("Full validation with null/empty config")
    class FullValidationTests {

        @Test
        @DisplayName("accepts any input when validation config is null")
        void acceptsWithNullConfig() {
            ValidationResult result = service.validate("anything", null);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("accepts any input when validation config is empty map")
        void acceptsWithEmptyConfig() {
            ValidationResult result = service.validate("anything", Map.of());
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("accepts null input when validation config is null")
        void acceptsNullInputWithNullConfig() {
            ValidationResult result = service.validate(null, null);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("multiple rules combined - all pass")
        void multipleRulesAllPass() {
            Map<String, Object> config = new HashMap<>();
            config.put("required", true);
            config.put("minLength", 3);
            config.put("maxLength", 10);
            config.put("numericOnly", true);
            config.put("pattern", "^\\d+$");

            ValidationResult result = service.validate("12345", config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("multiple rules combined - short-circuits on first failure")
        void multipleRulesShortCircuit() {
            Map<String, Object> config = new HashMap<>();
            config.put("required", true);
            config.put("minLength", 5);
            config.put("numericOnly", true);

            // Input "ab" fails minLength (< 5) and numericOnly (not digits)
            // Should get minLength error because it's evaluated before numericOnly
            ValidationResult result = service.validate("ab", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Input must be at least 5 characters");
        }
    }

    // ===== Evaluation Order Tests =====

    @Nested
    @DisplayName("Evaluation order")
    class EvaluationOrderTests {

        @Test
        @DisplayName("required fails before minLength is checked")
        void requiredBeforeMinLength() {
            Map<String, Object> config = new HashMap<>();
            config.put("required", true);
            config.put("minLength", 5);

            // Empty input fails both required and minLength — required error should appear
            ValidationResult result = service.validate("", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("This field is required");
        }

        @Test
        @DisplayName("minLength fails before maxLength is checked")
        void minLengthBeforeMaxLength() {
            Map<String, Object> config = new HashMap<>();
            config.put("minLength", 10);
            config.put("maxLength", 5);  // contradictory config

            // Input "abc" (length 3) fails minLength — should not reach maxLength
            ValidationResult result = service.validate("abc", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Input must be at least 10 characters");
        }

        @Test
        @DisplayName("maxLength fails before numericOnly is checked")
        void maxLengthBeforeNumericOnly() {
            Map<String, Object> config = new HashMap<>();
            config.put("maxLength", 3);
            config.put("numericOnly", true);

            // Input "abcdef" fails maxLength (6 > 3) and numericOnly (letters)
            // maxLength error should appear first
            ValidationResult result = service.validate("abcdef", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Input must not exceed 3 characters");
        }

        @Test
        @DisplayName("numericOnly fails before pattern is checked")
        void numericOnlyBeforePattern() {
            Map<String, Object> config = new HashMap<>();
            config.put("numericOnly", true);
            config.put("pattern", "^[A-Z]+$");

            // Input "ABC" fails numericOnly (not digits) and pattern would pass
            // numericOnly error should appear first
            ValidationResult result = service.validate("ABC", config);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("Only numeric characters are allowed");
        }

        @Test
        @DisplayName("full order: required → minLength → maxLength → numericOnly → pattern")
        void fullOrderVerification() {
            // Use custom messages to identify which rule triggered
            Map<String, Object> errorMessages = new HashMap<>();
            errorMessages.put("required", "ERR_REQUIRED");
            errorMessages.put("minLength", "ERR_MINLENGTH");
            errorMessages.put("maxLength", "ERR_MAXLENGTH");
            errorMessages.put("numericOnly", "ERR_NUMERIC");
            errorMessages.put("pattern", "ERR_PATTERN");

            Map<String, Object> config = new HashMap<>();
            config.put("required", true);
            config.put("minLength", 5);
            config.put("maxLength", 3); // contradictory with minLength — that's ok for order test
            config.put("numericOnly", true);
            config.put("pattern", "^[0-9]+$");
            config.put("errorMessages", errorMessages);

            // Test 1: whitespace input — fails required
            ValidationResult r1 = service.validate("   ", config);
            assertThat(r1.getErrorMessage()).isEqualTo("ERR_REQUIRED");

            // Test 2: "ab" — passes required, fails minLength (length 2 < 5)
            ValidationResult r2 = service.validate("ab", config);
            assertThat(r2.getErrorMessage()).isEqualTo("ERR_MINLENGTH");

            // Test 3: "abcdefgh" — passes required, passes minLength (8 >= 5), fails maxLength (8 > 3)
            ValidationResult r3 = service.validate("abcdefgh", config);
            assertThat(r3.getErrorMessage()).isEqualTo("ERR_MAXLENGTH");
        }

        @Test
        @DisplayName("when only numericOnly and pattern fail, numericOnly error is returned")
        void numericOnlyAndPatternBothFail() {
            Map<String, Object> errorMessages = new HashMap<>();
            errorMessages.put("numericOnly", "ERR_NUMERIC");
            errorMessages.put("pattern", "ERR_PATTERN");

            Map<String, Object> config = new HashMap<>();
            config.put("minLength", 1);
            config.put("maxLength", 100);
            config.put("numericOnly", true);
            config.put("pattern", "^[0-9]{5}$");
            config.put("errorMessages", errorMessages);

            // "abc" passes minLength and maxLength, fails numericOnly (letters) and pattern (not 5 digits)
            ValidationResult result = service.validate("abc", config);
            assertThat(result.getErrorMessage()).isEqualTo("ERR_NUMERIC");
        }
    }
}
