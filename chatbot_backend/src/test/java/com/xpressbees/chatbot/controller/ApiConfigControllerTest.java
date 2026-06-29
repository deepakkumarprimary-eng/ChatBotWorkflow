package com.xpressbees.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpressbees.chatbot.dto.ApiConfigRequest;
import com.xpressbees.chatbot.dto.ApiConfigResponse;
import com.xpressbees.chatbot.exception.ApiConfigNotFoundException;
import com.xpressbees.chatbot.exception.DuplicateApiConfigNameException;
import com.xpressbees.chatbot.exception.GlobalExceptionHandler;
import com.xpressbees.chatbot.exception.InvalidMethodException;
import com.xpressbees.chatbot.service.ApiConfigCacheService;
import com.xpressbees.chatbot.service.ApiConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for ApiConfigController.
 * Uses @WebMvcTest to load only the web layer (controller + exception handlers).
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
@WebMvcTest(ApiConfigController.class)
class ApiConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApiConfigService apiConfigService;

    @MockBean
    private ApiConfigCacheService apiConfigCacheService;

    /**
     * Test POST /api/api-configs with valid request returns 201 Created.
     * Validates: Requirement 3.1
     */
    @Test
    void createApiConfig_withValidRequest_returns201Created() throws Exception {
        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("payment-api");
        request.setUrl("https://api.example.com/pay");
        request.setMethod("POST");

        ApiConfigResponse response = new ApiConfigResponse();
        response.setId(1L);
        response.setName("payment-api");
        response.setUrl("https://api.example.com/pay");
        response.setMethod("POST");

        when(apiConfigService.create(any(ApiConfigRequest.class))).thenReturn(response);
        doNothing().when(apiConfigCacheService).evict(any(), any());

        mockMvc.perform(post("/api/api-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("payment-api"))
                .andExpect(jsonPath("$.url").value("https://api.example.com/pay"))
                .andExpect(jsonPath("$.method").value("POST"));
    }

    /**
     * Test POST /api/api-configs with missing name returns 400 Bad Request.
     * Validates: Requirement 3.2
     */
    @Test
    void createApiConfig_withMissingName_returns400BadRequest() throws Exception {
        ApiConfigRequest request = new ApiConfigRequest();
        request.setUrl("https://api.example.com/pay");
        request.setMethod("POST");

        mockMvc.perform(post("/api/api-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    /**
     * Test POST /api/api-configs with missing url returns 400 Bad Request.
     * Validates: Requirement 3.2
     */
    @Test
    void createApiConfig_withMissingUrl_returns400BadRequest() throws Exception {
        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("payment-api");
        request.setMethod("POST");

        mockMvc.perform(post("/api/api-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    /**
     * Test POST /api/api-configs with missing method returns 400 Bad Request.
     * Validates: Requirement 3.2
     */
    @Test
    void createApiConfig_withMissingMethod_returns400BadRequest() throws Exception {
        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("payment-api");
        request.setUrl("https://api.example.com/pay");

        mockMvc.perform(post("/api/api-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    /**
     * Test POST /api/api-configs with duplicate name returns 409 Conflict.
     * Validates: Requirement 3.3
     */
    @Test
    void createApiConfig_withDuplicateName_returns409Conflict() throws Exception {
        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("payment-api");
        request.setUrl("https://api.example.com/pay");
        request.setMethod("POST");

        when(apiConfigService.create(any(ApiConfigRequest.class)))
                .thenThrow(new DuplicateApiConfigNameException("payment-api"));

        mockMvc.perform(post("/api/api-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("ApiConfig with name 'payment-api' already exists"));
    }

    /**
     * Test POST /api/api-configs with invalid HTTP method returns 400 Bad Request.
     * Validates: Requirement 3.4
     */
    @Test
    void createApiConfig_withInvalidMethod_returns400BadRequest() throws Exception {
        ApiConfigRequest request = new ApiConfigRequest();
        request.setName("payment-api");
        request.setUrl("https://api.example.com/pay");
        request.setMethod("PATCH");

        when(apiConfigService.create(any(ApiConfigRequest.class)))
                .thenThrow(new InvalidMethodException("PATCH"));

        mockMvc.perform(post("/api/api-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message").value("method must be one of: GET, POST, PUT, DELETE"));
    }

    /**
     * Test GET /api/api-configs/{id} with non-numeric ID returns 400 Bad Request.
     * Validates: Requirement 3.5
     */
    @Test
    void getApiConfig_withNonNumericId_returns400BadRequest() throws Exception {
        mockMvc.perform(get("/api/api-configs/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.message").value("id must be a positive integer"));
    }

    /**
     * Test GET /api/api-configs/{id} with non-existent numeric ID returns 404 Not Found.
     * Validates: Requirement 3.6
     */
    @Test
    void getApiConfig_withNonExistentId_returns404NotFound() throws Exception {
        when(apiConfigService.getById(999L))
                .thenThrow(new ApiConfigNotFoundException(999L));

        mockMvc.perform(get("/api/api-configs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ApiConfig not found"))
                .andExpect(jsonPath("$.id").value(999));
    }

    /**
     * Test DELETE /api/api-configs/{id} with valid ID returns 204 No Content.
     * Validates: Requirement 3.7
     */
    @Test
    void deleteApiConfig_withValidId_returns204NoContent() throws Exception {
        ApiConfigResponse existingConfig = new ApiConfigResponse();
        existingConfig.setId(1L);
        existingConfig.setName("payment-api");

        when(apiConfigService.getById(1L)).thenReturn(existingConfig);
        doNothing().when(apiConfigService).delete(1L);
        doNothing().when(apiConfigCacheService).evict(any(), any());

        mockMvc.perform(delete("/api/api-configs/1"))
                .andExpect(status().isNoContent());
    }
}
