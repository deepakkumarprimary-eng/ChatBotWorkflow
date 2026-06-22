package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Property 9: Session State Persistence After Input Node
 *
 * For any input node encountered during processing, after the engine pauses, the persisted
 * ChatSession record SHALL have current_node_id equal to the input node's id, current_type
 * equal to "state", current_node_type equal to "input", and status equal to "active".
 *
 * Feature: websocket-workflow-execution, Property 9: Session State Persistence After Input Node
 */
class SessionPersistencePropertyTest {

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 9: Session State Persistence After Input Node")
    void sessionHasCorrectStateAfterInputNodePause(@ForAll("inputNodeData") InputNodeData data) {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new InputNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                null, chatSessionRepo, processors, placeholderService, messagingTemplate, null, null);

        ChatSession session = new ChatSession();
        session.setSessionId("test-session-" + UUID.randomUUID());
        session.setContext(new HashMap<>());
        session.setStatus("active");

        when(chatSessionRepo.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Process a workflow that starts with the input node
        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", List.of(data.inputNode));
        workflowJson.put("transitions", List.of());

        try {
            java.lang.reflect.Method processNodes = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                    "processNodes", ChatSession.class, Map.class, Map.class);
            processNodes.setAccessible(true);
            processNodes.invoke(service, session, data.inputNode, workflowJson);
        } catch (Exception e) {
            // Reflection exceptions acceptable
        }

        // Verify session state after pause
        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepo).save(sessionCaptor.capture());

        ChatSession savedSession = sessionCaptor.getValue();
        assert data.nodeId.equals(savedSession.getCurrentNodeId()) :
                "current_node_id should be " + data.nodeId + " but got " + savedSession.getCurrentNodeId();
        assert "state".equals(savedSession.getCurrentType()) :
                "current_type should be 'state' but got " + savedSession.getCurrentType();
        assert "input".equals(savedSession.getCurrentNodeType()) :
                "current_node_type should be 'input' but got " + savedSession.getCurrentNodeType();
        assert "active".equals(savedSession.getStatus()) :
                "status should be 'active' but got " + savedSession.getStatus();
    }

    @Provide
    Arbitrary<InputNodeData> inputNodeData() {
        return Arbitraries.of("1", "2", "node-A", "input-42", "abc").map(nodeId -> {
            Map<String, Object> inputNode = new HashMap<>();
            inputNode.put("id", nodeId);
            inputNode.put("name", "Enter your mobile number");
            inputNode.put("type", "state");
            inputNode.put("config", Map.of("nodeType", "input"));
            return new InputNodeData(nodeId, inputNode);
        });
    }

    record InputNodeData(String nodeId, Map<String, Object> inputNode) {}
}
