package com.xpressbees.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpressbees.chatbot.dto.WorkflowRequest;
import com.xpressbees.chatbot.dto.WorkflowResponse;
import com.xpressbees.chatbot.exception.WorkflowNotFoundException;
import com.xpressbees.chatbot.service.WorkflowCacheService;
import com.xpressbees.chatbot.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for WorkflowController using @WebMvcTest.
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6
 */
@WebMvcTest(WorkflowController.class)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkflowService workflowService;

    @MockBean
    private WorkflowCacheService workflowCacheService;

    /**
     * Test POST /api/workflows returns 201 Created with persisted workflow data.
     * Validates: Requirement 2.1
     */
    @Test
    void createWorkflow_withValidRequest_returns201WithWorkflowData() throws Exception {
        // Arrange
        WorkflowRequest request = new WorkflowRequest();
        request.setName("Test Workflow");
        request.setWorkflowJson(Map.of("nodes", List.of(Map.of("id", "node1", "type", "message"))));

        WorkflowResponse response = buildWorkflowResponse(1L, "Test Workflow",
                Map.of("nodes", List.of(Map.of("id", "node1", "type", "message"))));

        when(workflowService.create(any(WorkflowRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Workflow"))
                .andExpect(jsonPath("$.workflowJson.nodes[0].id").value("node1"))
                .andExpect(jsonPath("$.workflowJson.nodes[0].type").value("message"));

        verify(workflowService).create(any(WorkflowRequest.class));
    }

    /**
     * Test GET /api/workflows/{id} returns 200 OK with correct workflow JSON.
     * Validates: Requirement 2.2
     */
    @Test
    void getWorkflow_withValidId_returns200WithWorkflowJson() throws Exception {
        // Arrange
        WorkflowResponse response = buildWorkflowResponse(1L, "My Workflow",
                Map.of("nodes", List.of(Map.of("id", "n1", "type", "input")),
                        "transitions", List.of(Map.of("from", "n1", "to", "n2"))));

        when(workflowService.getById(1L)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/workflows/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("My Workflow"))
                .andExpect(jsonPath("$.workflowJson.nodes[0].id").value("n1"))
                .andExpect(jsonPath("$.workflowJson.transitions[0].from").value("n1"));

        verify(workflowService).getById(1L);
    }

    /**
     * Test GET /api/workflows/{id} with non-existent ID returns 404 Not Found.
     * Validates: Requirement 2.3
     */
    @Test
    void getWorkflow_withNonExistentId_returns404() throws Exception {
        // Arrange
        when(workflowService.getById(999L)).thenThrow(new WorkflowNotFoundException(999L));

        // Act & Assert
        mockMvc.perform(get("/api/workflows/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Workflow not found"))
                .andExpect(jsonPath("$.id").value(999));

        verify(workflowService).getById(999L);
    }

    /**
     * Test PUT /api/workflows/{id} returns 200 OK with updated data.
     * Validates: Requirement 2.4
     */
    @Test
    void updateWorkflow_withValidRequest_returns200WithUpdatedData() throws Exception {
        // Arrange
        WorkflowRequest request = new WorkflowRequest();
        request.setName("Updated Workflow");
        request.setWorkflowJson(Map.of("nodes", List.of(Map.of("id", "node2", "type", "api"))));

        WorkflowResponse response = buildWorkflowResponse(1L, "Updated Workflow",
                Map.of("nodes", List.of(Map.of("id", "node2", "type", "api"))));

        when(workflowService.update(eq(1L), any(WorkflowRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/workflows/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Workflow"))
                .andExpect(jsonPath("$.workflowJson.nodes[0].type").value("api"));

        verify(workflowService).update(eq(1L), any(WorkflowRequest.class));
        verify(workflowCacheService).evict(1L);
    }

    /**
     * Test DELETE /api/workflows/{id} returns 204 No Content.
     * Validates: Requirement 2.5
     */
    @Test
    void deleteWorkflow_withValidId_returns204NoContent() throws Exception {
        // Arrange
        doNothing().when(workflowService).delete(1L);

        // Act & Assert
        mockMvc.perform(delete("/api/workflows/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(workflowService).delete(1L);
        verify(workflowCacheService).evict(1L);
    }

    /**
     * Test GET /api/workflows returns 200 OK with JSON array of all workflows.
     * Validates: Requirement 2.6
     */
    @Test
    void listWorkflows_returns200WithJsonArray() throws Exception {
        // Arrange
        WorkflowResponse response1 = buildWorkflowResponse(1L, "Workflow A",
                Map.of("nodes", List.of()));
        WorkflowResponse response2 = buildWorkflowResponse(2L, "Workflow B",
                Map.of("nodes", List.of(Map.of("id", "x", "type", "message"))));

        when(workflowService.listAll()).thenReturn(List.of(response1, response2));

        // Act & Assert
        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Workflow A"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Workflow B"));

        verify(workflowService).listAll();
    }

    private WorkflowResponse buildWorkflowResponse(Long id, String name, Object workflowJson) {
        WorkflowResponse response = new WorkflowResponse();
        response.setId(id);
        response.setName(name);
        response.setWorkflowJson(workflowJson);
        response.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 0, 0));
        response.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 0, 0));
        return response;
    }
}
