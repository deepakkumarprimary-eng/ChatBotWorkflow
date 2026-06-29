package com.xpressbees.chatbot.integration;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying ChatSession persistence with JSONB context
 * column round-trip and status field persistence using a real PostgreSQL
 * database via TestContainers.
 */
class ChatSessionRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    private Workflow savedWorkflow;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
        workflowRepository.deleteAll();

        // ChatSession requires a valid workflow_id (FK constraint)
        Workflow workflow = new Workflow();
        workflow.setName("test-workflow");
        workflow.setWorkflowJson(Map.of("nodes", List.of(), "transitions", List.of()));
        savedWorkflow = workflowRepository.save(workflow);
    }

    @Test
    void shouldRoundTripChatSessionWithJsonbContext() {
        // Arrange: create a ChatSession with rich context data
        Map<String, Object> contextData = Map.of(
                "userName", "John",
                "orderId", 12345,
                "preferences", Map.of("language", "en", "theme", "dark"),
                "tags", List.of("vip", "returning")
        );

        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setWorkflowId(savedWorkflow.getId());
        session.setCurrentNodeId("node_1");
        session.setCurrentType("message");
        session.setCurrentNodeType("message");
        session.setContext(contextData);
        session.setStatus("active");

        // Act: save and retrieve
        ChatSession saved = chatSessionRepository.save(session);
        chatSessionRepository.flush();

        Optional<ChatSession> retrieved = chatSessionRepository.findBySessionId(saved.getSessionId());

        // Assert: all fields round-trip correctly
        assertThat(retrieved).isPresent();
        ChatSession found = retrieved.get();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getSessionId()).isEqualTo(session.getSessionId());
        assertThat(found.getWorkflowId()).isEqualTo(savedWorkflow.getId());
        assertThat(found.getCurrentNodeId()).isEqualTo("node_1");
        assertThat(found.getCurrentType()).isEqualTo("message");
        assertThat(found.getCurrentNodeType()).isEqualTo("message");
        assertThat(found.getStatus()).isEqualTo("active");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();

        // Verify JSONB context data round-trips completely
        Map<String, Object> retrievedContext = found.getContext();
        assertThat(retrievedContext).isNotNull();
        assertThat(retrievedContext).containsEntry("userName", "John");
        assertThat(retrievedContext).containsEntry("orderId", 12345);
        assertThat(retrievedContext).containsKey("preferences");
        assertThat(retrievedContext).containsKey("tags");

        @SuppressWarnings("unchecked")
        Map<String, Object> preferences = (Map<String, Object>) retrievedContext.get("preferences");
        assertThat(preferences).containsEntry("language", "en");
        assertThat(preferences).containsEntry("theme", "dark");

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) retrievedContext.get("tags");
        assertThat(tags).containsExactly("vip", "returning");
    }

    @Test
    void shouldPersistAndRetrieveSessionStatus() {
        // Arrange: create session with "active" status
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setWorkflowId(savedWorkflow.getId());
        session.setContext(Map.of());
        session.setStatus("active");

        // Act: save, then update status
        ChatSession saved = chatSessionRepository.save(session);
        chatSessionRepository.flush();

        saved.setStatus("completed");
        chatSessionRepository.save(saved);
        chatSessionRepository.flush();

        Optional<ChatSession> retrieved = chatSessionRepository.findBySessionId(saved.getSessionId());

        // Assert: status update persisted correctly
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo("completed");
    }
}
