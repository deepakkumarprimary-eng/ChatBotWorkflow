package com.xpressbees.chatbot.integration;

import com.xpressbees.chatbot.dto.SaveResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.service.SessionStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the full workflow execution lifecycle:
 * start session → process nodes → complete.
 *
 * Tests session status transitions from "active" to "completed" in the database
 * by simulating what WorkflowExecutionServiceImpl does at the persistence layer.
 *
 * Validates: Requirements 1.4
 */
class WorkflowExecutionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private SessionStateManager sessionStateManager;

    private Workflow savedWorkflow;

    @BeforeEach
    void setUp() {
        chatSessionRepository.deleteAll();
        workflowRepository.deleteAll();

        // Create a simple workflow: message → input → message (auto-advance to completion)
        Map<String, Object> workflowJson = buildSimpleWorkflow();

        Workflow workflow = new Workflow();
        workflow.setName("Execution Test Workflow");
        workflow.setWorkflowJson(workflowJson);
        savedWorkflow = workflowRepository.save(workflow);
        workflowRepository.flush();
    }

    @Test
    void shouldTransitionSessionFromActiveToCompletedAfterFullExecution() {
        // Step 1: Create session via SessionStateManager (simulates WorkflowExecutionServiceImpl.startWorkflow)
        String sessionId = UUID.randomUUID().toString();
        SaveResult createResult = sessionStateManager.createSession(sessionId, savedWorkflow.getId());

        assertThat(createResult.isSuccess()).isTrue();
        assertThat(createResult.getSession()).isNotNull();
        assertThat(createResult.getSession().getStatus()).isEqualTo("active");

        // Verify the session is persisted as "active" in the database
        Optional<ChatSession> activeSession = chatSessionRepository.findBySessionId(sessionId);
        assertThat(activeSession).isPresent();
        assertThat(activeSession.get().getStatus()).isEqualTo("active");
        assertThat(activeSession.get().getWorkflowId()).isEqualTo(savedWorkflow.getId());

        // Step 2: Simulate node processing — update current node and context
        ChatSession session = activeSession.get();
        session.setCurrentNodeId("msg1");
        session.setCurrentNodeType("message");
        session.setCurrentType("state");
        session.getContext().put("_rootWorkflowId", savedWorkflow.getId());
        sessionStateManager.save(session);

        // Simulate advancing to input node (pauses for user input)
        session.setCurrentNodeId("inp1");
        session.setCurrentNodeType("input");
        session.getContext().put("_inputVariableName", "userName");
        sessionStateManager.save(session);

        // Verify session is still "active" while processing
        Optional<ChatSession> midSession = chatSessionRepository.findBySessionId(sessionId);
        assertThat(midSession).isPresent();
        assertThat(midSession.get().getStatus()).isEqualTo("active");

        // Simulate user input received and stored in context
        session.getContext().put("userName", "John");
        session.getContext().remove("_inputVariableName");
        sessionStateManager.save(session);

        // Simulate advancing to final message node
        session.setCurrentNodeId("msg2");
        session.setCurrentNodeType("message");
        sessionStateManager.save(session);

        // Step 3: Workflow complete — no more nodes to process, mark as "completed"
        session.setStatus("completed");
        SaveResult completionResult = sessionStateManager.save(session);
        assertThat(completionResult.isSuccess()).isTrue();

        // Step 4: Verify the final status in the database is "completed"
        Optional<ChatSession> completedSession = chatSessionRepository.findBySessionId(sessionId);
        assertThat(completedSession).isPresent();
        assertThat(completedSession.get().getStatus()).isEqualTo("completed");
        assertThat(completedSession.get().getUpdatedAt()).isAfter(completedSession.get().getCreatedAt());
    }

    @Test
    void shouldPersistContextDataThroughoutWorkflowExecution() {
        // Create session
        String sessionId = UUID.randomUUID().toString();
        sessionStateManager.createSession(sessionId, savedWorkflow.getId());

        ChatSession session = chatSessionRepository.findBySessionId(sessionId).orElseThrow();

        // Simulate workflow execution building up context through each node
        session.setCurrentNodeId("msg1");
        session.setCurrentNodeType("message");
        session.getContext().put("_rootWorkflowId", savedWorkflow.getId());
        sessionStateManager.save(session);

        // Input node stores user input in context
        session.setCurrentNodeId("inp1");
        session.setCurrentNodeType("input");
        session.getContext().put("userName", "Alice");
        sessionStateManager.save(session);

        // Final message node — workflow completes
        session.setCurrentNodeId("msg2");
        session.setCurrentNodeType("message");
        session.setStatus("completed");
        sessionStateManager.save(session);

        // Verify final state has accumulated context persisted in the database
        ChatSession finalSession = chatSessionRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(finalSession.getStatus()).isEqualTo("completed");
        assertThat(finalSession.getCurrentNodeId()).isEqualTo("msg2");
        assertThat(finalSession.getContext()).containsEntry("userName", "Alice");
        assertThat(finalSession.getContext()).containsKey("_rootWorkflowId");
    }

    @Test
    void shouldCreateSessionWithActiveStatusLinkedToWorkflow() {
        // Verify that SessionStateManager.createSession produces an "active" session
        // linked to the correct workflow — the entry point of any workflow execution
        String sessionId = UUID.randomUUID().toString();
        SaveResult result = sessionStateManager.createSession(sessionId, savedWorkflow.getId());

        assertThat(result.isSuccess()).isTrue();
        ChatSession session = result.getSession();

        assertThat(session.getId()).isNotNull();
        assertThat(session.getSessionId()).isEqualTo(sessionId);
        assertThat(session.getWorkflowId()).isEqualTo(savedWorkflow.getId());
        assertThat(session.getStatus()).isEqualTo("active");
        assertThat(session.getContext()).isNotNull().isEmpty();
        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getUpdatedAt()).isNotNull();

        // Verify it's persisted in the database
        Optional<ChatSession> persisted = chatSessionRepository.findBySessionId(sessionId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo("active");
    }

    @Test
    void shouldHandleMultipleSessionsExecutingIndependently() {
        // Create two sessions for the same workflow
        String sessionId1 = UUID.randomUUID().toString();
        String sessionId2 = UUID.randomUUID().toString();

        sessionStateManager.createSession(sessionId1, savedWorkflow.getId());
        sessionStateManager.createSession(sessionId2, savedWorkflow.getId());

        // Advance session 1 to completion
        ChatSession session1 = chatSessionRepository.findBySessionId(sessionId1).orElseThrow();
        session1.setCurrentNodeId("msg2");
        session1.setStatus("completed");
        session1.getContext().put("userName", "User1");
        sessionStateManager.save(session1);

        // Session 2 remains active mid-execution
        ChatSession session2 = chatSessionRepository.findBySessionId(sessionId2).orElseThrow();
        session2.setCurrentNodeId("inp1");
        session2.setCurrentNodeType("input");
        session2.getContext().put("_inputVariableName", "userName");
        sessionStateManager.save(session2);

        // Verify independent statuses
        ChatSession retrieved1 = chatSessionRepository.findBySessionId(sessionId1).orElseThrow();
        ChatSession retrieved2 = chatSessionRepository.findBySessionId(sessionId2).orElseThrow();

        assertThat(retrieved1.getStatus()).isEqualTo("completed");
        assertThat(retrieved1.getContext()).containsEntry("userName", "User1");

        assertThat(retrieved2.getStatus()).isEqualTo("active");
        assertThat(retrieved2.getContext()).containsKey("_inputVariableName");
    }

    /**
     * Builds a simple 3-node workflow: message → input → message.
     * This structure allows testing the full lifecycle:
     * - msg1: auto-advances (message node)
     * - inp1: pauses for user input (input node)
     * - msg2: auto-advances to completion (message node)
     */
    private Map<String, Object> buildSimpleWorkflow() {
        Map<String, Object> workflowJson = new HashMap<>();

        // Nodes
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("id", "msg1");
        msg1.put("name", "Hello! What is your name?");
        msg1.put("type", "state");
        msg1.put("config", null);

        Map<String, Object> inp1 = new HashMap<>();
        inp1.put("id", "inp1");
        inp1.put("name", "Please enter your name:");
        inp1.put("type", "state");
        Map<String, Object> inputConfig = new HashMap<>();
        inputConfig.put("nodeType", "input");
        inputConfig.put("variableName", "userName");
        inp1.put("config", inputConfig);

        Map<String, Object> msg2 = new HashMap<>();
        msg2.put("id", "msg2");
        msg2.put("name", "Thank you, {{userName}}! Goodbye.");
        msg2.put("type", "state");
        msg2.put("config", null);

        workflowJson.put("nodes", List.of(msg1, inp1, msg2));

        // Transitions
        Map<String, Object> t1 = new HashMap<>();
        t1.put("sourceNodeId", "msg1");
        t1.put("targetNodeId", "inp1");

        Map<String, Object> t2 = new HashMap<>();
        t2.put("sourceNodeId", "inp1");
        t2.put("targetNodeId", "msg2");

        workflowJson.put("transitions", List.of(t1, t2));

        return workflowJson;
    }
}
