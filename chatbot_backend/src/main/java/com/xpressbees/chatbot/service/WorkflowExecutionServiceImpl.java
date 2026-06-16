package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private static final int MAX_MESSAGE_NODE_CHAIN = 50;

    private final WorkflowRepository workflowRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final List<NodeProcessor> nodeProcessors;
    private final PlaceholderService placeholderService;
    private final SimpMessagingTemplate messagingTemplate;

    public WorkflowExecutionServiceImpl(WorkflowRepository workflowRepository,
                                         ChatSessionRepository chatSessionRepository,
                                         List<NodeProcessor> nodeProcessors,
                                         PlaceholderService placeholderService,
                                         SimpMessagingTemplate messagingTemplate) {
        this.workflowRepository = workflowRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.nodeProcessors = nodeProcessors;
        this.placeholderService = placeholderService;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void startWorkflow(String sessionId, Long workflowId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sendError(sessionId != null ? sessionId : "unknown", "Session ID is required");
            return;
        }

        if (workflowId == null) {
            sendError(sessionId, "Workflow ID is invalid");
            return;
        }

        // Load existing session (created during chat.init)
        Optional<ChatSession> sessionOpt = chatSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            sendError(sessionId, "No active session found");
            return;
        }

        ChatSession session = sessionOpt.get();

        // Load workflow
        Optional<Workflow> workflowOpt = workflowRepository.findById(workflowId);
        if (workflowOpt.isEmpty()) {
            sendError(sessionId, "Workflow not found: " + workflowId);
            return;
        }

        Workflow workflow = workflowOpt.get();
        Map<String, Object> workflowJson = workflow.getWorkflowJson();

        Map<String, Object> firstNode = findFirstNode(workflowJson);
        if (firstNode == null) {
            sendError(sessionId, "Workflow has no starting node");
            return;
        }

        // Update session with the chosen workflow
        session.setWorkflowId(workflowId);
        try {
            session = chatSessionRepository.save(session);
        } catch (DataAccessException e) {
            sendError(sessionId, "Failed to persist session state");
            return;
        }

        // Process nodes starting from first node
        processNodes(session, firstNode, workflowJson);
    }

    @Override
    public void handleUserInput(String sessionId, String message) {
        if (message == null || message.trim().isEmpty()) {
            sendError(sessionId, "Non-empty message is required");
            return;
        }

        Optional<ChatSession> sessionOpt = chatSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            sendError(sessionId, "No active session found");
            return;
        }

        ChatSession session = sessionOpt.get();

        if ("completed".equals(session.getStatus())) {
            sendError(sessionId, "Session is already completed");
            return;
        }

        if (!"input".equals(session.getCurrentNodeType())) {
            sendError(sessionId, "Session is not awaiting input");
            return;
        }

        // Store user input in context
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
        }
        context.put("mobile_no", message);
        session.setContext(context);

        // Load workflow to get the JSON
        Optional<Workflow> workflowOpt = workflowRepository.findById(session.getWorkflowId());
        if (workflowOpt.isEmpty()) {
            sendError(sessionId, "Workflow not found");
            return;
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();

        // Resolve next node
        Map<String, Object> nextNode = resolveNextNode(session.getCurrentNodeId(), workflowJson);
        if (nextNode == null) {
            // End of workflow
            session.setStatus("completed");
            try {
                chatSessionRepository.save(session);
            } catch (DataAccessException e) {
                sendError(sessionId, "Failed to persist session state");
                return;
            }
            sendError(sessionId, "Session is already completed");
            return;
        }

        try {
            chatSessionRepository.save(session);
        } catch (DataAccessException e) {
            sendError(sessionId, "Failed to persist session state");
            return;
        }

        // Continue processing from next node
        processNodes(session, nextNode, workflowJson);
    }

    private void processNodes(ChatSession session, Map<String, Object> currentNode,
                              Map<String, Object> workflowJson) {
        int messageNodeCount = 0;
        Map<String, Object> node = currentNode;

        while (node != null) {
            NodeProcessor processor = findProcessor(node);
            NodeProcessingResult result = processor.process(node, session, placeholderService);

            if (result.getAction() == NodeProcessingResult.Action.CONTINUE) {
                messageNodeCount++;
                if (messageNodeCount > MAX_MESSAGE_NODE_CHAIN) {
                    sendError(session.getSessionId(), "Potential infinite loop detected");
                    return;
                }

                sendResponse(session.getSessionId(), result.getResponse());

                // Resolve next node
                String nodeId = (String) node.get("id");
                node = resolveNextNode(nodeId, workflowJson);

                if (node == null) {
                    // End of workflow
                    session.setStatus("completed");
                    try {
                        chatSessionRepository.save(session);
                    } catch (DataAccessException e) {
                        sendError(session.getSessionId(), "Failed to persist session state");
                        return;
                    }
                    // Send completion response for the last node
                    ChatResponse completionResponse = new ChatResponse(
                            currentNode, result.getResponse().getResponse(),
                            session.getSessionId(), true);
                    sendResponse(session.getSessionId(), completionResponse);
                    return;
                }
            } else if (result.getAction() == NodeProcessingResult.Action.PAUSE) {
                // Input node - save state and send response
                try {
                    chatSessionRepository.save(session);
                } catch (DataAccessException e) {
                    sendError(session.getSessionId(), "Failed to persist session state");
                    return;
                }
                sendResponse(session.getSessionId(), result.getResponse());
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveNextNode(String currentNodeId, Map<String, Object> workflowJson) {
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");

        if (transitions == null || nodes == null) {
            return null;
        }

        for (Map<String, Object> transition : transitions) {
            if (currentNodeId.equals(transition.get("sourceNodeId"))) {
                String targetNodeId = (String) transition.get("targetNodeId");
                for (Map<String, Object> n : nodes) {
                    if (targetNodeId.equals(n.get("id"))) {
                        return n;
                    }
                }
                break;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findFirstNode(Map<String, Object> workflowJson) {
        if (workflowJson == null) {
            return null;
        }

        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");

        if (transitions == null || transitions.isEmpty() || nodes == null) {
            return null;
        }

        String firstNodeId = (String) transitions.get(0).get("sourceNodeId");
        for (Map<String, Object> node : nodes) {
            if (firstNodeId.equals(node.get("id"))) {
                return node;
            }
        }
        return null;
    }

    private NodeProcessor findProcessor(Map<String, Object> node) {
        for (NodeProcessor processor : nodeProcessors) {
            if (processor.canHandle(node)) {
                return processor;
            }
        }
        // Fallback: treat as message node (find the MessageNodeProcessor)
        return nodeProcessors.stream()
                .filter(p -> p instanceof com.xpressbees.chatbot.processor.MessageNodeProcessor)
                .findFirst()
                .orElse(nodeProcessors.get(0));
    }

    private void sendResponse(String sessionId, ChatResponse response) {
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, response);
    }

    private void sendError(String sessionId, String errorMessage) {
        ChatErrorResponse error = new ChatErrorResponse(errorMessage, sessionId);
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, error);
    }
}
