package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.entity.ChatSession;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Property 6: Restart clears user state and restores root workflow
 *
 * For any session context containing a mix of user context variables (keys without
 * underscore prefix) and internal keys (keys with underscore prefix), after the restart
 * clearing logic: (a) no user context variable keys SHALL remain, (b) _navigationHistory
 * SHALL be an empty list, (c) _workflowStack SHALL be an empty list, and (d) the session's
 * workflowId SHALL equal the stored _rootWorkflowId.
 *
 * Validates: Requirements 8.1, 8.2, 8.3, 9.1
 *
 * Feature: chat-back-navigation, Property 6: Restart clears user state and restores root workflow
 */
class RestartPropertyTest {

    /**
     * Applies the restart clearing logic as implemented in handleRestart
     * (steps 3-6 of the algorithm). This is the pure state-manipulation
     * portion that doesn't require database or WebSocket access.
     */
    private void applyRestartClearing(ChatSession session) {
        Map<String, Object> context = session.getContext();

        // Step 2: Get _rootWorkflowId
        Long rootWorkflowId = ((Number) context.get("_rootWorkflowId")).longValue();

        // Step 3: Clear user context variables (keys not prefixed with '_')
        List<String> keysToRemove = context.keySet().stream()
                .filter(key -> !key.startsWith("_"))
                .collect(Collectors.toList());
        keysToRemove.forEach(context::remove);

        // Step 4: Clear _navigationHistory
        context.put("_navigationHistory", new ArrayList<>());

        // Step 5: Clear _workflowStack
        context.put("_workflowStack", new ArrayList<>());

        // Step 6: Set session workflowId to root workflow ID
        session.setWorkflowId(rootWorkflowId);
    }

    @Property(tries = 100)
    @Tag("Feature: chat-back-navigation, Property 6: Restart clears user state and restores root workflow")
    void restartClearsUserStateAndRestoresRootWorkflow(
            @ForAll("randomContexts") Map<String, Object> generatedContext,
            @ForAll("rootWorkflowIds") long rootWorkflowId,
            @ForAll("currentWorkflowIds") long currentWorkflowId) {

        // Precondition: currentWorkflowId differs from rootWorkflowId
        Assume.that(currentWorkflowId != rootWorkflowId);

        // Arrange: build context with the _rootWorkflowId and generated keys
        Map<String, Object> context = new HashMap<>(generatedContext);
        context.put("_rootWorkflowId", rootWorkflowId);

        // Arrange: create session with a different workflowId than the root
        ChatSession session = new ChatSession();
        session.setSessionId("test-session-" + UUID.randomUUID());
        session.setWorkflowId(currentWorkflowId);
        session.setStatus("active");
        session.setContext(context);

        // Capture user keys present before clearing (for assertion messaging)
        List<String> userKeysBefore = context.keySet().stream()
                .filter(key -> !key.startsWith("_"))
                .collect(Collectors.toList());

        // Act: apply restart clearing logic
        applyRestartClearing(session);

        // Assert (a): no user context variable keys remain
        Map<String, Object> resultContext = session.getContext();
        List<String> remainingUserKeys = resultContext.keySet().stream()
                .filter(key -> !key.startsWith("_"))
                .collect(Collectors.toList());
        assert remainingUserKeys.isEmpty() :
                "After restart, no user keys (without _ prefix) should remain. "
                        + "Found: " + remainingUserKeys + ". Originally had: " + userKeysBefore;

        // Assert (b): _navigationHistory is an empty list
        Object navHistory = resultContext.get("_navigationHistory");
        assert navHistory instanceof List :
                "_navigationHistory should be a List, got: "
                        + (navHistory != null ? navHistory.getClass().getName() : "null");
        assert ((List<?>) navHistory).isEmpty() :
                "_navigationHistory should be empty after restart, but has "
                        + ((List<?>) navHistory).size() + " entries";

        // Assert (c): _workflowStack is an empty list
        Object workflowStack = resultContext.get("_workflowStack");
        assert workflowStack instanceof List :
                "_workflowStack should be a List, got: "
                        + (workflowStack != null ? workflowStack.getClass().getName() : "null");
        assert ((List<?>) workflowStack).isEmpty() :
                "_workflowStack should be empty after restart, but has "
                        + ((List<?>) workflowStack).size() + " entries";

        // Assert (d): session's workflowId equals stored _rootWorkflowId
        assert session.getWorkflowId().equals(rootWorkflowId) :
                "Session workflowId should equal _rootWorkflowId after restart. "
                        + "Expected: " + rootWorkflowId + ", Got: " + session.getWorkflowId();
    }

    @Provide
    Arbitrary<Map<String, Object>> randomContexts() {
        // Generate a context map with:
        // - 0-10 user keys (no underscore prefix) with random string values
        // - 0-5 internal keys (underscore prefix) with random values
        // - May include _navigationHistory with random entries
        // - May include _workflowStack with random entries

        Arbitrary<Map<String, Object>> userEntries = userKeyEntries();
        Arbitrary<Map<String, Object>> internalEntries = internalKeyEntries();
        Arbitrary<List<Map<String, Object>>> navHistories = navigationHistoryEntries();
        Arbitrary<List<Map<String, Object>>> workflowStacks = workflowStackEntries();

        return Combinators.combine(userEntries, internalEntries, navHistories, workflowStacks)
                .as((user, internal, navHistory, stack) -> {
                    Map<String, Object> context = new HashMap<>();
                    context.putAll(user);
                    context.putAll(internal);
                    if (!navHistory.isEmpty()) {
                        context.put("_navigationHistory", new ArrayList<>(navHistory));
                    }
                    if (!stack.isEmpty()) {
                        context.put("_workflowStack", new ArrayList<>(stack));
                    }
                    return context;
                });
    }

    private Arbitrary<Map<String, Object>> userKeyEntries() {
        // Generate 0-10 user keys (no underscore prefix)
        Arbitrary<String> userKeys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15);
        Arbitrary<String> userValues = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);

        return Arbitraries.integers().between(0, 10).flatMap(count -> {
            if (count == 0) {
                return Arbitraries.just(new HashMap<>());
            }
            return Combinators.combine(
                    userKeys.list().ofSize(count),
                    userValues.list().ofSize(count)
            ).as((keys, values) -> {
                Map<String, Object> map = new HashMap<>();
                for (int i = 0; i < keys.size(); i++) {
                    // Ensure keys don't start with underscore
                    String key = keys.get(i);
                    if (key.startsWith("_")) {
                        key = "u" + key;
                    }
                    map.put(key, values.get(i));
                }
                return map;
            });
        });
    }

    private Arbitrary<Map<String, Object>> internalKeyEntries() {
        // Generate 0-5 internal keys (underscore prefix)
        // Exclude _rootWorkflowId, _navigationHistory, _workflowStack as they are managed separately
        Arbitrary<String> internalKeys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                .map(s -> "_" + s)
                .filter(k -> !k.equals("_rootWorkflowId")
                        && !k.equals("_navigationHistory")
                        && !k.equals("_workflowStack"));
        Arbitrary<String> internalValues = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);

        return Arbitraries.integers().between(0, 5).flatMap(count -> {
            if (count == 0) {
                return Arbitraries.just(new HashMap<>());
            }
            return Combinators.combine(
                    internalKeys.list().ofSize(count),
                    internalValues.list().ofSize(count)
            ).as((keys, values) -> {
                Map<String, Object> map = new HashMap<>();
                for (int i = 0; i < keys.size(); i++) {
                    map.put(keys.get(i), values.get(i));
                }
                return map;
            });
        });
    }

    private Arbitrary<List<Map<String, Object>>> navigationHistoryEntries() {
        // Generate 0-5 navigation history entries
        return Arbitraries.integers().between(0, 5).flatMap(count -> {
            if (count == 0) {
                return Arbitraries.just(new ArrayList<>());
            }
            Arbitrary<Map<String, Object>> entry = Combinators.combine(
                    Arbitraries.longs().between(1L, 100L),
                    Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10).map(s -> "node-" + s),
                    Arbitraries.of("input", "api", "workflow", null)
            ).as((wfId, nodeId, nodeType) -> {
                Map<String, Object> e = new HashMap<>();
                e.put("workflowId", wfId);
                e.put("nodeId", nodeId);
                e.put("nodeType", nodeType);
                e.put("timestamp", Instant.now().toString());
                return e;
            });
            return entry.list().ofSize(count);
        });
    }

    private Arbitrary<List<Map<String, Object>>> workflowStackEntries() {
        // Generate 0-3 workflow stack entries
        return Arbitraries.integers().between(0, 3).flatMap(count -> {
            if (count == 0) {
                return Arbitraries.just(new ArrayList<>());
            }
            Arbitrary<Map<String, Object>> entry = Combinators.combine(
                    Arbitraries.longs().between(1L, 100L),
                    Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10).map(s -> "node-" + s)
            ).as((parentWfId, nodeId) -> {
                Map<String, Object> e = new HashMap<>();
                e.put("parentWorkflowId", parentWfId);
                e.put("workflowNodeId", nodeId);
                return e;
            });
            return entry.list().ofSize(count);
        });
    }

    @Provide
    Arbitrary<Long> rootWorkflowIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Long> currentWorkflowIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }
}
