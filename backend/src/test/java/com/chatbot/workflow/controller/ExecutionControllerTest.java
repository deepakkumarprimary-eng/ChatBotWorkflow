package com.chatbot.workflow.controller;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for the ExecutionController REST endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Helper to create a workflow with a simple End state (serves as start state since it has no incoming transitions).
     */
    private String createSimpleWorkflow() throws Exception {
        UUID endStateId = UUID.randomUUID();
        String body = "{" +
                "\"name\": \"Test Workflow\"," +
                "\"description\": \"For testing execution\"," +
                "\"definition\": {" +
                "  \"states\": [{" +
                "    \"id\": \"" + endStateId + "\"," +
                "    \"type\": \"END\"," +
                "    \"name\": \"End\"" +
                "  }]," +
                "  \"transitions\": []" +
                "}" +
                "}";

        MvcResult result = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    /**
     * Helper to create a workflow with Response -> End states (produces history entries).
     */
    private String createWorkflowWithHistory() throws Exception {
        UUID responseStateId = UUID.randomUUID();
        UUID endStateId = UUID.randomUUID();
        UUID transitionId = UUID.randomUUID();

        String body = "{" +
                "\"name\": \"History Workflow\"," +
                "\"description\": \"Produces history\"," +
                "\"definition\": {" +
                "  \"states\": [" +
                "    {\"id\": \"" + responseStateId + "\", \"type\": \"RESPONSE\", \"name\": \"Greeting\"," +
                "     \"config\": {\"template\": \"Hello\"}}," +
                "    {\"id\": \"" + endStateId + "\", \"type\": \"END\", \"name\": \"Done\"}" +
                "  ]," +
                "  \"transitions\": [" +
                "    {\"id\": \"" + transitionId + "\", \"source\": \"" + responseStateId + "\"," +
                "     \"target\": \"" + endStateId + "\"}" +
                "  ]" +
                "}" +
                "}";

        MvcResult result = mockMvc.perform(post("/api/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    void executeWorkflow_returns202WithExecutionId() throws Exception {
        String workflowId = createSimpleWorkflow();

        mockMvc.perform(post("/api/workflows/" + workflowId + "/execute"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionId", notNullValue()));
    }

    @Test
    void executeWorkflow_nonExistentWorkflow_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(post("/api/workflows/" + randomId + "/execute"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Workflow not found")));
    }

    @Test
    void getExecution_returnsStatusAndDetails() throws Exception {
        String workflowId = createSimpleWorkflow();

        MvcResult execResult = mockMvc.perform(post("/api/workflows/" + workflowId + "/execute"))
                .andExpect(status().isAccepted())
                .andReturn();

        String executionId = objectMapper.readTree(execResult.getResponse().getContentAsString())
                .get("executionId").asText();

        mockMvc.perform(get("/api/executions/" + executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId", is(executionId)))
                .andExpect(jsonPath("$.workflowId", is(workflowId)))
                .andExpect(jsonPath("$.status", is("completed")))
                .andExpect(jsonPath("$.startTime", notNullValue()))
                .andExpect(jsonPath("$.endTime", notNullValue()))
                .andExpect(jsonPath("$.elapsedTimeMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.history").isArray());
    }

    @Test
    void getExecution_nonExistentExecution_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/executions/" + randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Execution not found")));
    }

    @Test
    void getExecution_withHistory_returnsHistoryEntries() throws Exception {
        String workflowId = createWorkflowWithHistory();

        MvcResult execResult = mockMvc.perform(post("/api/workflows/" + workflowId + "/execute"))
                .andExpect(status().isAccepted())
                .andReturn();

        String executionId = objectMapper.readTree(execResult.getResponse().getContentAsString())
                .get("executionId").asText();

        mockMvc.perform(get("/api/executions/" + executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId", is(executionId)))
                .andExpect(jsonPath("$.status", is("completed")))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history[0].stateId", notNullValue()))
                .andExpect(jsonPath("$.history[0].stateName", notNullValue()))
                .andExpect(jsonPath("$.history[0].entryTime", notNullValue()))
                .andExpect(jsonPath("$.history[0].outcome", notNullValue()));
    }

    @Test
    void listExecutions_returnsPagedResults() throws Exception {
        // Create an execution
        String workflowId = createSimpleWorkflow();
        mockMvc.perform(post("/api/workflows/" + workflowId + "/execute"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/executions")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageable", notNullValue()))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void listExecutions_defaultPageSize_is20() throws Exception {
        mockMvc.perform(get("/api/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageable.pageSize", is(20)));
    }

    @Test
    void listExecutions_pageSizeCappedAt100() throws Exception {
        mockMvc.perform(get("/api/executions")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable.pageSize", is(100)));
    }
}
