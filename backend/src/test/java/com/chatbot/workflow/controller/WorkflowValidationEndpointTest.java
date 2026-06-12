package com.chatbot.workflow.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbot.workflow.model.Position;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.TransitionDefinition;
import com.chatbot.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for the POST /api/workflows/{id}/validate endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowValidationEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validateWorkflow_validWorkflow_returnsValidTrue() throws Exception {
        // Create a valid workflow: Response state -> End state
        UUID responseId = UUID.randomUUID();
        UUID endId = UUID.randomUUID();

        Map<String, Object> responseConfig = new HashMap<>();
        responseConfig.put("template", "Hello {{name}}!");

        StateDefinition responseState = new StateDefinition(
                responseId, StateType.RESPONSE, "Greeting",
                new Position(100, 100), responseConfig, null, null);
        StateDefinition endState = new StateDefinition(
                endId, StateType.END, "Done",
                new Position(200, 200), null, null, null);

        TransitionDefinition transition = new TransitionDefinition(
                UUID.randomUUID(), responseId, endId, null);

        WorkflowDefinition definition = new WorkflowDefinition(
                null,
                Arrays.asList(responseState, endState),
                Collections.singletonList(transition),
                null);

        // Create the workflow via API
        CreateWorkflowRequest createRequest = new CreateWorkflowRequest(
                "Valid Workflow", "A valid workflow for testing validation", definition);

        MvcResult createResult = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Validate the workflow
        mockMvc.perform(post("/api/workflows/" + id + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void validateWorkflow_invalidWorkflow_returnsValidFalseWithErrors() throws Exception {
        // Create an invalid workflow: a single Response state with no outgoing transition
        // and missing config (no "template" field)
        UUID responseId = UUID.randomUUID();

        StateDefinition responseState = new StateDefinition(
                responseId, StateType.RESPONSE, "Broken State",
                new Position(100, 100), null, null, null);

        WorkflowDefinition definition = new WorkflowDefinition(
                null,
                Collections.singletonList(responseState),
                Collections.emptyList(),
                null);

        // Create the workflow
        CreateWorkflowRequest createRequest = new CreateWorkflowRequest(
                "Invalid Workflow", "An invalid workflow for validation", definition);

        MvcResult createResult = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Validate the workflow — should fail with errors
        mockMvc.perform(post("/api/workflows/" + id + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].message").isString())
                .andExpect(jsonPath("$.errors[0].errorType").isString());
    }

    @Test
    void validateWorkflow_emptyWorkflow_returnsValidFalseWithEmptyError() throws Exception {
        // Create an empty workflow (no states)
        WorkflowDefinition definition = new WorkflowDefinition(
                null, Collections.emptyList(), Collections.emptyList(), null);

        CreateWorkflowRequest createRequest = new CreateWorkflowRequest(
                "Empty Workflow", "No states", definition);

        MvcResult createResult = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Validate — should fail with EMPTY_WORKFLOW error
        mockMvc.perform(post("/api/workflows/" + id + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].errorType", is("EMPTY_WORKFLOW")))
                .andExpect(jsonPath("$.errors[0].stateId").doesNotExist())
                .andExpect(jsonPath("$.errors[0].stateName").doesNotExist());
    }

    @Test
    void validateWorkflow_nonExistentId_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(post("/api/workflows/" + randomId + "/validate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Workflow not found")));
    }
}
