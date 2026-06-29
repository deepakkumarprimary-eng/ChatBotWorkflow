package com.xpressbees.chatbot.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.xpressbees.chatbot.service.ApiConfigCacheService;
import com.xpressbees.chatbot.service.ApiConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/api-configs")
@Tag(name = "API Configurations", description = "API configuration CRUD operations")
public class ApiConfigController {

    private final ApiConfigService apiConfigService;
    private final ApiConfigCacheService apiConfigCacheService;

    public ApiConfigController(ApiConfigService apiConfigService,
                               ApiConfigCacheService apiConfigCacheService) {
        this.apiConfigService = apiConfigService;
        this.apiConfigCacheService = apiConfigCacheService;
    }

    @Operation(summary = "Create an API configuration", description = "Creates a new API configuration with URL, method, headers, payload template, and response mappings")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "API configuration created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiConfigResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
        @ApiResponse(responseCode = "409", description = "Duplicate API configuration name", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ApiConfigResponse> createApiConfig(@Valid @RequestBody ApiConfigRequest request) {
        ApiConfigResponse response = apiConfigService.create(request);
        apiConfigCacheService.evict(response.getId(), response.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get an API configuration by ID", description = "Retrieves a single API configuration by its unique identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "API configuration found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiConfigResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid ID format", content = @Content),
        @ApiResponse(responseCode = "404", description = "API configuration not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiConfigResponse> getApiConfig(
            @Parameter(description = "API configuration ID", required = true) @PathVariable String id) {
        Long validatedId = validateId(id);
        ApiConfigResponse response = apiConfigService.getById(validatedId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List all API configurations", description = "Retrieves all API configurations")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of API configurations",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = ApiConfigResponse.class))))
    })
    @GetMapping
    public ResponseEntity<List<ApiConfigResponse>> listApiConfigs() {
        List<ApiConfigResponse> responses = apiConfigService.listAll();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Update an API configuration", description = "Updates an existing API configuration by its ID with the provided URL, method, headers, payload template, and response mappings")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "API configuration updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiConfigResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body or ID format", content = @Content),
        @ApiResponse(responseCode = "404", description = "API configuration not found", content = @Content),
        @ApiResponse(responseCode = "409", description = "Duplicate API configuration name", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiConfigResponse> updateApiConfig(
            @Parameter(description = "API configuration ID", required = true) @PathVariable String id,
            @Valid @RequestBody ApiConfigRequest request) {
        Long validatedId = validateId(id);
        // Get the old name before update so we can evict the old name-based cache entry
        ApiConfigResponse existingConfig = apiConfigService.getById(validatedId);
        String oldName = existingConfig.getName();

        ApiConfigResponse response = apiConfigService.update(validatedId, request);

        // Evict by ID and new name
        apiConfigCacheService.evict(response.getId(), response.getName());
        // Also evict the old name if it changed
        if (!oldName.equals(response.getName())) {
            apiConfigCacheService.evict(response.getId(), oldName);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete an API configuration", description = "Deletes an API configuration by its ID and evicts it from cache")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "API configuration deleted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid ID format", content = @Content),
        @ApiResponse(responseCode = "404", description = "API configuration not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiConfig(
            @Parameter(description = "API configuration ID", required = true) @PathVariable String id) {
        Long validatedId = validateId(id);
        // Get the config's name before deletion for cache eviction
        ApiConfigResponse existingConfig = apiConfigService.getById(validatedId);
        apiConfigService.delete(validatedId);
        apiConfigCacheService.evict(validatedId, existingConfig.getName());
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
