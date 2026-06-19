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

        String nodeType = session.getCurrentNodeType();

        if ("input".equals(nodeType)) {
            handleInputNodeResume(session, sessionId, message);
        } else if ("api".equals(nodeType)) {
            handleApiNodeResume(session, sessionId, message);
        } else {
            sendError(sessionId, "Session is not awaiting input");
        }
    }

    private void handleInputNodeResume(ChatSession session, String sessionId, String message) {
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

    private void handleApiNodeResume(ChatSession session, String sessionId, String message) {
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
        }

        String displayVariable = (String) context.get("_displayVariable");
        String buttonOptions = (String) context.get("_buttonOptions");

        if (displayVariable != null) {
            // Type 3: Interactive selection - validate reply against stored options
            String arrayValues = (String) context.get(displayVariable);
            if (arrayValues != null) {
                String[] options = arrayValues.split("\n");
                boolean validSelection = Arrays.stream(options).anyMatch(opt -> opt.equals(message));

                if (!validSelection) {
                    sendError(sessionId, "'" + message + "' is not in the available options");
                    return;
                }

                // Store selected value, replacing the newline-separated string
                context.put(displayVariable, message);
                context.remove("_displayVariable");
                session.setContext(context);
            } else {
                sendError(sessionId, "Session is not awaiting input");
                return;
            }

            // Load workflow and resolve next node
            Optional<Workflow> workflowOpt = workflowRepository.findById(session.getWorkflowId());
            if (workflowOpt.isEmpty()) {
                sendError(sessionId, "Workflow not found");
                return;
            }

            Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();
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
                sendResponse(sessionId, new ChatResponse(null, "Session completed", sessionId, true));
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

        } else if (buttonOptions != null) {
            // Button node: validate reply matches a target node name
            String[] options = buttonOptions.split("\n");
            boolean validSelection = Arrays.stream(options).anyMatch(opt -> opt.equals(message));

            if (!validSelection) {
                sendError(sessionId, "'" + message + "' is not a valid selection");
                return;
            }

            context.remove("_buttonOptions");
            session.setContext(context);

            // Load workflow to find the target node by name
            Optional<Workflow> workflowOpt = workflowRepository.findById(session.getWorkflowId());
            if (workflowOpt.isEmpty()) {
                sendError(sessionId, "Workflow not found");
                return;
            }

            Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();

            // Find the target node whose name matches the user's selection
            Map<String, Object> targetNode = findTargetNodeByName(session.getCurrentNodeId(), message, workflowJson);

            if (targetNode == null) {
                sendError(sessionId, "Target node not found for selection: " + message);
                return;
            }

            try {
                chatSessionRepository.save(session);
            } catch (DataAccessException e) {
                sendError(sessionId, "Failed to persist session state");
                return;
            }

            // Continue processing from the matched target node
            processNodes(session, targetNode, workflowJson);

        } else {
            sendError(sessionId, "Session is not awaiting input");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findTargetNodeByName(String currentNodeId, String targetName,
                                                      Map<String, Object> workflowJson) {
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");

        if (transitions == null || nodes == null) {
            return null;
        }

        // Find all transitions from current node
        for (Map<String, Object> transition : transitions) {
            if (currentNodeId.equals(transition.get("sourceNodeId"))) {
                String targetNodeId = (String) transition.get("targetNodeId");
                // Find the target node and check its name
                for (Map<String, Object> node : nodes) {
                    if (targetNodeId.equals(node.get("id"))) {
                        Object name = node.get("name");
                        if (name != null && targetName.equals(String.valueOf(name))) {
                            return node;
                        }
                        break;
                    }
                }
            }
        }
        return null;
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

                // Check for targeted routing (from conditional branching)
                String targetNodeId = (String) session.getContext().get("_targetNodeId");
                if (targetNodeId != null) {
                    session.getContext().remove("_targetNodeId");
                    node = resolveNextNode(nodeId, targetNodeId, workflowJson);
                } else {
                    node = resolveNextNode(nodeId, workflowJson);
                }

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
    private Map<String, Object> resolveNextNode(String currentNodeId, String targetNodeId, Map<String, Object> workflowJson) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");
        if (nodes == null || targetNodeId == null) {
            return resolveNextNode(currentNodeId, workflowJson);
        }

        for (Map<String, Object> node : nodes) {
            if (targetNodeId.equals(node.get("id"))) {
                return node;
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
