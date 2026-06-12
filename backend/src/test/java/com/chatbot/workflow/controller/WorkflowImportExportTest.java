package com.chatbot.workflow.controller;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbot.workflow.model.Position;
import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;
import com.chatbot.workflow.model.WorkflowDefinition;
import com.chatbot.workflow.model.WorkflowMetadata;
import com.chatbot.workflow.repository.WorkflowEntity;
import com.chatbot.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowImportExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowService workflowService;

    @Test
    void exportWorkflow_returnsJsonFileWithCorrectHeaders() throws Exception {
        // Create a workflow to export
        WorkflowDefinition definition = createSampleDefinition("Export Test Workflow");
        WorkflowEntity created = workflowService.createWorkflow(
                "Export Test Workflow", "A test workflow", definition);

        mockMvc.perform(get("/api/workflows/{id}/export", created.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"Export_Test_Workflow.json\"")))
                .andExpect(jsonPath("$.metadata.name").value("Export Test Workflow"))
                .andExpect(jsonPath("$.states").isArray())
                .andExpect(jsonPath("$.transitions").isArray());
    }

    @Test
    void exportWorkflow_returns404ForNonExistentWorkflow() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/workflows/{id}/export", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Workflow not found"))
                .andExpect(jsonPath("$.workflowId").value(nonExistentId.toString()));
    }

    @Test
    void importWorkflow_successfulImport() throws Exception {
        WorkflowDefinition definition = createSampleDefinition("Imported Workflow");
        byte[] fileContent = objectMapper.writeValueAsBytes(definition);

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, fileContent);

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Imported Workflow"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void importWorkflow_rejectsMissingMetadata() throws Exception {
        String json = "{\"states\":[], \"transitions\":[]}";

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("metadata is required"))));
    }

    @Test
    void importWorkflow_rejectsMissingMetadataName() throws Exception {
        String json = "{\"metadata\":{\"version\":1}, \"states\":[], \"transitions\":[]}";

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("metadata.name is required"))));
    }

    @Test
    void importWorkflow_rejectsMissingStates() throws Exception {
        String json = "{\"metadata\":{\"name\":\"Test\",\"version\":1}, \"transitions\":[]}";

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("states list is required"))));
    }

    @Test
    void importWorkflow_rejectsMissingTransitions() throws Exception {
        String json = "{\"metadata\":{\"name\":\"Test\",\"version\":1}, \"states\":[]}";

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("transitions list is required"))));
    }

    @Test
    void importWorkflow_rejectsInvalidStateType() throws Exception {
        // State with type that doesn't deserialize (unknown type)
        String json = "{"
                + "\"metadata\":{\"name\":\"Test\",\"version\":1},"
                + "\"states\":[{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"INVALID_TYPE\",\"name\":\"Bad State\"}],"
                + "\"transitions\":[]"
                + "}";

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void importWorkflow_rejectsStateWithNullId() throws Exception {
        String json = "{"
                + "\"metadata\":{\"name\":\"Test\",\"version\":1},"
                + "\"states\":[{\"type\":\"end\",\"name\":\"End\"}],"
                + "\"transitions\":[]"
                + "}";

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("states[0].id is required"))));
    }

    @Test
    void importWorkflow_rejectsTransitionWithNullSource() throws Exception {
        String json = "{"
                + "\"metadata\":{\"name\":\"Test\",\"version\":1},"
                + "\"states\":[],"
                + "\"transitions\":[{\"id\":\"" + UUID.randomUUID() + "\",\"target\":\"" + UUID.randomUUID() + "\"}]"
                + "}";

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("transitions[0].source is required"))));
    }

    @Test
    void importWorkflow_rejectsMalformedJson() throws Exception {
        String json = "{ this is not valid json }}}";

        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes());

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString("Invalid JSON format"))));
    }

    @Test
    void importWorkflow_rejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void exportThenImport_roundTrip() throws Exception {
        // Create and export
        WorkflowDefinition definition = createSampleDefinition("Round Trip Workflow");
        WorkflowEntity created = workflowService.createWorkflow(
                "Round Trip Workflow", "Round trip test", definition);

        MvcResult exportResult = mockMvc.perform(get("/api/workflows/{id}/export", created.getId()))
                .andExpect(status().isOk())
                .andReturn();

        byte[] exportedContent = exportResult.getResponse().getContentAsByteArray();

        // Import the exported file
        MockMultipartFile file = new MockMultipartFile(
                "file", "workflow.json", MediaType.APPLICATION_JSON_VALUE, exportedContent);

        mockMvc.perform(multipart("/api/workflows/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Round Trip Workflow"));
    }

    // --- Helper methods ---

    private WorkflowDefinition createSampleDefinition(String name) {
        WorkflowMetadata metadata = new WorkflowMetadata(
                name, "Test description", 1, Instant.now(), Instant.now());

        UUID stateId = UUID.randomUUID();
        StateDefinition endState = new StateDefinition(
                stateId, StateType.END, "End", new Position(100, 100),
                Collections.emptyMap(), null, null);

        return new WorkflowDefinition(
                metadata,
                Collections.singletonList(endState),
                Collections.emptyList(),
                Collections.emptyList());
    }
}
