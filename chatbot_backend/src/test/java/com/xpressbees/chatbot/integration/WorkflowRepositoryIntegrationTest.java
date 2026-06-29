package com.xpressbees.chatbot.integration;

import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for WorkflowRepository verifying JSONB column round-trip preservation.
 * Tests that saving a workflow with a complex workflowJson (nodes + transitions)
 * persists and retrieves the complete structure without data loss.
 *
 * Validates: Requirements 1.2
 */
class WorkflowRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Test
    void shouldPersistAndRetrieveWorkflowJsonWithNodesAndTransitions() {
        // Arrange: build a complex workflowJson with nodes and transitions
        Map<String, Object> workflowJson = buildComplexWorkflowJson();

        Workflow workflow = new Workflow();
        workflow.setName("Integration Test Workflow");
        workflow.setWorkflowJson(workflowJson);

        // Act: save and retrieve
        Workflow saved = workflowRepository.save(workflow);
        workflowRepository.flush();

        Workflow retrieved = workflowRepository.findById(saved.getId()).orElseThrow();

        // Assert: JSONB content matches exactly
        assertThat(retrieved.getId()).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("Integration Test Workflow");
        assertThat(retrieved.getWorkflowJson()).isNotNull();
        assertThat(retrieved.getWorkflowJson()).isEqualTo(workflowJson);

        // Verify nodes structure
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) retrieved.getWorkflowJson().get("nodes");
        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(0).get("id")).isEqualTo("msg1");
        assertThat(nodes.get(0).get("type")).isEqualTo("state");
        assertThat(nodes.get(1).get("id")).isEqualTo("inp1");
        assertThat(nodes.get(2).get("id")).isEqualTo("api1");

        // Verify node config with nested objects
        @SuppressWarnings("unchecked")
        Map<String, Object> inputConfig = (Map<String, Object>) nodes.get(1).get("config");
        assertThat(inputConfig).containsEntry("nodeType", "input");
        assertThat(inputConfig).containsEntry("variableName", "userName");

        @SuppressWarnings("unchecked")
        Map<String, Object> apiConfig = (Map<String, Object>) nodes.get(2).get("config");
        assertThat(apiConfig).containsEntry("nodeType", "api");
        assertThat(apiConfig).containsEntry("apiConfigId", "42");

        // Verify transitions structure
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) retrieved.getWorkflowJson().get("transitions");
        assertThat(transitions).hasSize(2);
        assertThat(transitions.get(0)).containsEntry("sourceNodeId", "msg1");
        assertThat(transitions.get(0)).containsEntry("targetNodeId", "inp1");
        assertThat(transitions.get(1)).containsEntry("sourceNodeId", "inp1");
        assertThat(transitions.get(1)).containsEntry("targetNodeId", "api1");
    }

    @Test
    void shouldPersistAndRetrieveWorkflowJsonWithNestedObjects() {
        // Arrange: workflow with deeply nested config
        Map<String, Object> workflowJson = new HashMap<>();

        Map<String, Object> node = new HashMap<>();
        node.put("id", "complex1");
        node.put("name", "Complex Node");
        node.put("type", "state");

        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "api");
        config.put("apiConfigId", "99");
        config.put("displayVariable", "options");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("retryOnFailure", true);
        metadata.put("maxRetries", 3);
        metadata.put("tags", List.of("critical", "external"));
        config.put("metadata", metadata);

        node.put("config", config);
        workflowJson.put("nodes", List.of(node));
        workflowJson.put("transitions", List.of());
        workflowJson.put("version", "2.0");

        Workflow workflow = new Workflow();
        workflow.setName("Nested Config Workflow");
        workflow.setWorkflowJson(workflowJson);

        // Act
        Workflow saved = workflowRepository.save(workflow);
        workflowRepository.flush();

        Workflow retrieved = workflowRepository.findById(saved.getId()).orElseThrow();

        // Assert: deeply nested content preserved
        assertThat(retrieved.getWorkflowJson()).isEqualTo(workflowJson);
        assertThat(retrieved.getWorkflowJson()).containsEntry("version", "2.0");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) retrieved.getWorkflowJson().get("nodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedConfig = (Map<String, Object>) nodes.get(0).get("config");
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedMetadata = (Map<String, Object>) retrievedConfig.get("metadata");

        assertThat(retrievedMetadata).containsEntry("retryOnFailure", true);
        assertThat(retrievedMetadata).containsEntry("maxRetries", 3);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) retrievedMetadata.get("tags");
        assertThat(tags).containsExactly("critical", "external");
    }

    @Test
    void shouldPersistNullWorkflowJson() {
        // Arrange: workflow with null workflowJson
        Workflow workflow = new Workflow();
        workflow.setName("Empty Workflow");
        workflow.setWorkflowJson(null);

        // Act
        Workflow saved = workflowRepository.save(workflow);
        workflowRepository.flush();

        Workflow retrieved = workflowRepository.findById(saved.getId()).orElseThrow();

        // Assert
        assertThat(retrieved.getName()).isEqualTo("Empty Workflow");
        assertThat(retrieved.getWorkflowJson()).isNull();
    }

    private Map<String, Object> buildComplexWorkflowJson() {
        Map<String, Object> workflowJson = new HashMap<>();

        // Nodes
        Map<String, Object> msgNode = new HashMap<>();
        msgNode.put("id", "msg1");
        msgNode.put("name", "Welcome! How can I help?");
        msgNode.put("type", "state");
        msgNode.put("config", null);

        Map<String, Object> inputNode = new HashMap<>();
        inputNode.put("id", "inp1");
        inputNode.put("name", "Please enter your name:");
        inputNode.put("type", "state");
        Map<String, Object> inputConfig = new HashMap<>();
        inputConfig.put("nodeType", "input");
        inputConfig.put("variableName", "userName");
        inputNode.put("config", inputConfig);

        Map<String, Object> apiNode = new HashMap<>();
        apiNode.put("id", "api1");
        apiNode.put("name", "Looking up your account...");
        apiNode.put("type", "state");
        Map<String, Object> apiNodeConfig = new HashMap<>();
        apiNodeConfig.put("nodeType", "api");
        apiNodeConfig.put("apiConfigId", "42");
        apiNode.put("config", apiNodeConfig);

        workflowJson.put("nodes", List.of(msgNode, inputNode, apiNode));

        // Transitions
        Map<String, Object> transition1 = new HashMap<>();
        transition1.put("sourceNodeId", "msg1");
        transition1.put("targetNodeId", "inp1");

        Map<String, Object> transition2 = new HashMap<>();
        transition2.put("sourceNodeId", "inp1");
        transition2.put("targetNodeId", "api1");

        workflowJson.put("transitions", List.of(transition1, transition2));

        return workflowJson;
    }
}
