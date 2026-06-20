package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class InputValidationServiceImpl implements InputValidationService {

    private static final Logger log = LoggerFactory.getLogger(InputValidationServiceImpl.class);

    // Default error messages
    private static final String DEFAULT_REQUIRED_MSG = "This field is required";
    private static final String DEFAULT_MIN_LENGTH_MSG = "Input must be at least %d characters";
    private static final String DEFAULT_MAX_LENGTH_MSG = "Input must not exceed %d characters";
    private static final String DEFAULT_NUMERIC_ONLY_MSG = "Only numeric characters are allowed";
    private static final String DEFAULT_PATTERN_MSG = "Input must match the required format";

    @Override
    public ValidationResult validate(String input, Map<String, Object> validationConfig) {
        if (validationConfig == null || validationConfig.isEmpty()) {
            return ValidationResult.success();
        }

        Map<String, Object> errorMessages = extractErrorMessages(validationConfig);

        // Rule 1: required
        ValidationResult result = validateRequired(input, validationConfig, errorMessages);
        if (!result.isValid()) return result;

        // Rule 2: minLength (uses trimmed input)
        result = validateMinLength(input, validationConfig, errorMessages);
        if (!result.isValid()) return result;

        // Rule 3: maxLength (uses trimmed input)
        result = validateMaxLength(input, validationConfig, errorMessages);
        if (!result.isValid()) return result;

        // Rule 4: numericOnly
        result = validateNumericOnly(input, validationConfig, errorMessages);
        if (!result.isValid()) return result;

        // Rule 5: pattern (uses untrimmed input)
        result = validatePattern(input, validationConfig, errorMessages);
        if (!result.isValid()) return result;

        return ValidationResult.success();
    }

    /**
     * Extracts the errorMessages map from the validation config.
     * Returns an empty map if absent or not a valid map type.
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> extractErrorMessages(Map<String, Object> validationConfig) {
        Object errorMessagesObj = validationConfig.get("errorMessages");
        if (errorMessagesObj instanceof Map) {
            return (Map<String, Object>) errorMessagesObj;
        }
        return Collections.emptyMap();
    }

    /**
     * Validates the required rule.
     * Rejects if required == true and input is null, empty, or whitespace-only.
     * Treats non-boolean required values as false (fail-open).
     */
    ValidationResult validateRequired(String input, Map<String, Object> validationConfig, Map<String, Object> errorMessages) {
        Object requiredObj = validationConfig.get("required");

        // Fail-open: treat non-boolean values as not-required
        if (!(requiredObj instanceof Boolean)) {
            return ValidationResult.success();
        }

        boolean required = (Boolean) requiredObj;
        if (!required) {
            return ValidationResult.success();
        }

        // required == true: reject null, empty, or whitespace-only input
        if (input == null || input.trim().isEmpty()) {
            String message = getCustomMessage(errorMessages, "required", DEFAULT_REQUIRED_MSG);
            return ValidationResult.failure(message);
        }

        return ValidationResult.success();
    }

    /**
     * Validates the minLength rule.
     * Rejects if the trimmed input length is less than the specified minimum.
     * Skips if the value is not an integer or is negative (fail-open, log warning).
     */
    ValidationResult validateMinLength(String input, Map<String, Object> validationConfig, Map<String, Object> errorMessages) {
        Object minLengthObj = validationConfig.get("minLength");

        if (minLengthObj == null) {
            return ValidationResult.success();
        }

        // Convert to integer — support Integer and Number types
        int minLength;
        if (minLengthObj instanceof Integer) {
            minLength = (Integer) minLengthObj;
        } else if (minLengthObj instanceof Number) {
            minLength = ((Number) minLengthObj).intValue();
        } else {
            log.warn("minLength value is not a valid integer: '{}'. Skipping minLength validation.", minLengthObj);
            return ValidationResult.success();
        }

        // Skip if negative (fail-open)
        if (minLength < 0) {
            log.warn("minLength value is negative: {}. Skipping minLength validation.", minLength);
            return ValidationResult.success();
        }

        // Compare trimmed input length against minLength
        String trimmedInput = (input == null) ? "" : input.trim();
        if (trimmedInput.length() < minLength) {
            String message = getCustomMessage(errorMessages, "minLength", String.format(DEFAULT_MIN_LENGTH_MSG, minLength));
            return ValidationResult.failure(message);
        }

        return ValidationResult.success();
    }

    /**
     * Validates the maxLength rule.
     * Rejects if the trimmed input length exceeds the specified maximum.
     * Skips if the value is not an integer or is negative (fail-open, log warning).
     */
    ValidationResult validateMaxLength(String input, Map<String, Object> validationConfig, Map<String, Object> errorMessages) {
        Object maxLengthObj = validationConfig.get("maxLength");

        if (maxLengthObj == null) {
            return ValidationResult.success();
        }

        // Convert to integer — support Integer and Number types
        int maxLength;
        if (maxLengthObj instanceof Integer) {
            maxLength = (Integer) maxLengthObj;
        } else if (maxLengthObj instanceof Number) {
            maxLength = ((Number) maxLengthObj).intValue();
        } else {
            log.warn("maxLength value is not a valid integer: '{}'. Skipping maxLength validation.", maxLengthObj);
            return ValidationResult.success();
        }

        // Skip if negative (fail-open)
        if (maxLength < 0) {
            log.warn("maxLength value is negative: {}. Skipping maxLength validation.", maxLength);
            return ValidationResult.success();
        }

        // Compare trimmed input length against maxLength
        String trimmedInput = (input == null) ? "" : input.trim();
        if (trimmedInput.length() > maxLength) {
            String message = getCustomMessage(errorMessages, "maxLength", String.format(DEFAULT_MAX_LENGTH_MSG, maxLength));
            return ValidationResult.failure(message);
        }

        return ValidationResult.success();
    }

    /**
     * Validates the numericOnly rule.
     * If numericOnly == true, checks that trimmed input is empty OR every character is an ASCII digit (0-9).
     * Rejects decimal points, negative signs, and Unicode digit characters.
     * Treats non-boolean numericOnly values as false (skip rule).
     */
    ValidationResult validateNumericOnly(String input, Map<String, Object> validationConfig, Map<String, Object> errorMessages) {
        Object numericOnlyObj = validationConfig.get("numericOnly");

        // Fail-open: treat non-boolean values as false (skip rule)
        if (!(numericOnlyObj instanceof Boolean)) {
            return ValidationResult.success();
        }

        boolean numericOnly = (Boolean) numericOnlyObj;
        if (!numericOnly) {
            return ValidationResult.success();
        }

        // Use trimmed input for numericOnly check
        String trimmed = (input == null) ? "" : input.trim();

        // Empty trimmed input passes (empty handling delegated to required rule)
        if (trimmed.isEmpty()) {
            return ValidationResult.success();
        }

        // Check every character is an ASCII digit (0-9)
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                String message = getCustomMessage(errorMessages, "numericOnly", DEFAULT_NUMERIC_ONLY_MSG);
                return ValidationResult.failure(message);
            }
        }

        return ValidationResult.success();
    }

    /**
     * Retrieves a custom error message for the given rule name from the errorMessages map.
     * Falls back to the default message if the key is absent or the value is not a string.
     */
    String getCustomMessage(Map<String, Object> errorMessages, String ruleKey, String defaultMessage) {
        Object customMsg = errorMessages.get(ruleKey);
        if (customMsg instanceof String && !((String) customMsg).isEmpty()) {
            return (String) customMsg;
        }
        return defaultMessage;
    }

    /**
     * Validates the pattern rule.
     * Compiles the pattern as a Java regex and applies Matcher.matches() against the untrimmed input.
     * If the pattern is not a String or is empty, the rule is skipped.
     * If the pattern is invalid (PatternSyntaxException), logs a warning and accepts input (fail-open).
     */
    ValidationResult validatePattern(String input, Map<String, Object> validationConfig, Map<String, Object> errorMessages) {
        Object patternObj = validationConfig.get("pattern");

        // Skip if pattern is not a non-empty string
        if (!(patternObj instanceof String)) {
            return ValidationResult.success();
        }

        String patternStr = (String) patternObj;
        if (patternStr.isEmpty()) {
            return ValidationResult.success();
        }

        Pattern compiledPattern;
        try {
            compiledPattern = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern '{}' in validation config: {}", patternStr, e.getMessage());
            return ValidationResult.success();
        }

        // Apply matcher against the untrimmed (raw) input
        Matcher matcher = compiledPattern.matcher(input);
        if (!matcher.matches()) {
            String message = getCustomMessage(errorMessages, "pattern", DEFAULT_PATTERN_MSG);
            return ValidationResult.failure(message);
        }

        return ValidationResult.success();
    }
}
