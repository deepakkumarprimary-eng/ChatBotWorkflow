package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.InputNodeProcessor;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.processor.WorkflowNodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying that when a child workflow encounters an input node
 * and PAUSEs, the session is saved with the child workflow's workflowId and the
 * correct currentNodeId/currentNodeType pointing to the input node in the child.
 *
 * Validates: Requirements 7.1, 7.3, 7.4
 */
class WorkflowChildPauseTest {

    private static final String SESSION_ID = "child-pause-session-001";
    private static final Long PARENT_WORKFLOW_ID = 100L;
    private static final Long CHILD_WORKFLOW_ID = 200L;

    private WorkflowRepository workflowRepository;
    private ChatSessionRepository chatSessionRepository;
    private SimpMessagingTemplate messagingTemplate;
    private WorkflowExecutionServiceImpl service;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(WorkflowRepository.class);
        chatSessionRepository = mock(ChatSessionRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);

        PlaceholderService placeholderService = new PlaceholderService();

        // Use real processors: InputNodeProcessor handles input nodes,
        // WorkflowNodeProcessor handles workflow nodes, MessageNodeProcessor for messages
        WorkflowNodeProcessor workflowNodeProcessor = new WorkflowNodeProcessor(workflowRepository);
        List<NodeProcessor> processors = List.of(
                new InputNodeProcessor(),
                new MessageNodeProcessor(),
                workflowNodeProcessor
        );

        service = new WorkflowExecutionServiceImpl(
                workflowRepository, chatSessionRepository, processors,
                placeholderService, messagingTemplate, null);

        // Create session
        session = new ChatSession();
        session.setSessionId(SESSION_ID);
        session.setWorkflowId(PARENT_WORKFLOW_ID);
        session.setStatus("active");
        session.setContext(new HashMap<>());

        when(chatSessionRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Build parent and child workflows
        Workflow parentWorkflow = buildParentWorkflow();
        Workflow childWorkflow = buildChildWorkflow();

        when(workflowRepository.findById(PARENT_WORKFLOW_ID)).thenReturn(Optional.of(parentWorkflow));
        when(workflowRepository.findById(CHILD_WORKFLOW_ID)).thenReturn(Optional.of(childWorkflow));
    }

    @Test
    void pauseInChildWorkflow_savesSessionWithChildWorkflowId() {
        // Start the parent workflow — it has a workflow node that enters the child,
        // and the child has an input node that should cause PAUSE
        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // After PAUSE, the session's workflowId should be the CHILD workflow ID
        assertEquals(CHILD_WORKFLOW_ID, session.getWorkflowId(),
                "Session workflowId should be the child workflow ID after PAUSE in child");
    }

    @Test
    void pauseInChildWorkflow_setsCurrentNodeIdToChildInputNode() {
        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // currentNodeId should point to the input node inside the child workflow
        assertEquals("child_input_node", session.getCurrentNodeId(),
                "currentNodeId should point to the input node in the child workflow");
    }

    @Test
    void pauseInChildWorkflow_setsCurrentNodeTypeToInput() {
        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // currentNodeType should be "input"
        assertEquals("input", session.getCurrentNodeType(),
                "currentNodeType should be 'input' when paused at an input node in child workflow");
    }

    @Test
    void pauseInChildWorkflow_sessionIsSavedViaRepository() {
        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // Verify that chatSessionRepository.save was called (PAUSE branch saves session)
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository, atLeastOnce()).save(captor.capture());

        // The last save should have the child workflow ID
        ChatSession savedSession = captor.getValue();
        assertEquals(CHILD_WORKFLOW_ID, savedSession.getWorkflowId(),
                "Saved session should have child workflow ID");
        assertEquals("child_input_node", savedSession.getCurrentNodeId(),
                "Saved session should have child input node as currentNodeId");
    }

    @Test
    void pauseInChildWorkflow_workflowStackContainsParentEntry() {
        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // The workflow stack should have an entry for the parent
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stack = (List<Map<String, Object>>) session.getContext().get("_workflowStack");
        assertNotNull(stack, "Workflow stack should exist in context");
        assertEquals(1, stack.size(), "Workflow stack should have one entry (parent)");

        Map<String, Object> entry = stack.get(0);
        assertEquals(PARENT_WORKFLOW_ID, ((Number) entry.get("parentWorkflowId")).longValue(),
                "Stack entry should reference parent workflow ID");
        assertEquals("workflow_node_1", entry.get("workflowNodeId"),
                "Stack entry should reference the workflow node that triggered child entry");
    }

    @Test
    void resumeAfterPauseInChildWorkflow_usesChildWorkflowForResolution() {
        // Start workflow — pauses at child input node
        service.startWorkflow(SESSION_ID, PARENT_WORKFLOW_ID);

        // Simulate user reply — handleUserInput uses session.getWorkflowId() (child) to load workflow
        service.handleUserInput(SESSION_ID, "user response");

        // After resume, the input value should be stored under the child's variableName
        assertEquals("user response", session.getContext().get("child_user_input"),
                "User response should be stored under child workflow's variable name");
    }

    /**
     * Builds a parent workflow with a single workflow node that references the child workflow.
     *
     * [workflow_node_1] --> [parent_msg_end]
     *   config.nodeType="workflow"
     *   config.workflowId=200
     */
    private Workflow buildParentWorkflow() {
        // Workflow node that references child
        Map<String, Object> workflowNodeConfig = new HashMap<>();
        workflowNodeConfig.put("nodeType", "workflow");
        workflowNodeConfig.put("workflowId", CHILD_WORKFLOW_ID.toString());

        Map<String, Object> workflowNode = new HashMap<>();
        workflowNode.put("id", "workflow_node_1");
        workflowNode.put("name", "Call Child Workflow");
        workflowNode.put("type", "state");
        workflowNode.put("config", workflowNodeConfig);

        // End message node in parent (after child returns)
        Map<String, Object> parentMsgEnd = new HashMap<>();
        parentMsgEnd.put("id", "parent_msg_end");
        parentMsgEnd.put("name", "Back in parent workflow");
        parentMsgEnd.put("type", "state");

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(workflowNode);
        nodes.add(parentMsgEnd);

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(Map.of("sourceNodeId", "workflow_node_1", "targetNodeId", "parent_msg_end"));

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        Workflow workflow = new Workflow();
        workflow.setId(PARENT_WORKFLOW_ID);
        workflow.setName("Parent Workflow");
        workflow.setWorkflowJson(workflowJson);
        return workflow;
    }

    /**
     * Builds a child workflow with an input node that will cause PAUSE.
     *
     * [child_input_node] --> [child_msg_done]
     *   config.nodeType="input"
     *   config.variableName="child_user_input"
     */
    private Workflow buildChildWorkflow() {
        // Input node in child — will cause PAUSE
        Map<String, Object> inputConfig = new HashMap<>();
        inputConfig.put("nodeType", "input");
        inputConfig.put("variableName", "child_user_input");

        Map<String, Object> childInputNode = new HashMap<>();
        childInputNode.put("id", "child_input_node");
        childInputNode.put("name", "Enter something in child");
        childInputNode.put("type", "state");
        childInputNode.put("config", inputConfig);

        // Message node after input (child workflow continues after resume)
        Map<String, Object> childMsgDone = new HashMap<>();
        childMsgDone.put("id", "child_msg_done");
        childMsgDone.put("name", "Child workflow done");
        childMsgDone.put("type", "state");

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(childInputNode);
        nodes.add(childMsgDone);

        List<Map<String, Object>> transitions = new ArrayList<>();
        transitions.add(Map.of("sourceNodeId", "child_input_node", "targetNodeId", "child_msg_done"));

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", transitions);

        Workflow workflow = new Workflow();
        workflow.setId(CHILD_WORKFLOW_ID);
        workflow.setName("Child Workflow");
        workflow.setWorkflowJson(workflowJson);
        return workflow;
    }
}
