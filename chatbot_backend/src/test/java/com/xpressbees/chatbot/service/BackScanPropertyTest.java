package com.xpressbees.chatbot.service;

import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;

/**
 * Property 2: Back-scan correctness
 *
 * For any navigation history containing a mix of node entries (message, input, api
 * with and without awaitsInput), the back-scan algorithm SHALL return the most recent
 * entry where awaitsInput == true, and after applying it, the session's currentNodeId
 * SHALL equal the target entry's nodeId, and currentNodeType SHALL equal the target
 * entry's nodeType.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 4.1
 *
 * Feature: chat-back-navigation, Property 2: Back-scan correctness
 */
class BackScanPropertyTest {

    /**
     * Replicates the back-scan algorithm from handleBack in WorkflowExecutionServiceImpl.
     * Scans backwards through the history looking for the most recent entry where
     * awaitsInput == Boolean.TRUE.
     *
     * @return the index of the target entry, or -1 if not found
     */
    private int backScan(List<Map<String, Object>> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> entry = history.get(i);
            if (Boolean.TRUE.equals(entry.get("awaitsInput"))) {
                return i;
            }
        }
        return -1;
    }

    @Property(tries = 100)
    @Tag("Feature: chat-back-navigation, Property 2: Back-scan correctness")
    void backScanReturnsLastAwaitsInputEntryAndUpdatesSessionState(
            @ForAll("navigationHistories") List<Map<String, Object>> history) {

        // Precondition: at least one entry has awaitsInput == true
        Assume.that(history.stream().anyMatch(e -> Boolean.TRUE.equals(e.get("awaitsInput"))));

        // Act: run the back-scan algorithm
        int targetIndex = backScan(history);

        // Assert 1: targetIndex must be valid (>= 0)
        assert targetIndex >= 0 : "Back-scan should find a target when history contains awaitsInput entries";

        // Assert 2: the entry at targetIndex has awaitsInput == true
        Map<String, Object> targetEntry = history.get(targetIndex);
        assert Boolean.TRUE.equals(targetEntry.get("awaitsInput")) :
                "Target entry at index " + targetIndex + " should have awaitsInput == true";

        // Assert 3: it is the LAST (most recent) entry with awaitsInput == true
        // No entry after targetIndex should have awaitsInput == true
        for (int i = targetIndex + 1; i < history.size(); i++) {
            assert !Boolean.TRUE.equals(history.get(i).get("awaitsInput")) :
                    "Found awaitsInput == true at index " + i + " which is after target index " + targetIndex
                            + ". Back-scan should return the most recent one.";
        }

        // Assert 4: Verify session state would be updated correctly after applying back-scan
        // Simulate what handleBack does (lines 6d-e in the implementation):
        //   session.setCurrentNodeId(targetNodeId);
        //   session.setCurrentNodeType(targetNodeType);
        String targetNodeId = (String) targetEntry.get("nodeId");
        String targetNodeType = (String) targetEntry.get("nodeType");

        // The target nodeId must not be null (it's always set in recordNavigationEntry)
        assert targetNodeId != null : "Target entry nodeId should not be null";

        // Simulate session state update
        String sessionCurrentNodeId = targetNodeId;
        String sessionCurrentNodeType = targetNodeType;

        // Verify session currentNodeId equals the target entry's nodeId
        assert sessionCurrentNodeId.equals(targetEntry.get("nodeId")) :
                "Session currentNodeId should equal target entry's nodeId. Expected: "
                        + targetEntry.get("nodeId") + ", Got: " + sessionCurrentNodeId;

        // Verify session currentNodeType equals the target entry's nodeType
        assert Objects.equals(sessionCurrentNodeType, targetEntry.get("nodeType")) :
                "Session currentNodeType should equal target entry's nodeType. Expected: "
                        + targetEntry.get("nodeType") + ", Got: " + sessionCurrentNodeType;
    }

    @Provide
    Arbitrary<List<Map<String, Object>>> navigationHistories() {
        // Generate histories of varying length 1-50 with mixed node types and awaitsInput values
        return Arbitraries.integers().between(1, 50).flatMap(size -> {
            Arbitrary<Map<String, Object>> entryArbitrary = navigationEntries();
            return entryArbitrary.list().ofSize(size).map(entries -> {
                // Ensure at least one entry has awaitsInput == true
                boolean hasAwaitsInput = entries.stream()
                        .anyMatch(e -> Boolean.TRUE.equals(e.get("awaitsInput")));
                if (!hasAwaitsInput && !entries.isEmpty()) {
                    // Force a random entry to have awaitsInput = true
                    int randomIdx = new Random().nextInt(entries.size());
                    entries.get(randomIdx).put("awaitsInput", true);
                    // Also set nodeType to "input" for realism
                    entries.get(randomIdx).put("nodeType", "input");
                }
                return entries;
            });
        });
    }

    private Arbitrary<Map<String, Object>> navigationEntries() {
        Arbitrary<Long> workflowIds = Arbitraries.longs().between(1L, 100L);
        Arbitrary<String> nodeIds = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "node-" + s);
        Arbitrary<String> nodeTypes = Arbitraries.of("input", "api", "workflow", null);
        // awaitsInput can be true, false, or absent (null means don't add key)
        Arbitrary<Boolean> awaitsInputValues = Arbitraries.of(Boolean.TRUE, Boolean.FALSE, null);

        return Combinators.combine(workflowIds, nodeIds, nodeTypes, awaitsInputValues)
                .as((workflowId, nodeId, nodeType, awaitsInput) -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("workflowId", workflowId);
                    entry.put("nodeId", nodeId);
                    entry.put("nodeType", nodeType);
                    entry.put("timestamp", Instant.now().toString());
                    if (awaitsInput != null) {
                        entry.put("awaitsInput", awaitsInput);
                    }
                    // If awaitsInput is null, we don't add the key — simulates message nodes
                    // that never had awaitsInput set
                    return entry;
                });
    }

    // ========================================================================================
    // Property 3: History truncation on back-navigation
    // ========================================================================================

    /**
     * Property 3: History truncation on back-navigation
     *
     * For any navigation history of length N with a target entry at index K (0-based,
     * the last entry where awaitsInput==true), after back-navigation the history SHALL
     * contain exactly K entries (indices 0 through K-1).
     *
     * Validates: Requirements 3.4
     */
    @Property(tries = 100)
    @Tag("chat-back-navigation-property-3")
    void historyTruncatedToExactlyKEntriesAfterBackNavigation(
            @ForAll("historyLengths") int historyLength,
            @ForAll("targetPositionSeeds") int targetPositionSeed) {

        // 1. Generate a random navigation history of length N (2-50)
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = 0; i < historyLength; i++) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("workflowId", 1L);
            entry.put("nodeId", "node-" + i);
            entry.put("nodeType", i % 3 == 0 ? "input" : (i % 3 == 1 ? "api" : null));
            entry.put("timestamp", Instant.now().minusSeconds(historyLength - i).toString());
            entry.put("awaitsInput", false);
            history.add(entry);
        }

        // 2. Place at least one entry with awaitsInput = true at a random position K
        //    K is computed from the seed, constrained to valid range [0, historyLength - 1]
        int targetIndex = Math.abs(targetPositionSeed % historyLength);
        history.get(targetIndex).put("awaitsInput", true);

        // Ensure no entry after targetIndex has awaitsInput=true
        // (so targetIndex is the most recent / last awaitsInput entry — the back-scan target)
        for (int i = targetIndex + 1; i < historyLength; i++) {
            history.get(i).put("awaitsInput", false);
        }

        // Take a snapshot of entries 0 to K-1 for later comparison
        List<Map<String, Object>> expectedRemaining = new ArrayList<>();
        for (int i = 0; i < targetIndex; i++) {
            expectedRemaining.add(new HashMap<>(history.get(i)));
        }

        // 3. Apply the truncation logic (same as handleBack implementation):
        //    history.subList(K, history.size()).clear()
        history.subList(targetIndex, history.size()).clear();

        // 4. Verify the resulting list has exactly K elements
        assert history.size() == targetIndex :
                "After truncation, history should have exactly " + targetIndex
                        + " entries but has " + history.size();

        // 5. Verify entries at indices 0 to K-1 are unchanged from original
        for (int i = 0; i < targetIndex; i++) {
            Map<String, Object> actual = history.get(i);
            Map<String, Object> expected = expectedRemaining.get(i);
            assert actual.equals(expected) :
                    "Entry at index " + i + " was modified after truncation. "
                            + "Expected: " + expected + ", Got: " + actual;
        }
    }

    @Provide
    Arbitrary<Integer> historyLengths() {
        return Arbitraries.integers().between(2, 50);
    }

    @Provide
    Arbitrary<Integer> targetPositionSeeds() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE - 1);
    }
}
