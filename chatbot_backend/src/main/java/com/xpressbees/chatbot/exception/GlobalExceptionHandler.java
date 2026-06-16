package com.xpressbees.chatbot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

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
}
