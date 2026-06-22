package com.xpressbees.chatbot.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xpressbees.chatbot.dto.ApiConfigRequest;
import com.xpressbees.chatbot.dto.ApiConfigResponse;
import com.xpressbees.chatbot.service.ApiConfigService;

@RestController
@RequestMapping("/api/api-configs")
@CrossOrigin("*")
public class ApiConfigController {

    private final ApiConfigService apiConfigService;

    public ApiConfigController(ApiConfigService apiConfigService) {
        this.apiConfigService = apiConfigService;
    }

    @PostMapping
    public ResponseEntity<ApiConfigResponse> createApiConfig(@RequestBody ApiConfigRequest request) {
        ApiConfigResponse response = apiConfigService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiConfigResponse> getApiConfig(@PathVariable String id) {
        Long validatedId = validateId(id);
        ApiConfigResponse response = apiConfigService.getById(validatedId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiConfigResponse>> listApiConfigs() {
        List<ApiConfigResponse> responses = apiConfigService.listAll();
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiConfigResponse> updateApiConfig(@PathVariable String id, @RequestBody ApiConfigRequest request) {
        Long validatedId = validateId(id);
        ApiConfigResponse response = apiConfigService.update(validatedId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiConfig(@PathVariable String id) {
        Long validatedId = validateId(id);
        apiConfigService.delete(validatedId);
        return ResponseEntity.noContent().build();
    }

    private Long validateId(String id) {
        try {
            long parsedId = Long.parseLong(id);
            if (parsedId <= 0) {
                throw new IllegalArgumentException("id must be a positive integer");
            }
            return parsedId;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("id must be a positive integer");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", "Validation failed");
        errorBody.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }
}
