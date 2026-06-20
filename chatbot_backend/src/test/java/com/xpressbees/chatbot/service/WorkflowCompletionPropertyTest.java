package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Property 6: Workflow Completion Detection
 *
 * For any node in a workflow graph that has no outgoing transition, when that node is the current
 * node being processed, the engine SHALL mark the session status as "completed" and the final
 * response SHALL include completed = true.
 *
 * Feature: websocket-workflow-execution, Property 6: Workflow Completion Detection
 */
class WorkflowCompletionPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 6: Workflow Completion Detection")
    void marksSessionCompletedWhenNoOutgoingTransition(@ForAll("terminalWorkflows") TerminalWorkflowData data) {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                null, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        ChatSession session = new ChatSession();
        session.setSessionId("test-session-" + UUID.randomUUID());
        session.setContext(new HashMap<>());
        session.setStatus("active");

        when(chatSessionRepo.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        try {
            java.lang.reflect.Method processNodes = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                    "processNodes", ChatSession.class, Map.class, Map.class);
            processNodes.setAccessible(true);
            processNodes.invoke(service, session, data.startNode, data.workflowJson);
        } catch (Exception e) {
            // Reflection exceptions acceptable
        }

        // Verify session was marked completed
        assert "completed".equals(session.getStatus()) :
                "Session should be marked as completed. Got: " + session.getStatus();

        // Verify a response with completed=true was sent
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), messageCaptor.capture());

        boolean completionSent = messageCaptor.getAllValues().stream()
                .filter(msg -> msg instanceof ChatResponse)
                .map(msg -> (ChatResponse) msg)
                .anyMatch(resp -> Boolean.TRUE.equals(resp.getCompleted()));
        assert completionSent : "Should send response with completed=true";
    }

    @Provide
    Arbitrary<TerminalWorkflowData> terminalWorkflows() {
        return Arbitraries.of(
                createTerminalWorkflow(1),
                createTerminalWorkflow(2),
                createTerminalWorkflow(3),
                createTerminalWorkflow(5)
        );
    }

    private TerminalWorkflowData createTerminalWorkflow(int nodeCount) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> transitions = new ArrayList<>();

        for (int i = 1; i <= nodeCount; i++) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", String.valueOf(i));
            node.put("name", "Node " + i);
            node.put("type", "state");
            node.put("config", null);
            nodes.add(node);

            if (i < nodeCount) {
                Map<String, Object> transition = new HashMap<>();
                transition.put("sourceNodeId", String.valueOf(i));
                transition.put("targetNodeId", String.valueOf(i + 1));
                transitions.add(transition);
            }
        }

        Map<String, Object> wf = new HashMap<>();
        wf.put("nodes", nodes);
        wf.put("transitions", transitions);

        return new TerminalWorkflowData(nodes.get(0), wf);
    }

    record TerminalWorkflowData(Map<String, Object> startNode, Map<String, Object> workflowJson) {}
}
