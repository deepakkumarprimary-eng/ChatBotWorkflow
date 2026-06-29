package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.dto.NavigationResult;
import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.repository.WorkflowRepository;
import com.xpressbees.chatbot.util.WorkflowJsonUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Handles back navigation, restart, and navigation history recording.
 * Returns NavigationResult — never throws for expected outcomes.
 */
@Service
public class NavigationService {

    private final WorkflowRepository workflowRepository;
    private final PlaceholderService placeholderService;

    public NavigationService(WorkflowRepository workflowRepository,
                             PlaceholderService placeholderService) {
        this.workflowRepository = workflowRepository;
        this.placeholderService = placeholderService;
    }

    /**
     * Scans navigation history for most recent awaitsInput entry.
     * Restores session state (node position, workflow ID, stack).
     * Resolves the prompt text for the target node.
     *
     * @return NavigationResult with target node info, or unavailable signal
     */
    @SuppressWarnings("unchecked")
    public NavigationResult handleBack(ChatSession session) {
        Map<String, Object> context = session.getContext();
        if (context == null) {
            return NavigationResult.unavailable();
        }

        List<Map<String, Object>> history = (List<Map<String, Object>>) context.get("_navigationHistory");
        if (history == null || history.isEmpty()) {
            return NavigationResult.unavailable();
        }

        // Scan backwards for most recent entry where awaitsInput == true
        int targetIndex = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> entry = history.get(i);
            if (Boolean.TRUE.equals(entry.get("awaitsInput"))) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex < 0) {
            return NavigationResult.unavailable();
        }

        // Target found at targetIndex
        Map<String, Object> targetEntry = history.get(targetIndex);
        String targetNodeId = (String) targetEntry.get("nodeId");
        String targetNodeType = (String) targetEntry.get("nodeType");
        Long targetWorkflowId = ((Number) targetEntry.get("workflowId")).longValue();

        // Truncate history: remove target entry and everything after it
        history.subList(targetIndex, history.size()).clear();

        // Cross-workflow navigation: unwind _workflowStack if needed
        if (!targetWorkflowId.equals(session.getWorkflowId())) {
            List<Map<String, Object>> workflowStack = getWorkflowStack(context);
            // Remove entries from the end until we find the target workflow
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

        // Update session node position
        session.setCurrentNodeId(targetNodeId);
        session.setCurrentNodeType(targetNodeType);

        // Load target workflow and find target node
        Optional<Workflow> workflowOpt = workflowRepository.findById(targetWorkflowId);
        if (workflowOpt.isEmpty()) {
            return NavigationResult.error("Workflow not found");
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();
        Map<String, Object> targetNode = WorkflowJsonUtils.findNodeById(targetNodeId, workflowJson);
        if (targetNode == null) {
            return NavigationResult.error("Target node not found");
        }

        // Get the node's "name" field (prompt message) and resolve placeholders
        String prompt = (String) targetNode.get("name");
        if (prompt != null) {
            prompt = placeholderService.resolve(prompt, context);
        }

        return NavigationResult.resumeNode(targetNode, workflowJson, prompt);
    }

    /**
     * Clears user context variables (non-underscore keys), resets navigation
     * history and workflow stack, restores root workflow ID.
     *
     * @return NavigationResult with first node and workflowJson to resume
     */
    @SuppressWarnings("unchecked")
    public NavigationResult handleRestart(ChatSession session) {
        Map<String, Object> context = session.getContext();
        if (context == null) {
            context = new HashMap<>();
            session.setContext(context);
        }

        // Get _rootWorkflowId from context (may be stored as Long or Integer)
        Object rootWorkflowIdObj = context.get("_rootWorkflowId");
        if (rootWorkflowIdObj == null) {
            return NavigationResult.error("Workflow not found");
        }
        Long rootWorkflowId = ((Number) rootWorkflowIdObj).longValue();

        // Clear all user context variables (keys not prefixed with '_')
        List<String> keysToRemove = new ArrayList<>();
        for (String key : context.keySet()) {
            if (!key.startsWith("_")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            context.remove(key);
        }

        // Clear _navigationHistory
        context.put("_navigationHistory", new ArrayList<>());

        // Clear _workflowStack
        context.put("_workflowStack", new ArrayList<>());

        // Set session workflowId to root workflow ID
        session.setWorkflowId(rootWorkflowId);

        // If session status is "completed", reset to "active"
        if ("completed".equals(session.getStatus())) {
            session.setStatus("active");
        }

        // Load the root workflow
        Optional<Workflow> workflowOpt = workflowRepository.findById(rootWorkflowId);
        if (workflowOpt.isEmpty()) {
            return NavigationResult.error("Workflow not found");
        }

        Map<String, Object> workflowJson = workflowOpt.get().getWorkflowJson();

        // Find first node
        Map<String, Object> firstNode = WorkflowJsonUtils.findFirstNode(workflowJson);
        if (firstNode == null) {
            return NavigationResult.error("Workflow has no starting node");
        }

        return NavigationResult.resumeNode(firstNode, workflowJson, null);
    }

    /**
     * Appends a navigation entry to the session's _navigationHistory.
     * Entry contains: workflowId, nodeId, nodeType, timestamp.
     */
    @SuppressWarnings("unchecked")
    public void recordNavigationEntry(ChatSession session, Map<String, Object> node) {
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
        entry.put("nodeType", WorkflowJsonUtils.extractNodeType(node));
        entry.put("timestamp", Instant.now().toString());

        navigationHistory.add(entry);
    }

    /**
     * Marks the last navigation history entry as awaitsInput=true.
     * Called when a processor returns PAUSE.
     */
    @SuppressWarnings("unchecked")
    public void markLastEntryAwaitsInput(ChatSession session) {
        Map<String, Object> context = session.getContext();
        if (context == null) {
            return;
        }

        List<Map<String, Object>> history = (List<Map<String, Object>>) context.get("_navigationHistory");
        if (history != null && !history.isEmpty()) {
            history.get(history.size() - 1).put("awaitsInput", true);
        }
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
}
