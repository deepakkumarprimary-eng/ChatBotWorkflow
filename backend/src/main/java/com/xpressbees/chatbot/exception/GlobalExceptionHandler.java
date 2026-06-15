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
}
