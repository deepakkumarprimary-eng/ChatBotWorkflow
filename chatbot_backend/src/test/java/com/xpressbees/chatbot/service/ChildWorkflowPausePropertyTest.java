package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.processor.WorkflowNodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Method;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Property 9: Child workflow pause preserves child workflowId
 *
 * For any input or API node within a child workflow that causes a PAUSE,
 * the session SHALL be saved with workflowId equal to the child workflow's ID
 * (not the parent's), and currentNodeId pointing to the pausing node within the child workflow.
 *
 * Feature: workflow-node, Property 9: Child workflow pause preserves child workflowId
 *
 * Validates: Requirements 7.1, 7.4
 */
class ChildWorkflowPausePropertyTest {

    @Property(tries = 100)
    void childWorkflowPausePreservesChildWorkflowId(
            @ForAll("pauseScenarios") PauseScenario scenario) throws Exception {

        // Set up mocks
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        PlaceholderService placeholderService = new PlaceholderService();

        List<NodeProcessor> processors = List.of(
                new InputNodeProcessor(),
                new MessageNodeProcessor(),
                new WorkflowNodeProcessor(workflowRepository)
        );

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, null);

        // Create session - start as parent workflow
        ChatSession session = new ChatSession();
        session.setSessionId("test-session-" + UUID.randomUUID());
        session.setWorkflowId(scenario.parentWorkflowId);
        session.setStatus("active");

        // Set up context with empty workflow stack (parent is the root)
        Map<String, Object> context = new HashMap<>();
        context.put("_workflowStack", new ArrayList<>());
        session.setContext(context);

        // Mock parent workflow - has a workflow node pointing to child
        Workflow parentWorkflow = new Workflow();
        parentWorkflow.setId(scenario.parentWorkflowId);
        parentWorkflow.setName("Parent Workflow");
        parentWorkflow.setWorkflowJson(scenario.parentWorkflowJson);
        when(workflowRepository.findById(scenario.parentWorkflowId)).thenReturn(Optional.of(parentWorkflow));

        // Mock child workflow - has an input node that will cause PAUSE
        Workflow childWorkflow = new Workflow();
        childWorkflow.setId(scenario.childWorkflowId);
        childWorkflow.setName("Child Workflow");
        childWorkflow.setWorkflowJson(scenario.childWorkflowJson);
        when(workflowRepository.findById(scenario.childWorkflowId)).thenReturn(Optional.of(childWorkflow));

        // Capture session saves to verify state at PAUSE time
        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        when(chatSessionRepository.save(sessionCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Start processing from the workflow node in the parent
        // This will: enter child workflow -> hit input node -> PAUSE
        Method processNodes = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                "processNodes", ChatSession.class, Map.class, Map.class);
        processNodes.setAccessible(true);
        processNodes.invoke(service, session, scenario.parentWorkflowNode, scenario.parentWorkflowJson);

        // After PAUSE in child workflow, verify the session state
        // The session should have been saved with child workflow's workflowId
        assert session.getWorkflowId().equals(scenario.childWorkflowId) :
                "Session workflowId should be child workflow ID. Expected " +
                scenario.childWorkflowId + " but got " + session.getWorkflowId();

        // The currentNodeId should point to the pausing node in the child workflow
        assert session.getCurrentNodeId().equals(scenario.inputNodeId) :
                "Session currentNodeId should point to child's input node. Expected " +
                scenario.inputNodeId + " but got " + session.getCurrentNodeId();

        // The currentNodeType should be "input"
        assert "input".equals(session.getCurrentNodeType()) :
                "Session currentNodeType should be 'input'. Got " + session.getCurrentNodeType();
    }

    @Provide
    Arbitrary<PauseScenario> pauseScenarios() {
        return Arbitraries.longs().between(1L, 999L).flatMap(parentWorkflowId ->
            Arbitraries.longs().between(1000L, 9999L).flatMap(childWorkflowId ->
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10).flatMap(inputNodeSuffix ->
                    Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10).map(varNameSuffix -> {
                        String workflowNodeId = "wf-node-" + inputNodeSuffix;
                        String inputNodeId = "input-node-" + inputNodeSuffix;
                        String afterNodeId = "after-node-" + inputNodeSuffix;
                        String variableName = "var_" + varNameSuffix;

                        // Parent workflow: workflowNode -> afterNode
                        Map<String, Object> parentWorkflowJson = createParentWorkflow(
                                workflowNodeId, afterNodeId, childWorkflowId);

                        // Child workflow: inputNode (causes PAUSE)
                        Map<String, Object> childWorkflowJson = createChildWorkflow(
                                inputNodeId, variableName);

                        // The workflow node in the parent
                        Map<String, Object> parentWorkflowNode = createWorkflowNode(
                                workflowNodeId, childWorkflowId);

                        return new PauseScenario(
                                parentWorkflowId,
                                childWorkflowId,
                                inputNodeId,
                                variableName,
                                parentWorkflowJson,
                                childWorkflowJson,
                                parentWorkflowNode
                        );
                    })
                )
            )
        );
    }

    private Map<String, Object> createParentWorkflow(String workflowNodeId, String afterNodeId, Long childWorkflowId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createWorkflowNode(workflowNodeId, childWorkflowId));
        nodes.add(createMessageNode(afterNodeId, "After child"));

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(createTransition(workflowNodeId, afterNodeId));

        Map<String, Object> wf = new HashMap<>();
        wf.put("nodes", nodes);
        wf.put("transitions", transitions);
        return wf;
    }

    private Map<String, Object> createChildWorkflow(String inputNodeId, String variableName) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(createInputNode(inputNodeId, "Enter value", variableName));

        // The input node is the first (and only) node - transition starts from it
        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(createTransition(inputNodeId, "dummy-target"));

        Map<String, Object> wf = new HashMap<>();
        wf.put("nodes", nodes);
        wf.put("transitions", transitions);
        return wf;
    }

    private Map<String, Object> createWorkflowNode(String id, Long childWorkflowId) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", "Call Child");
        node.put("type", "state");
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "workflow");
        config.put("workflowId", String.valueOf(childWorkflowId));
        node.put("config", config);
        return node;
    }

    private Map<String, Object> createInputNode(String id, String name, String variableName) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        Map<String, Object> config = new HashMap<>();
        config.put("nodeType", "input");
        config.put("variableName", variableName);
        node.put("config", config);
        return node;
    }

    private Map<String, Object> createMessageNode(String id, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("name", name);
        node.put("type", "state");
        node.put("config", null);
        return node;
    }

    private Map<String, Object> createTransition(String sourceId, String targetId) {
        Map<String, Object> t = new HashMap<>();
        t.put("sourceNodeId", sourceId);
        t.put("targetNodeId", targetId);
        return t;
    }

    record PauseScenario(
            Long parentWorkflowId,
            Long childWorkflowId,
            String inputNodeId,
            String variableName,
            Map<String, Object> parentWorkflowJson,
            Map<String, Object> childWorkflowJson,
            Map<String, Object> parentWorkflowNode
    ) {}
}
