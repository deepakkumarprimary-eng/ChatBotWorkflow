package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import com.xpressbees.chatbot.processor.MessageNodeProcessor;
import com.xpressbees.chatbot.processor.NodeProcessor;
import com.xpressbees.chatbot.repository.ChatSessionRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Property 10: Navigation history is append-only with correct format
 *
 * For any session with an existing navigation history of size N, after the engine processes
 * one additional node, the history SHALL have size N+1, the first N entries SHALL be identical
 * to the original entries, and the new entry SHALL contain exactly three fields: workflowId
 * (Long matching session's current workflowId), nodeId (String matching the processed node's id),
 * and timestamp (valid ISO-8601 string).
 *
 * Validates: Requirements 8.2, 8.3, 8.4, 8.6
 *
 * Feature: workflow-node, Property 10: Navigation history is append-only with correct format
 */
class NavigationHistoryPropertyTest {

    @Property(tries = 100)
    @Tag("Feature: workflow-node, Property 10: Navigation history is append-only with correct format")
    void navigationHistoryIsAppendOnlyWithCorrectFormat(
            @ForAll("existingHistorySizes") int existingHistorySize,
            @ForAll("workflowIds") long workflowId,
            @ForAll("nodeIds") String nodeId) {

        // Arrange: set up mocks
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatSessionRepository chatSessionRepo = mock(ChatSessionRepository.class);
        PlaceholderService placeholderService = new PlaceholderService();
        List<NodeProcessor> processors = List.of(new MessageNodeProcessor());

        WorkflowExecutionServiceImpl service = new WorkflowExecutionServiceImpl(
                null, chatSessionRepo, processors, placeholderService, messagingTemplate, null);

        // Create a session with existing navigation history of size N
        ChatSession session = new ChatSession();
        session.setSessionId("test-session-" + UUID.randomUUID());
        session.setWorkflowId(workflowId);
        session.setStatus("active");

        Map<String, Object> context = new HashMap<>();
        List<Map<String, Object>> existingHistory = new ArrayList<>();
        for (int i = 0; i < existingHistorySize; i++) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("workflowId", (long) (i + 1));
            entry.put("nodeId", "existing-node-" + i);
            entry.put("timestamp", Instant.now().minusSeconds(existingHistorySize - i).toString());
            existingHistory.add(entry);
        }
        context.put("_navigationHistory", existingHistory);
        session.setContext(context);

        // Snapshot original history for later comparison
        List<Map<String, Object>> originalHistorySnapshot = new ArrayList<>();
        for (Map<String, Object> entry : existingHistory) {
            originalHistorySnapshot.add(new HashMap<>(entry));
        }

        when(chatSessionRepo.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        // Create a single-node workflow (message node, type "state", no config.nodeType)
        // with no outgoing transitions → ends immediately after processing
        Map<String, Object> messageNode = new HashMap<>();
        messageNode.put("id", nodeId);
        messageNode.put("name", "Test Message Node");
        messageNode.put("type", "state");
        messageNode.put("config", null);

        List<Map<String, Object>> nodes = List.of(messageNode);
        // First transition has sourceNodeId pointing to our node, but no target → findFirstNode returns our node
        Map<String, Object> transition = new HashMap<>();
        transition.put("sourceNodeId", nodeId);
        transition.put("targetNodeId", "non-existent"); // no target node exists

        Map<String, Object> workflowJson = new HashMap<>();
        workflowJson.put("nodes", nodes);
        workflowJson.put("transitions", List.of(transition));

        // Act: invoke processNodes via reflection (private method)
        try {
            java.lang.reflect.Method processNodes = WorkflowExecutionServiceImpl.class.getDeclaredMethod(
                    "processNodes", ChatSession.class, Map.class, Map.class);
            processNodes.setAccessible(true);
            processNodes.invoke(service, session, messageNode, workflowJson);
        } catch (Exception e) {
            // If reflection fails, test cannot proceed — fail explicitly
            throw new AssertionError("Failed to invoke processNodes via reflection", e);
        }

        // Assert: navigation history has been updated correctly
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> finalHistory =
                (List<Map<String, Object>>) session.getContext().get("_navigationHistory");

        // 1. History size is N+1
        assert finalHistory != null : "Navigation history should not be null after processing";
        assert finalHistory.size() == existingHistorySize + 1 :
                "Navigation history size should be N+1. Expected " + (existingHistorySize + 1)
                        + " but got " + finalHistory.size();

        // 2. First N entries are identical to the original entries (append-only)
        for (int i = 0; i < existingHistorySize; i++) {
            Map<String, Object> originalEntry = originalHistorySnapshot.get(i);
            Map<String, Object> currentEntry = finalHistory.get(i);
            assert originalEntry.equals(currentEntry) :
                    "Entry at index " + i + " was modified. Original: " + originalEntry
                            + ", Current: " + currentEntry;
        }

        // 3. New entry (at index N) has correct format
        Map<String, Object> newEntry = finalHistory.get(existingHistorySize);

        // 3a. workflowId is present and matches session's workflowId (Long)
        assert newEntry.containsKey("workflowId") : "New entry must contain 'workflowId'";
        Object entryWorkflowId = newEntry.get("workflowId");
        assert entryWorkflowId instanceof Long :
                "workflowId should be a Long, got: " + (entryWorkflowId != null ? entryWorkflowId.getClass() : "null");
        assert entryWorkflowId.equals(workflowId) :
                "workflowId should match session's workflowId. Expected " + workflowId + " got " + entryWorkflowId;

        // 3b. nodeId is present and matches the processed node's id (String)
        assert newEntry.containsKey("nodeId") : "New entry must contain 'nodeId'";
        Object entryNodeId = newEntry.get("nodeId");
        assert entryNodeId instanceof String :
                "nodeId should be a String, got: " + (entryNodeId != null ? entryNodeId.getClass() : "null");
        assert entryNodeId.equals(nodeId) :
                "nodeId should match the processed node's id. Expected '" + nodeId + "' got '" + entryNodeId + "'";

        // 3c. timestamp is present and is a valid ISO-8601 parseable string
        assert newEntry.containsKey("timestamp") : "New entry must contain 'timestamp'";
        Object entryTimestamp = newEntry.get("timestamp");
        assert entryTimestamp instanceof String :
                "timestamp should be a String, got: " + (entryTimestamp != null ? entryTimestamp.getClass() : "null");
        try {
            Instant.parse((String) entryTimestamp);
        } catch (DateTimeParseException e) {
            throw new AssertionError("timestamp should be a valid ISO-8601 string, got: '" + entryTimestamp + "'", e);
        }

        // 3d. New entry has exactly four keys (workflowId, nodeId, nodeType, timestamp)
        assert newEntry.size() == 4 :
                "New entry should have exactly 4 keys (workflowId, nodeId, nodeType, timestamp), got " + newEntry.size()
                        + " keys: " + newEntry.keySet();

        // 3e. nodeType is present (may be null for message nodes without config.nodeType)
        assert newEntry.containsKey("nodeType") : "New entry must contain 'nodeType'";
    }

    @Provide
    Arbitrary<Integer> existingHistorySizes() {
        return Arbitraries.integers().between(0, 20);
    }

    @Provide
    Arbitrary<Long> workflowIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<String> nodeIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(20)
                .map(s -> "node-" + s);
    }
}
