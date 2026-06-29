package com.xpressbees.chatbot.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xpressbees.chatbot.dto.WorkflowRequest;
import com.xpressbees.chatbot.dto.WorkflowResponse;
import com.xpressbees.chatbot.service.WorkflowCacheService;
import com.xpressbees.chatbot.service.WorkflowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/workflows")
@Tag(name = "Workflows", description = "Workflow CRUD operations")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowCacheService workflowCacheService;

    public WorkflowController(WorkflowService workflowService, WorkflowCacheService workflowCacheService) {
        this.workflowService = workflowService;
        this.workflowCacheService = workflowCacheService;
    }

    @Operation(summary = "Create a workflow", description = "Creates a new workflow definition with the provided name and workflow JSON structure")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Workflow created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content)
    })
    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(@Valid @RequestBody WorkflowRequest request) {
        WorkflowResponse response = workflowService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get a workflow by ID", description = "Retrieves a single workflow definition by its unique identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workflow found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowResponse.class))),
        @ApiResponse(responseCode = "404", description = "Workflow not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(
            @Parameter(description = "Workflow ID", required = true) @PathVariable Long id) {
        WorkflowResponse response = workflowService.getById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List all workflows", description = "Retrieves all workflow definitions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of workflows",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = WorkflowResponse.class))))
    })
    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> listWorkflows() {
        List<WorkflowResponse> responses = workflowService.listAll();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Update a workflow", description = "Updates an existing workflow definition by its ID with the provided name and workflow JSON structure")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workflow updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkflowResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
        @ApiResponse(responseCode = "404", description = "Workflow not found", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<WorkflowResponse> updateWorkflow(
            @Parameter(description = "Workflow ID", required = true) @PathVariable Long id,
            @Valid @RequestBody WorkflowRequest request) {
        WorkflowResponse response = workflowService.update(id, request);
        workflowCacheService.evict(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a workflow", description = "Deletes a workflow definition by its ID and evicts it from cache")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Workflow deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Workflow not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(
            @Parameter(description = "Workflow ID", required = true) @PathVariable Long id) {
        workflowService.delete(id);
        workflowCacheService.evict(id);
        return ResponseEntity.noContent().build();
    }
}
