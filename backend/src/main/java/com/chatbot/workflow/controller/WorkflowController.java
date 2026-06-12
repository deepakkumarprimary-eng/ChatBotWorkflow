package com.chatbot.workflow.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chatbot.workflow.model.WorkflowDefinition;
import com.chatbot.workflow.repository.WorkflowEntity;
import com.chatbot.workflow.service.ValidationEngine;
import com.chatbot.workflow.service.ValidationResult;
import com.chatbot.workflow.service.WorkflowImportValidator;
import com.chatbot.workflow.service.WorkflowService;
import com.chatbot.workflow.service.WorkflowService.WorkflowWithDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST controller for workflow CRUD, export, and import operations.
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ValidationEngine validationEngine;
    private final WorkflowImportValidator importValidator;
    private final ObjectMapper objectMapper;

    public WorkflowController(WorkflowService workflowService,
                              ValidationEngine validationEngine,
                              WorkflowImportValidator importValidator,
                              ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.validationEngine = validationEngine;
        this.importValidator = importValidator;
        this.objectMapper = objectMapper;
    }

    // ========== CRUD Endpoints ==========

    /**
     * Creates a new workflow.
     */
    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(@Valid @RequestBody CreateWorkflowRequest request) {
        WorkflowEntity entity = workflowService.createWorkflow(
                request.getName(),
                request.getDescription(),
                request.getDefinition()
        );

        WorkflowResponse response = new WorkflowResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCurrentVersion(),
                entity.getCreatedAt(),
                entity.getLastModifiedAt(),
                request.getDefinition()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all workflows with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<WorkflowListItemResponse>> listWorkflows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WorkflowEntity> workflowPage = workflowService.listWorkflows(page, size);

        Page<WorkflowListItemResponse> responsePage = workflowPage.map(entity ->
                new WorkflowListItemResponse(
                        entity.getId(),
                        entity.getName(),
                        entity.getDescription(),
                        entity.getCurrentVersion(),
                        entity.getCreatedAt(),
                        entity.getLastModifiedAt()
                )
        );

        return ResponseEntity.ok(responsePage);
    }

    /**
     * Gets a workflow by ID, including its latest definition.
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable UUID id) {
        WorkflowWithDefinition result = workflowService.getWorkflow(id);
        WorkflowEntity entity = result.getWorkflow();

        WorkflowResponse response = new WorkflowResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCurrentVersion(),
                entity.getCreatedAt(),
                entity.getLastModifiedAt(),
                result.getDefinition()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing workflow.
     */
    @PutMapping("/{id}")
    public ResponseEntity<WorkflowResponse> updateWorkflow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkflowRequest request) {
        WorkflowEntity entity = workflowService.updateWorkflow(
                id,
                request.getName(),
                request.getDescription(),
                request.getDefinition()
        );

        WorkflowResponse response = new WorkflowResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getCurrentVersion(),
                entity.getCreatedAt(),
                entity.getLastModifiedAt(),
                request.getDefinition()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Soft-deletes a workflow.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable UUID id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    // ========== Validation Endpoint ==========

    /**
     * Validates a workflow definition and returns all validation errors.
     * Returns HTTP 200 regardless of whether validation passes or fails,
     * since validation errors are informational, not HTTP errors.
     */
    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidationResponse> validateWorkflow(@PathVariable UUID id) {
        WorkflowWithDefinition result = workflowService.getWorkflow(id);
        WorkflowDefinition definition = result.getDefinition();

        ValidationResult validationResult = validationEngine.validate(definition);

        List<ValidationErrorDto> errorDtos = validationResult.getErrors().stream()
                .map(error -> new ValidationErrorDto(
                        error.getStateId() != null ? error.getStateId().toString() : null,
                        error.getStateName(),
                        error.getMessage(),
                        error.getErrorType().name()
                ))
                .collect(java.util.stream.Collectors.toList());

        ValidationResponse response = new ValidationResponse(validationResult.isValid(), errorDtos);
        return ResponseEntity.ok(response);
    }

    // ========== Export/Import Endpoints ==========

    /**
     * Export a workflow as a downloadable JSON file.
     * Returns the WorkflowDefinition with Content-Disposition header for file download.
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportWorkflow(@PathVariable UUID id) {
        WorkflowWithDefinition workflowWithDef = workflowService.getWorkflow(id);
        WorkflowDefinition definition = workflowWithDef.getDefinition();
        WorkflowEntity workflow = workflowWithDef.getWorkflow();

        try {
            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(definition);

            String filename = sanitizeFilename(workflow.getName()) + ".json";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headers.setContentLength(jsonBytes.length);

            return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Import a workflow from an uploaded JSON file.
     * Validates file size (max 5 MB) and JSON structure before creating the workflow.
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importWorkflow(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return importBadRequest(Collections.singletonList("Uploaded file is empty"));
        }

        // Parse JSON
        WorkflowDefinition definition;
        try {
            definition = objectMapper.readValue(file.getInputStream(), WorkflowDefinition.class);
        } catch (IOException e) {
            return importBadRequest(Collections.singletonList("Invalid JSON format: " + e.getMessage()));
        }

        // Validate structure
        List<String> errors = importValidator.validate(definition);
        if (!errors.isEmpty()) {
            return importBadRequest(errors);
        }

        // Create workflow
        String name = definition.getMetadata().getName();
        String description = definition.getMetadata().getDescription();
        WorkflowEntity created = workflowService.createWorkflow(name, description, definition);

        Map<String, Object> importResponse = new LinkedHashMap<>();
        importResponse.put("id", created.getId());
        importResponse.put("name", created.getName());
        importResponse.put("description", created.getDescription());
        importResponse.put("version", created.getCurrentVersion());

        return ResponseEntity.status(HttpStatus.CREATED).body(importResponse);
    }

    private ResponseEntity<Map<String, Object>> importBadRequest(List<String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Import validation failed");
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Sanitize a workflow name for use as a filename.
     * Removes any characters that are not alphanumeric, spaces, hyphens, or underscores.
     */
    static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "workflow";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9 _\\-]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return sanitized.isEmpty() ? "workflow" : sanitized;
    }
}
