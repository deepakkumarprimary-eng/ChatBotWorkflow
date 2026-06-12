package com.chatbot.workflow.controller;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbot.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for the WorkflowController REST endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createWorkflow_withValidRequest_returns201() throws Exception {
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "Test Workflow", "A test workflow", null);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Test Workflow")))
                .andExpect(jsonPath("$.description", is("A test workflow")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.lastModifiedAt", notNullValue()));
    }

    @Test
    void createWorkflow_withEmptyName_returns400() throws Exception {
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "", "A test workflow", null);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation failed")))
                .andExpect(jsonPath("$.details", notNullValue()));
    }

    @Test
    void createWorkflow_withNullName_returns400() throws Exception {
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                null, "A test workflow", null);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation failed")));
    }

    @Test
    void createWorkflow_withNameOver100Chars_returns400() throws Exception {
        String longName = "a".repeat(101);
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                longName, "A test workflow", null);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation failed")))
                .andExpect(jsonPath("$.details", notNullValue()));
    }

    @Test
    void createWorkflow_withNameExactly100Chars_returns201() throws Exception {
        String name100 = "a".repeat(100);
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                name100, "Boundary test", null);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is(name100)));
    }

    @Test
    void getWorkflow_existingId_returns200() throws Exception {
        // Create a workflow first
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "Get Test Workflow", "Description", null);

        MvcResult createResult = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Now get it
        mockMvc.perform(get("/api/workflows/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.name", is("Get Test Workflow")))
                .andExpect(jsonPath("$.description", is("Description")))
                .andExpect(jsonPath("$.version", is(1)));
    }

    @Test
    void getWorkflow_nonExistentId_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/workflows/" + randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Workflow not found")));
    }

    @Test
    void updateWorkflow_existingId_returns200() throws Exception {
        // Create a workflow first
        CreateWorkflowRequest createRequest = new CreateWorkflowRequest(
                "Original Name", "Original Desc", null);

        MvcResult createResult = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Update it
        UpdateWorkflowRequest updateRequest = new UpdateWorkflowRequest(
                "Updated Name", "Updated Desc", null);

        mockMvc.perform(put("/api/workflows/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.description", is("Updated Desc")))
                .andExpect(jsonPath("$.version", is(2)));
    }

    @Test
    void updateWorkflow_nonExistentId_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();
        UpdateWorkflowRequest updateRequest = new UpdateWorkflowRequest(
                "Name", "Desc", null);

        mockMvc.perform(put("/api/workflows/" + randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Workflow not found")));
    }

    @Test
    void updateWorkflow_withInvalidName_returns400() throws Exception {
        // Create a workflow first
        CreateWorkflowRequest createRequest = new CreateWorkflowRequest(
                "Valid Name", "Desc", null);

        MvcResult createResult = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Try to update with invalid name
        UpdateWorkflowRequest updateRequest = new UpdateWorkflowRequest(
                "", "Desc", null);

        mockMvc.perform(put("/api/workflows/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation failed")));
    }

    @Test
    void deleteWorkflow_existingId_returns204() throws Exception {
        // Create a workflow first
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "Delete Test Workflow", "To be deleted", null);

        MvcResult createResult = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Delete it
        mockMvc.perform(delete("/api/workflows/" + id))
                .andExpect(status().isNoContent());

        // Verify it's no longer accessible
        mockMvc.perform(get("/api/workflows/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWorkflow_nonExistentId_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(delete("/api/workflows/" + randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Workflow not found")));
    }

    @Test
    void listWorkflows_returnsPagedResults() throws Exception {
        // Create a few workflows
        for (int i = 0; i < 3; i++) {
            CreateWorkflowRequest request = new CreateWorkflowRequest(
                    "List Test " + i, "Description " + i, null);

            mockMvc.perform(post("/api/workflows")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // List them
        mockMvc.perform(get("/api/workflows")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageable", notNullValue()))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void listWorkflows_withDefaultPagination_returnsResults() throws Exception {
        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void createWorkflow_withDefinition_returns201WithDefinition() throws Exception {
        WorkflowDefinition definition = new WorkflowDefinition(null, null, null, null);
        CreateWorkflowRequest request = new CreateWorkflowRequest(
                "With Definition", "Has a definition", definition);

        mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("With Definition")))
                .andExpect(jsonPath("$.version", is(1)));
    }
}
