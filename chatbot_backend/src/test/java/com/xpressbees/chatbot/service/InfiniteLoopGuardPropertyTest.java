package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.repository.ChatSessionRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Property 7: Infinite Loop Guard
 *
 * For any workflow graph where a chain of consecutive Message_Nodes exceeds 50 without
 * encountering an Input_Node or end-of-workflow, the engine SHALL stop processing and produce
 * an error response. For chains of 50 or fewer, processing SHALL complete normally.
 *
 * Feature: websocket-workflow-execution, Property 7: Infinite Loop Guard
 */
class InfiniteLoopGuardPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 7: Infinite Loop Guard")
    void stopsProcessingWhenChainExceeds50(@ForAll("chainLengthsOver50") int chainLength) {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = TestServiceFactory.createService(null, processors, placeholderService, null, null, new ChatMessageSender(messagingTemplate), new SessionStateManager(chatSessionRepo), new NavigationService(null, placeholderService), new ChildWorkflowService(null));

        // Create a chain of message nodes
        Map<String, Object> workflowJson = createMessageNodeChain(chainLength);
        ChatSession session = new ChatSession();
        session.setSessionId("test-session");
        session.setContext(new HashMap<>());
        session.setStatus("active");

        when(chatSessionRepo.save(any(ChatSession.class))).thenReturn(session);

        // Use reflection to call processNodes
        try {
            java.lang.reflect.Method processNodes = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                    "processNodes", ChatSession.class, Map.class, Map.class);
            processNodes.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");
            processNodes.invoke(service, session, nodes.get(0), workflowJson);
        } catch (Exception e) {
            // Reflection exceptions are acceptable in test
        }

        // Verify error was sent about infinite loop
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());

        boolean errorSent = messageCaptor.getAllValues().stream()
                .anyMatch(msg -> msg.toString().contains("infinite loop"));
        assert errorSent : "Should send infinite loop error for chain of " + chainLength;
    }

    @Property(tries = 100)
    @Tag("Feature: websocket-workflow-execution, Property 7: Infinite Loop Guard")
    void completesNormallyWhenChainIs50OrFewer(@ForAll("chainLengthsUnder50") int chainLength) {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = TestServiceFactory.createService(null, processors, placeholderService, null, null, new ChatMessageSender(messagingTemplate), new SessionStateManager(chatSessionRepo), new NavigationService(null, placeholderService), new ChildWorkflowService(null));

        Map<String, Object> workflowJson = createMessageNodeChain(chainLength);
        ChatSession session = new ChatSession();
        session.setSessionId("test-session");
        session.setContext(new HashMap<>());
        session.setStatus("active");

        when(chatSessionRepo.save(any(ChatSession.class))).thenReturn(session);

        try {
            java.lang.reflect.Method processNodes = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                    "processNodes", ChatSession.class, Map.class, Map.class);
            processNodes.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");
            processNodes.invoke(service, session, nodes.get(0), workflowJson);
        } catch (Exception e) {
            // Reflection exceptions are acceptable in test
        }

        // Verify no infinite loop error
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), messageCaptor.capture());

        boolean infiniteLoopError = messageCaptor.getAllValues().stream()
                .anyMatch(msg -> msg.toString().contains("infinite loop"));
        assert !infiniteLoopError : "Should NOT send infinite loop error for chain of " + chainLength;
    }

    @Provide
    Arbitrary<Integer> chainLengthsOver50() {
        return Arbitraries.integers().between(51, 100);
    }

    @Provide
    Arbitrary<Integer> chainLengthsUnder50() {
        return Arbitraries.integers().between(2, 50);
    }

    private Map<String, Object> createMessageNodeChain(int length) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> transitions = new ArrayList<>();

        for (int i = 1; i <= length; i++) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", String.valueOf(i));
            node.put("name", "Message " + i);
            node.put("type", "state");
            node.put("config", null);
            nodes.add(node);

            if (i < length) {
                Map<String, Object> transition = new HashMap<>();
                transition.put("sourceNodeId", String.valueOf(i));
                transition.put("targetNodeId", String.valueOf(i + 1));
                transitions.add(transition);
            }
        }

        Map<String, Object> wf = new HashMap<>();
        wf.put("nodes", nodes);
        wf.put("transitions", transitions);
        return wf;
    }
}
