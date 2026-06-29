package com.xpressbees.chatbot.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWorkflowNotFound(WorkflowNotFoundException ex) {
        Map<String, Object> body = Map.of(
                "error", "Workflow not found",
                "id", ex.getId()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ApiConfigNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleApiConfigNotFound(ApiConfigNotFoundException ex) {
        Map<String, Object> body = Map.of(
                "error", "ApiConfig not found",
                "id", ex.getId()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DuplicateApiConfigNameException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateApiConfigName(DuplicateApiConfigNameException ex) {
        Map<String, Object> body = Map.of(
                "error", "Conflict",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidMethodException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidMethod(InvalidMethodException ex) {
        Map<String, Object> body = Map.of(
                "error", "Validation failed",
                "message", ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = extractConstraintMessage(ex);
        Map<String, Object> body = Map.of(
                "error", "Validation failed",
                "message", message
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(Map.of("error", "Validation failed", "violations", violations));
    }

    private String extractConstraintMessage(DataIntegrityViolationException ex) {
        String rootMessage = ex.getMostSpecificCause().getMessage();

        if (rootMessage != null && rootMessage.contains("uq_api_response_mapping_api_id_ctx_var")) {
            String duplicateValue = extractDuplicateValue(rootMessage);
            if (duplicateValue != null) {
                return "Duplicate context_variable_name: '" + duplicateValue + "' for this API configuration";
            }
            return "Duplicate context_variable_name for this API configuration";
        }

        return rootMessage != null ? rootMessage : "Data integrity violation";
    }

    private String extractDuplicateValue(String message) {
        // PostgreSQL unique violation messages typically contain: Detail: Key (col1, col2)=(val1, val2) already exists.
        Pattern pattern = Pattern.compile("Key \\(api_id, context_variable_name\\)=\\(\\d+,\\s*(.+?)\\) already exists");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Fallback: try to extract value from a simpler pattern
        Pattern fallback = Pattern.compile("context_variable_name\\)=\\([^,]+,\\s*(.+?)\\) already exists");
        Matcher fallbackMatcher = fallback.matcher(message);
        if (fallbackMatcher.find()) {
            return fallbackMatcher.group(1).trim();
        }

        return null;
    }
}
