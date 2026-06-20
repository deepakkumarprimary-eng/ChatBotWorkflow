package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.ChatErrorResponse;
import com.xpressbees.chatbot.dto.ChatResponse;
import com.xpressbees.chatbot.dto.NodeProcessingResult;
import com.xpressbees.chatbot.dto.ValidationResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private static final int MAX_MESSAGE_NODE_CHAIN = 50;

    private final WorkflowRepository workflowRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final List<NodeProcessor> nodeProcessors;
    private final PlaceholderService placeholderService;
    private final SimpMessagingTemplate messagingTemplate;
    private final InputValidationService inputValidationService;

    public WorkflowExecutionServiceImpl(WorkflowRepository workflowRepository,
                                         ChatSessionRepository chatSessionRepository,
                                         List<NodeProcessor> nodeProcessors,
                                         PlaceholderService placeholderService,
                                         SimpMessagingTemplate messagingTemplate,
                                         InputValidationService inputValidationService) {
        this.workflowRepository = workflowRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.nodeProcessors = nodeProcessors;
        this.placeholderService = placeholderService;
        this.messagingTemplate = messagingTemplate;
        this.inputValidationService = inputValidationService;
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

        // Store root workflow ID in context for restart navigation
        session.getContext().put("_rootWorkflowId", workflowId);

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

    @SuppressWarnings("unchecked")
    private void handleInputNodeResume(ChatSession session, String sessionId, String message) {
        // Load workflow FIRST to access node config for validation
        Optional<Workflow> workflowOpt = workflowRepository.findById(session.getWorkflowId());
        if (workflowOpt.isEmpty()) {
            sendError(sessionId, "Workflow is no longer available");
            return;
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();

        // Find the current node config to check for validation rules
        Map<String, Object> currentNode = findNodeById(session.getCurrentNodeId(), workflowJson);
        if (currentNode != null) {
            Map<String, Object> config = (Map<String, Object>) currentNode.get("config");
            Map<String, Object> validationConfig = null;
            if (config != null) {
                validationConfig = (Map<String, Object>) config.get("validation");
            }

            if (validationConfig != null) {
                ValidationResult validationResult = inputValidationService.validate(message, validationConfig);
                if (!validationResult.isValid()) {
                    sendError(sessionId, validationResult.getErrorMessage());
                    return;
                }
            }
        }

        // Validation passed (or no validation configured) — store user input in context
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
        }

        // Read the target variable name from temporary context key
        String variableName = (String) context.get("_inputVariableName");
        if (variableName == null || variableName.trim().isEmpty()) {
            // Defensive fallback: use current node id
            variableName = session.getCurrentNodeId();
        }

        context.put(variableName, message);
        context.remove("_inputVariableName");
        session.setContext(context);

        // Resolve next node
        Map<String, Object> nextNode = resolveNextNode(session.getCurrentNodeId(), workflowJson);
        if (nextNode == null) {
            // Check if we're in a child workflow
            Map<String, Object> ctx = session.getContext();
            if (ctx != null) {
                List<Map<String, Object>> workflowStack = getWorkflowStack(ctx);
                if (!workflowStack.isEmpty()) {
                    // Child workflow ended after input - return to parent
                    try {
                        chatSessionRepository.save(session);
                    } catch (DataAccessException e) {
                        sendError(sessionId, "Failed to persist session state");
                        return;
                    }
                    handleChildWorkflowEnd(session);
                    return;
                }
            }
            // End of root workflow
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
                sendError(sessionId, "Workflow is no longer available");
                return;
            }

            Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();
            Map<String, Object> nextNode = resolveNextNode(session.getCurrentNodeId(), workflowJson);

            if (nextNode == null) {
                // Check if we're in a child workflow
                Map<String, Object> ctx = session.getContext();
                if (ctx != null) {
                    List<Map<String, Object>> workflowStack = getWorkflowStack(ctx);
                    if (!workflowStack.isEmpty()) {
                        // Child workflow ended after API node - return to parent
                        try {
                            chatSessionRepository.save(session);
                        } catch (DataAccessException e) {
                            sendError(sessionId, "Failed to persist session state");
                            return;
                        }
                        handleChildWorkflowEnd(session);
                        return;
                    }
                }
                // End of root workflow
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
                sendError(sessionId, "Workflow is no longer available");
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
            recordNavigationEntry(session, node);
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
                    // Check if we're in a child workflow
                    List<Map<String, Object>> workflowStack = getWorkflowStack(session.getContext());
                    if (!workflowStack.isEmpty()) {
                        // Child workflow ended - return to parent
                        handleChildWorkflowEnd(session);
                        return;
                    }
                    // End of root workflow
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
            } else if (result.getAction() == NodeProcessingResult.Action.ENTER_CHILD) {
                // Child workflow entry - read child workflow ID from context and transfer control
                Long childWorkflowId = (Long) session.getContext().remove("_childWorkflowId");
                enterChildWorkflow(session, childWorkflowId, node);
                return;
            } else if (result.getAction() == NodeProcessingResult.Action.PAUSE) {
                // Input node - mark last navigation entry as awaiting input, save state, and send response
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> history = (List<Map<String, Object>>) session.getContext().get("_navigationHistory");
                if (history != null && !history.isEmpty()) {
                    history.get(history.size() - 1).put("awaitsInput", true);
                }
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
    private Map<String, Object> findNodeById(String nodeId, Map<String, Object> workflowJson) {
        if (nodeId == null || workflowJson == null) {
            return null;
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");
        if (nodes == null) {
            return null;
        }

        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(node.get("id"))) {
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

    @SuppressWarnings("unchecked")
    private String getInputVariableName(String nodeId, Map<String, Object> workflowJson) {
        if (nodeId == null || workflowJson == null) {
            return nodeId != null ? nodeId : "userInput";
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");
        if (nodes == null) {
            return nodeId;
        }

        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(node.get("id"))) {
                Map<String, Object> config = (Map<String, Object>) node.get("config");
                if (config != null && config.get("variableName") != null) {
                    String varName = String.valueOf(config.get("variableName")).trim();
                    if (!varName.isEmpty()) {
                        return varName;
                    }
                }
                break;
            }
        }

        return nodeId;
    }

    @SuppressWarnings("unchecked")
    private void recordNavigationEntry(ChatSession session, Map<String, Object> node) {
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
            session.setContext(context);
        }

        List<Map<String, Object>> navigationHistory = (List<Map<String, Object>>) context.get("_navigationHistory");
        if (navigationHistory == null) {
            navigationHistory = new ArrayList<>();
            context.put("_navigationHistory", navigationHistory);
        }

        Map<String, Object> entry = new HashMap<>();
        entry.put("workflowId", session.getWorkflowId());
        entry.put("nodeId", node.get("id"));
        entry.put("nodeType", extractNodeType(node));
        entry.put("timestamp", Instant.now().toString());

        navigationHistory.add(entry);
    }

    @SuppressWarnings("unchecked")
    private String extractNodeType(Map<String, Object> node) {
        Map<String, Object> config = (Map<String, Object>) node.get("config");
        if (config != null && config.get("nodeType") != null) {
            return (String) config.get("nodeType");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getWorkflowStack(Map<String, Object> context) {
        List<Map<String, Object>> stack = (List<Map<String, Object>>) context.get("_workflowStack");
        if (stack == null) {
            stack = new ArrayList<>();
            context.put("_workflowStack", stack);
        }
        return stack;
    }

    private void clearTransientKeys(Map<String, Object> context) {
        context.remove("_targetNodeId");
        context.remove("_inputVariableName");
        context.remove("_displayVariable");
        context.remove("_buttonOptions");
    }

    private void enterChildWorkflow(ChatSession session, Long childWorkflowId, Map<String, Object> workflowNode) {
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
            session.setContext(context);
        }

        // Push stack entry with parent workflow info
        List<Map<String, Object>> stack = getWorkflowStack(context);
        Map<String, Object> stackEntry = new HashMap<>();
        stackEntry.put("parentWorkflowId", session.getWorkflowId());
        stackEntry.put("workflowNodeId", workflowNode.get("id"));
        stack.add(stackEntry);

        // Clear transient keys
        clearTransientKeys(context);

        // Switch to child workflow
        session.setWorkflowId(childWorkflowId);

        // Load child workflow
        Optional<Workflow> childWorkflowOpt = workflowRepository.findById(childWorkflowId);
        if (childWorkflowOpt.isEmpty()) {
            sendError(session.getSessionId(), "Child workflow not found: " + childWorkflowId);
            return;
        }

        Map<String, Object> childWorkflowJson = childWorkflowOpt.get().getWorkflowJson();
        Map<String, Object> firstNode = findFirstNode(childWorkflowJson);

        if (firstNode == null) {
            sendError(session.getSessionId(), "Child workflow has no starting node");
            return;
        }

        // Process child workflow from its first node
        processNodes(session, firstNode, childWorkflowJson);
    }

    @SuppressWarnings("unchecked")
    private void handleChildWorkflowEnd(ChatSession session) {
        Map<String, Object> context = session.getContext();
        if (context == null) {
            session.setStatus("completed");
            try { chatSessionRepository.save(session); } catch (DataAccessException e) {
                sendError(session.getSessionId(), "Failed to persist session state"); }
            return;
        }

        List<Map<String, Object>> stack = getWorkflowStack(context);
        if (stack.isEmpty()) {
            // No parent to return to - complete session
            session.setStatus("completed");
            try { chatSessionRepository.save(session); } catch (DataAccessException e) {
                sendError(session.getSessionId(), "Failed to persist session state"); }
            return;
        }

        // Pop the top entry
        Map<String, Object> entry = stack.remove(stack.size() - 1);
        Long parentWorkflowId = ((Number) entry.get("parentWorkflowId")).longValue();
        String workflowNodeId = (String) entry.get("workflowNodeId");

        // Restore parent workflow
        session.setWorkflowId(parentWorkflowId);

        // Load parent workflow
        Optional<Workflow> parentWorkflowOpt = workflowRepository.findById(parentWorkflowId);
        if (parentWorkflowOpt.isEmpty()) {
            sendError(session.getSessionId(), "Parent workflow not found: " + parentWorkflowId);
            return;
        }

        Map<String, Object> parentWorkflowJson = parentWorkflowOpt.get().getWorkflowJson();

        // Resolve next node after the workflow node in parent
        Map<String, Object> nextNode = resolveNextNode(workflowNodeId, parentWorkflowJson);

        if (nextNode != null) {
            processNodes(session, nextNode, parentWorkflowJson);
        } else if (!stack.isEmpty()) {
            // No next node and still in nested workflow - keep unwinding
            handleChildWorkflowEnd(session);
        } else {
            // No next node and stack is empty - workflow is done
            session.setStatus("completed");
            try {
                chatSessionRepository.save(session);
            } catch (DataAccessException e) {
                sendError(session.getSessionId(), "Failed to persist session state");
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleBack(String sessionId) {
        // 1. Validate session exists
        Optional<ChatSession> sessionOpt = chatSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            sendError(sessionId, "No active session found");
            return;
        }

        ChatSession session = sessionOpt.get();

        // 2. Check session not completed
        if ("completed".equals(session.getStatus())) {
            sendError(sessionId, "Session is already completed");
            return;
        }

        // 3. Retrieve _navigationHistory from context
        Map<String, Object> context = session.getContext();
        if (context == null) {
            sendError(sessionId, "No previous input to go back to");
            return;
        }

        List<Map<String, Object>> history = (List<Map<String, Object>>) context.get("_navigationHistory");
        if (history == null || history.isEmpty()) {
            sendError(sessionId, "No previous input to go back to");
            return;
        }

        // 4. Scan backwards for most recent entry where awaitsInput == true
        int targetIndex = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> entry = history.get(i);
            if (Boolean.TRUE.equals(entry.get("awaitsInput"))) {
                targetIndex = i;
                break;
            }
        }

        // 5. If no target found, send error
        if (targetIndex < 0) {
            sendError(sessionId, "No previous input to go back to");
            return;
        }

        // 6. Target found at targetIndex
        Map<String, Object> targetEntry = history.get(targetIndex);
        String targetNodeId = (String) targetEntry.get("nodeId");
        String targetNodeType = (String) targetEntry.get("nodeType");
        Long targetWorkflowId = ((Number) targetEntry.get("workflowId")).longValue();

        // 6a. Truncate history: remove target entry and everything after it
        history.subList(targetIndex, history.size()).clear();

        // 6c. Cross-workflow navigation: unwind _workflowStack if needed
        if (!targetWorkflowId.equals(session.getWorkflowId())) {
            List<Map<String, Object>> workflowStack = getWorkflowStack(context);
            // Remove entries from the end until session's workflowId matches target
            while (!workflowStack.isEmpty()) {
                Map<String, Object> stackEntry = workflowStack.get(workflowStack.size() - 1);
                Long parentWorkflowId = ((Number) stackEntry.get("parentWorkflowId")).longValue();
                workflowStack.remove(workflowStack.size() - 1);
                if (parentWorkflowId.equals(targetWorkflowId)) {
                    break;
                }
            }
            session.setWorkflowId(targetWorkflowId);
        }

        // 6d-e. Update session node position
        session.setCurrentNodeId(targetNodeId);
        session.setCurrentNodeType(targetNodeType);

        // 6f. Load target workflow and find target node
        Optional<Workflow> workflowOpt = workflowRepository.findById(targetWorkflowId);
        if (workflowOpt.isEmpty()) {
            sendError(sessionId, "Workflow not found");
            return;
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();
        Map<String, Object> targetNode = findNodeById(targetNodeId, workflowJson);
        if (targetNode == null) {
            sendError(sessionId, "Target node not found");
            return;
        }

        // 6g. Get the node's "name" field (prompt message) and resolve placeholders
        String prompt = (String) targetNode.get("name");
        if (prompt != null) {
            prompt = placeholderService.resolve(prompt, context);
        }

        // 6h. Send ChatResponse with the prompt text
        ChatResponse response = new ChatResponse(targetNode, prompt, sessionId);
        sendResponse(sessionId, response);

        // 6i. Persist session
        try {
            chatSessionRepository.save(session);
        } catch (DataAccessException e) {
            sendError(sessionId, "Failed to persist session state");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleRestart(String sessionId) {
        // 1. Validate session exists
        Optional<ChatSession> sessionOpt = chatSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            sendError(sessionId, "No active session found");
            return;
        }

        ChatSession session = sessionOpt.get();

        // 2. Get _rootWorkflowId from context (may be stored as Long or Integer)
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
            session.setContext(context);
        }

        Object rootWorkflowIdObj = context.get("_rootWorkflowId");
        if (rootWorkflowIdObj == null) {
            sendError(sessionId, "Workflow not found");
            return;
        }
        Long rootWorkflowId = ((Number) rootWorkflowIdObj).longValue();

        // 3. Clear all user context variables (keys not prefixed with '_')
        List<String> keysToRemove = new ArrayList<>();
        for (String key : context.keySet()) {
            if (!key.startsWith("_")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            context.remove(key);
        }

        // 4. Clear _navigationHistory
        context.put("_navigationHistory", new ArrayList<>());

        // 5. Clear _workflowStack
        context.put("_workflowStack", new ArrayList<>());

        // 6. Set session workflowId to root workflow ID
        session.setWorkflowId(rootWorkflowId);

        // 7. If session status is "completed", reset to "active"
        if ("completed".equals(session.getStatus())) {
            session.setStatus("active");
        }

        // 8. Load the root workflow
        Optional<Workflow> workflowOpt = workflowRepository.findById(rootWorkflowId);
        if (workflowOpt.isEmpty()) {
            sendError(sessionId, "Workflow not found");
            return;
        }

        Workflow workflow = workflowOpt.get();
        Map<String, Object> workflowJson = workflow.getWorkflowJson();

        // 9. Find first node
        Map<String, Object> firstNode = findFirstNode(workflowJson);
        if (firstNode == null) {
            sendError(sessionId, "Workflow has no starting node");
            return;
        }

        // 10. Process nodes from the beginning
        processNodes(session, firstNode, workflowJson);
    }

    private void sendResponse(String sessionId, ChatResponse response) {
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, response);
    }

    private void sendError(String sessionId, String errorMessage) {
        ChatErrorResponse error = new ChatErrorResponse(errorMessage, sessionId);
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, error);
    }
}
