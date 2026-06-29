package com.xpressbees.chatbot.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler validation error responses.
 * Tests the handler directly without requiring a full Spring Boot context.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleValidation_singleViolation_returnsHttp400WithProperFormat() throws Exception {
        // Arrange: create a MethodArgumentNotValidException with one field error
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                (MethodParameter) null, bindingResult);

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Validation failed");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> violations = (List<Map<String, String>>) body.get("violations");
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).get("field")).isEqualTo("name");
        assertThat(violations.get(0).get("message")).isEqualTo("must not be blank");
    }

    @Test
    void handleValidation_multipleViolations_returnsAllViolations() throws Exception {
        // Arrange: create a MethodArgumentNotValidException with multiple field errors
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        bindingResult.addError(new FieldError("request", "url", "must not be blank"));
        bindingResult.addError(new FieldError("request", "method", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                (MethodParameter) null, bindingResult);

        // Act
        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Validation failed");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> violations = (List<Map<String, String>>) body.get("violations");
        assertThat(violations).hasSize(3);

        // Verify all violations are present (order may vary)
        assertThat(violations).extracting(v -> v.get("field"))
                .containsExactlyInAnyOrder("name", "url", "method");
        assertThat(violations).extracting(v -> v.get("message"))
                .containsOnly("must not be blank");
    }
}
