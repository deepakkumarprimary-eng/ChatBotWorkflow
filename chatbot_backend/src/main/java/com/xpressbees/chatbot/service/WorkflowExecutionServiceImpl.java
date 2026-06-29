package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.controller.ChatWebSocketHandler;
import com.xpressbees.chatbot.dto.*;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.util.WorkflowJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionServiceImpl.class);
    private static final int MAX_MESSAGE_NODE_CHAIN = 50;

    private final WorkflowRepository workflowRepository;
    private final WorkflowCacheService workflowCacheService;
    private final ProcessorRegistry processorRegistry;
    private final PlaceholderService placeholderService;
    private final InputValidationService inputValidationService;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatMessageSender chatMessageSender;
    private final BufferedMessageSender bufferedMessageSender;
    private final SessionStateManager sessionStateManager;
    private final NavigationService navigationService;
    private final ChildWorkflowService childWorkflowService;
    private final CorrelationIdManager correlationIdManager;
    private final ExecutionTracker executionTracker;

    public WorkflowExecutionServiceImpl(WorkflowRepository workflowRepository,
                                         WorkflowCacheService workflowCacheService,
                                         ProcessorRegistry processorRegistry,
                                         PlaceholderService placeholderService,
                                         InputValidationService inputValidationService,
                                         ChatWebSocketHandler chatWebSocketHandler,
                                         ChatMessageSender chatMessageSender,
                                         BufferedMessageSender bufferedMessageSender,
                                         SessionStateManager sessionStateManager,
                                         NavigationService navigationService,
                                         ChildWorkflowService childWorkflowService,
                                         CorrelationIdManager correlationIdManager,
                                         ExecutionTracker executionTracker) {
        this.workflowRepository = workflowRepository;
        this.workflowCacheService = workflowCacheService;
        this.processorRegistry = processorRegistry;
        this.placeholderService = placeholderService;
        this.inputValidationService = inputValidationService;
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.chatMessageSender = chatMessageSender;
        this.bufferedMessageSender = bufferedMessageSender;
        this.sessionStateManager = sessionStateManager;
        this.navigationService = navigationService;
        this.childWorkflowService = childWorkflowService;
        this.correlationIdManager = correlationIdManager;
        this.executionTracker = executionTracker;
    }

    @Override
    public void startWorkflow(String sessionId, Long workflowId) {
        correlationIdManager.set(sessionId != null ? sessionId : "unknown");
        if (!executionTracker.tryStart()) {
            log.warn("Rejected startWorkflow request - application is shutting down: sessionId={}", sessionId);
            sendError(sessionId != null ? sessionId : "unknown", "Service is shutting down, please try again later");
            correlationIdManager.clear();
            return;
        }
        try {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                log.warn("startWorkflow called with empty session ID");
                sendError(sessionId != null ? sessionId : "unknown", "Session ID is required");
                return;
            }

            if (workflowId == null) {
                log.warn("startWorkflow called with null workflow ID for session {}", sessionId);
                sendError(sessionId, "Workflow ID is invalid");
                return;
            }

            log.info("Starting workflow execution: workflowId={}, sessionId={}", workflowId, sessionId);

            // Validate the session ID was generated during chat.init
            boolean isPendingSession = chatWebSocketHandler.consumePendingSession(sessionId);
            if (!isPendingSession) {
                log.warn("No pending session found for sessionId={}", sessionId);
                sendError(sessionId, "No active session found");
                return;
            }

            // Load workflow (via Redis cache)
            Optional<Workflow> workflowOpt = workflowCacheService.findById(workflowId);
            if (workflowOpt.isEmpty()) {
                log.warn("Workflow not found: workflowId={}", workflowId);
                sendError(sessionId, "Workflow not found: " + workflowId);
                return;
            }

            Workflow workflow = workflowOpt.get();
            Map<String, Object> workflowJson = workflow.getWorkflowJson();

            Map<String, Object> firstNode = WorkflowJsonUtils.findFirstNode(workflowJson);
            if (firstNode == null) {
                log.warn("Workflow has no starting node: workflowId={}", workflowId);
                sendError(sessionId, "Workflow has no starting node");
                return;
            }

            // Create and persist ChatSession now that we have a valid workflowId
            SaveResult createResult = sessionStateManager.createSession(sessionId, workflowId);
            if (!createResult.isSuccess()) {
                log.error("Failed to create session: sessionId={}, error={}", sessionId, createResult.getErrorMessage());
                sendError(sessionId, createResult.getErrorMessage());
                return;
            }
            ChatSession session = createResult.getSession();

            // Store root workflow ID in context for restart navigation
            session.getContext().put("_rootWorkflowId", workflowId);

            log.debug("Processing first node for workflowId={}, nodeId={}", workflowId, firstNode.get("id"));

            // Process nodes starting from first node
            processNodes(session, firstNode, workflowJson);

            log.info("Workflow execution completed: workflowId={}, sessionId={}", workflowId, sessionId);
        } catch (Exception e) {
            log.error("Unexpected error during workflow start: sessionId={}, workflowId={}", sessionId, workflowId, e);
            sendError(sessionId != null ? sessionId : "unknown", "An unexpected error occurred");
        } finally {
            executionTracker.complete();
            correlationIdManager.clear();
        }
    }

    @Override
    public void handleUserInput(String sessionId, String message) {
        correlationIdManager.set(sessionId != null ? sessionId : "unknown");
        if (!executionTracker.tryStart()) {
            log.warn("Rejected handleUserInput request - application is shutting down: sessionId={}", sessionId);
            sendError(sessionId != null ? sessionId : "unknown", "Service is shutting down, please try again later");
            correlationIdManager.clear();
            return;
        }
        try {
            if (message == null || message.trim().isEmpty()) {
                log.warn("handleUserInput called with empty message for sessionId={}", sessionId);
                sendError(sessionId, "Non-empty message is required");
                return;
            }

            log.info("Handling user input: sessionId={}", sessionId);

            Optional<ChatSession> sessionOpt = sessionStateManager.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("No active session found for sessionId={}", sessionId);
                sendError(sessionId, "No active session found");
                return;
            }

            ChatSession session = sessionOpt.get();

            if ("completed".equals(session.getStatus())) {
                log.warn("User input received for already completed session: sessionId={}", sessionId);
                sendError(sessionId, "Session is already completed");
                return;
            }

            String nodeType = session.getCurrentNodeType();
            log.debug("Processing user input for nodeType={}, sessionId={}", nodeType, sessionId);

            if ("input".equals(nodeType)) {
                handleInputNodeResume(session, sessionId, message);
            } else if ("api".equals(nodeType)) {
                handleApiNodeResume(session, sessionId, message);
            } else {
                log.warn("User input received but session is not awaiting input: sessionId={}, nodeType={}", sessionId, nodeType);
                sendError(sessionId, "Session is not awaiting input");
            }

            log.info("User input handling completed: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Unexpected error handling user input: sessionId={}", sessionId, e);
            sendError(sessionId != null ? sessionId : "unknown", "An unexpected error occurred");
        } finally {
            executionTracker.complete();
            correlationIdManager.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleInputNodeResume(ChatSession session, String sessionId, String message) {
        // Load workflow FIRST to access node config for validation (via Redis cache)
        Optional<Workflow> workflowOpt = workflowCacheService.findById(session.getWorkflowId());
        if (workflowOpt.isEmpty()) {
            sendError(sessionId, "Workflow is no longer available");
            return;
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();

        // Find the current node config to check for validation rules
        Map<String, Object> currentNode = WorkflowJsonUtils.findNodeById(session.getCurrentNodeId(), workflowJson);
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
        Map<String, Object> nextNode = WorkflowJsonUtils.resolveNextNode(session.getCurrentNodeId(), workflowJson);
        if (nextNode == null) {
            // Check if we're in a child workflow
            Map<String, Object> ctx = session.getContext();
            if (ctx != null) {
                List<Map<String, Object>> workflowStack = (List<Map<String, Object>>) ctx.get("_workflowStack");
                if (workflowStack != null && !workflowStack.isEmpty()) {
                    // Child workflow ended after input - return to parent
                    SaveResult saveResult = sessionStateManager.save(session);
                    if (!saveResult.isSuccess()) {
                        sendError(sessionId, saveResult.getErrorMessage());
                        return;
                    }
                    ChildWorkflowResult childResult = childWorkflowService.handleChildEnd(session);
                    switch (childResult.getOutcome()) {
                        case NEXT_NODE:
                            processNodes(session, childResult.getNextNode(), childResult.getWorkflowJson());
                            break;
                        case ERROR:
                            sendError(session.getSessionId(), childResult.getErrorMessage());
                            break;
                        case COMPLETE:
                            session.setStatus("completed");
                            sessionStateManager.save(session);
                            break;
                    }
                    return;
                }
            }
            // End of root workflow
            session.setStatus("completed");
            SaveResult saveResult = sessionStateManager.save(session);
            if (!saveResult.isSuccess()) {
                sendError(sessionId, saveResult.getErrorMessage());
                return;
            }
            sendError(sessionId, "Session is already completed");
            return;
        }

        SaveResult saveResult = sessionStateManager.save(session);
        if (!saveResult.isSuccess()) {
            sendError(sessionId, saveResult.getErrorMessage());
            return;
        }

        // Continue processing from next node
        processNodes(session, nextNode, workflowJson);
    }

    @SuppressWarnings("unchecked")
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
            Optional<Workflow> workflowOpt = workflowCacheService.findById(session.getWorkflowId());
            if (workflowOpt.isEmpty()) {
                sendError(sessionId, "Workflow is no longer available");
                return;
            }

            Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();
            Map<String, Object> nextNode = WorkflowJsonUtils.resolveNextNode(session.getCurrentNodeId(), workflowJson);

            if (nextNode == null) {
                // Check if we're in a child workflow
                Map<String, Object> ctx = session.getContext();
                if (ctx != null) {
                    List<Map<String, Object>> workflowStack = (List<Map<String, Object>>) ctx.get("_workflowStack");
                    if (workflowStack != null && !workflowStack.isEmpty()) {
                        // Child workflow ended after API node - return to parent
                        SaveResult saveResult = sessionStateManager.save(session);
                        if (!saveResult.isSuccess()) {
                            sendError(sessionId, saveResult.getErrorMessage());
                            return;
                        }
                        ChildWorkflowResult childResult = childWorkflowService.handleChildEnd(session);
                        switch (childResult.getOutcome()) {
                            case NEXT_NODE:
                                processNodes(session, childResult.getNextNode(), childResult.getWorkflowJson());
                                break;
                            case ERROR:
                                sendError(session.getSessionId(), childResult.getErrorMessage());
                                break;
                            case COMPLETE:
                                session.setStatus("completed");
                                sessionStateManager.save(session);
                                break;
                        }
                        return;
                    }
                }
                // End of root workflow
                session.setStatus("completed");
                SaveResult saveResult = sessionStateManager.save(session);
                if (!saveResult.isSuccess()) {
                    sendError(sessionId, saveResult.getErrorMessage());
                    return;
                }
                sendResponse(sessionId, new ChatResponse(null, "Session completed", sessionId, true));
                return;
            }

            SaveResult saveResult = sessionStateManager.save(session);
            if (!saveResult.isSuccess()) {
                sendError(sessionId, saveResult.getErrorMessage());
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
            Optional<Workflow> workflowOpt = workflowCacheService.findById(session.getWorkflowId());
            if (workflowOpt.isEmpty()) {
                sendError(sessionId, "Workflow is no longer available");
                return;
            }

            Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();

            // Find the target node whose name matches the user's selection
            Map<String, Object> targetNode = WorkflowJsonUtils.findTargetNodeByName(session.getCurrentNodeId(), message, workflowJson);

            if (targetNode == null) {
                sendError(sessionId, "Target node not found for selection: " + message);
                return;
            }

            SaveResult saveResult = sessionStateManager.save(session);
            if (!saveResult.isSuccess()) {
                sendError(sessionId, saveResult.getErrorMessage());
                return;
            }

            // Continue processing from the matched target node
            processNodes(session, targetNode, workflowJson);

        } else {
            sendError(sessionId, "Session is not awaiting input");
        }
    }

    @SuppressWarnings("unchecked")
    private void processNodes(ChatSession session, Map<String, Object> currentNode,
                              Map<String, Object> workflowJson) {
        int messageNodeCount = 0;
        Map<String, Object> node = currentNode;

        while (node != null) {
            log.debug("Processing node: nodeId={}, type={}, sessionId={}", node.get("id"), node.get("type"), session.getSessionId());
            navigationService.recordNavigationEntry(session, node);
            NodeProcessor processor = findProcessor(node);
            NodeProcessingResult result = processor.process(node, session, placeholderService, workflowJson);

            if (result.getAction() == NodeProcessingResult.Action.CONTINUE) {
                messageNodeCount++;
                if (messageNodeCount > MAX_MESSAGE_NODE_CHAIN) {
                    sendError(session.getSessionId(), "Potential infinite loop detected");
                    return;
                }

                if (result.getResponse() != null) {
                    if (!sendResponse(session.getSessionId(), result.getResponse())) {
                        return; // Connection closed due to drain timeout — stop processing
                    }
                }

                // Resolve next node
                String nodeId = (String) node.get("id");

                // Check for targeted routing (from conditional branching)
                String targetNodeId = (String) session.getContext().get("_targetNodeId");
                if (targetNodeId != null) {
                    session.getContext().remove("_targetNodeId");
                    node = WorkflowJsonUtils.resolveNextNode(nodeId, targetNodeId, workflowJson);
                } else {
                    node = WorkflowJsonUtils.resolveNextNode(nodeId, workflowJson);
                }

                if (node == null) {
                    // Check if we're in a child workflow
                    List<Map<String, Object>> workflowStack = (List<Map<String, Object>>) session.getContext().get("_workflowStack");
                    if (workflowStack != null && !workflowStack.isEmpty()) {
                        // Child workflow ended - return to parent
                        ChildWorkflowResult childResult = childWorkflowService.handleChildEnd(session);
                        switch (childResult.getOutcome()) {
                            case NEXT_NODE:
                                processNodes(session, childResult.getNextNode(), childResult.getWorkflowJson());
                                break;
                            case ERROR:
                                sendError(session.getSessionId(), childResult.getErrorMessage());
                                break;
                            case COMPLETE:
                                session.setStatus("completed");
                                sessionStateManager.save(session);
                                break;
                        }
                        return;
                    }
                    // End of root workflow
                    session.setStatus("completed");
                    SaveResult saveResult = sessionStateManager.save(session);
                    if (!saveResult.isSuccess()) {
                        sendError(session.getSessionId(), saveResult.getErrorMessage());
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
                ChildWorkflowResult childResult = childWorkflowService.enterChild(session, childWorkflowId, node);
                switch (childResult.getOutcome()) {
                    case NEXT_NODE:
                        processNodes(session, childResult.getNextNode(), childResult.getWorkflowJson());
                        break;
                    case ERROR:
                        sendError(session.getSessionId(), childResult.getErrorMessage());
                        break;
                    case COMPLETE:
                        session.setStatus("completed");
                        sessionStateManager.save(session);
                        break;
                }
                return;
            } else if (result.getAction() == NodeProcessingResult.Action.ERROR) {
                // Processor reported an error — send it to the client and stop
                bufferedMessageSender.sendError(session.getSessionId(), result.getErrorMessage());
                return;
            } else if (result.getAction() == NodeProcessingResult.Action.PAUSE) {
                // Input node - mark last navigation entry as awaiting input, save state, and send response
                navigationService.markLastEntryAwaitsInput(session);

                // Store last prompt payload for reconnection support (Requirement 6.3)
                ChatResponse promptResponse = result.getResponse();
                if (promptResponse != null) {
                    Map<String, Object> promptPayload = new HashMap<>();
                    promptPayload.put("node", promptResponse.getNode());
                    promptPayload.put("response", promptResponse.getResponse());
                    promptPayload.put("sessionId", promptResponse.getSessionId());
                    promptPayload.put("completed", promptResponse.getCompleted());
                    session.setLastPromptPayload(promptPayload);
                }

                SaveResult saveResult = sessionStateManager.save(session);
                if (!saveResult.isSuccess()) {
                    sendError(session.getSessionId(), saveResult.getErrorMessage());
                    return;
                }
                sendResponse(session.getSessionId(), result.getResponse());
                return;
            }
        }
    }

    @Override
    public void handleBack(String sessionId) {
        Optional<ChatSession> sessionOpt = sessionStateManager.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            sendError(sessionId, "No active session found");
            return;
        }
        ChatSession session = sessionOpt.get();
        if ("completed".equals(session.getStatus())) {
            sendError(sessionId, "Session is already completed");
            return;
        }

        NavigationResult result = navigationService.handleBack(session);
        switch (result.getOutcome()) {
            case RESUME_NODE:
                ChatResponse response = new ChatResponse(result.getTargetNode(), result.getPrompt(), sessionId);
                sendResponse(sessionId, response);
                sessionStateManager.save(session);
                break;
            case UNAVAILABLE:
                sendError(sessionId, "No previous input to go back to");
                break;
            case ERROR:
                sendError(sessionId, result.getErrorMessage());
                break;
        }
    }

    @Override
    public void handleRestart(String sessionId) {
        Optional<ChatSession> sessionOpt = sessionStateManager.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            sendError(sessionId, "No active session found");
            return;
        }
        ChatSession session = sessionOpt.get();

        NavigationResult result = navigationService.handleRestart(session);
        switch (result.getOutcome()) {
            case RESUME_NODE:
                processNodes(session, result.getTargetNode(), result.getWorkflowJson());
                break;
            case ERROR:
                sendError(sessionId, result.getErrorMessage());
                break;
            default:
                sendError(sessionId, "Unable to restart workflow");
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private NodeProcessor findProcessor(Map<String, Object> node) {
        String type = (String) node.get("type");

        // For "state" nodes, the logical node type is inside config.nodeType
        // (e.g. "input", "api", "workflow"). If absent, it's a plain message node.
        if ("state".equals(type)) {
            Map<String, Object> config = (Map<String, Object>) node.get("config");
            if (config != null && config.containsKey("nodeType")) {
                String logicalType = (String) config.get("nodeType");
                return processorRegistry.getProcessor(logicalType);
            }
            // No config or no nodeType → message node (fallback)
            return processorRegistry.getProcessor("message");
        }

        // For non-state nodes (e.g. "decision"), the type field IS the logical type
        return processorRegistry.getProcessor(type);
    }

    private boolean sendResponse(String sessionId, ChatResponse response) {
        boolean sent = bufferedMessageSender.send(sessionId, response);
        if (!sent) {
            log.warn("Send buffer full or connection closed for session {}, stopping workflow processing", sessionId);
            return false;
        }
        return true;
    }

    private void sendError(String sessionId, String errorMessage) {
        bufferedMessageSender.sendError(sessionId, errorMessage);
    }
}
