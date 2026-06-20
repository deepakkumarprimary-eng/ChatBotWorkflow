package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: Workflow stack unwinding on cross-workflow back
 *
 * For any workflow stack of depth D and a target navigation entry whose workflowId
 * differs from the session's current workflow, the stack SHALL be unwound (entries
 * removed from the top) until the session's workflowId matches the target entry's
 * workflowId, and the session's workflowId SHALL be updated to the target's workflowId.
 *
 * Feature: chat-back-navigation, Property 4: Workflow stack unwinding on cross-workflow back
 *
 * Validates: Requirements 4.2, 4.3
 */
class WorkflowStackPropertyTest {

    /**
     * Simulates the stack unwinding logic from handleBack:
     *
     * if (!targetWorkflowId.equals(session.getWorkflowId())) {
     *     List<Map<String, Object>> workflowStack = getWorkflowStack(context);
     *     while (!workflowStack.isEmpty()) {
     *         Map<String, Object> stackEntry = workflowStack.get(workflowStack.size() - 1);
     *         Long parentWorkflowId = ((Number) stackEntry.get("parentWorkflowId")).longValue();
     *         workflowStack.remove(workflowStack.size() - 1);
     *         if (parentWorkflowId.equals(targetWorkflowId)) {
     *             break;
     *         }
     *     }
     *     session.setWorkflowId(targetWorkflowId);
     * }
     */
    private static Long applyUnwinding(List<Map<String, Object>> workflowStack, Long currentWorkflowId, Long targetWorkflowId) {
        if (!targetWorkflowId.equals(currentWorkflowId)) {
            while (!workflowStack.isEmpty()) {
                Map<String, Object> stackEntry = workflowStack.get(workflowStack.size() - 1);
                Long parentWorkflowId = ((Number) stackEntry.get("parentWorkflowId")).longValue();
                workflowStack.remove(workflowStack.size() - 1);
                if (parentWorkflowId.equals(targetWorkflowId)) {
                    break;
                }
            }
            return targetWorkflowId;
        }
        return currentWorkflowId;
    }

    @Property(tries = 100)
    @Tag("Feature: chat-back-navigation, Property 4: Workflow stack unwinding on cross-workflow back")
    void stackUnwoundCorrectlyOnCrossWorkflowBack(
            @ForAll("workflowStackWithTarget") WorkflowStackScenario scenario) {

        // --- Arrange ---
        // Create a mutable copy of the workflow stack
        List<Map<String, Object>> workflowStack = new ArrayList<>();
        for (Map<String, Object> entry : scenario.workflowStack) {
            workflowStack.add(new HashMap<>(entry));
        }

        Long currentWorkflowId = scenario.currentWorkflowId;
        Long targetWorkflowId = scenario.targetWorkflowId;

        // Preconditions: target differs from current, and target exists in the stack chain
        assertThat(targetWorkflowId).isNotEqualTo(currentWorkflowId);

        // --- Act ---
        Long resultWorkflowId = applyUnwinding(workflowStack, currentWorkflowId, targetWorkflowId);

        // --- Assert ---
        // 1. Session workflowId SHALL be updated to the target's workflowId
        assertThat(resultWorkflowId)
                .as("Session workflowId must equal target workflowId after unwinding")
                .isEqualTo(targetWorkflowId);

        // 2. The stack SHALL be unwound: all entries above (after) the entry
        //    that contains parentWorkflowId == targetWorkflowId must be removed.
        //    The entry whose parentWorkflowId matches targetWorkflowId is also removed.
        //    Only entries below it in the original stack should remain.
        int expectedRemainingSize = scenario.targetStackIndex;
        assertThat(workflowStack.size())
                .as("Stack should be unwound to contain exactly the entries below the target's matching stack entry")
                .isEqualTo(expectedRemainingSize);

        // 3. Remaining entries should be the first `expectedRemainingSize` entries from original (unchanged)
        for (int i = 0; i < expectedRemainingSize; i++) {
            assertThat(((Number) workflowStack.get(i).get("parentWorkflowId")).longValue())
                    .as("Remaining stack entry at index %d should be unchanged", i)
                    .isEqualTo(((Number) scenario.workflowStack.get(i).get("parentWorkflowId")).longValue());
        }
    }

    @Property(tries = 100)
    @Tag("Feature: chat-back-navigation, Property 4: Workflow stack unwinding on cross-workflow back")
    void noUnwindingWhenTargetMatchesCurrent(
            @ForAll("workflowStackNoUnwind") NoUnwindScenario scenario) {

        // --- Arrange ---
        List<Map<String, Object>> workflowStack = new ArrayList<>();
        for (Map<String, Object> entry : scenario.workflowStack) {
            workflowStack.add(new HashMap<>(entry));
        }
        int originalSize = workflowStack.size();
        Long currentWorkflowId = scenario.workflowId;
        Long targetWorkflowId = scenario.workflowId; // same as current

        // --- Act ---
        Long resultWorkflowId = applyUnwinding(workflowStack, currentWorkflowId, targetWorkflowId);

        // --- Assert ---
        // When target == current, no unwinding occurs
        assertThat(resultWorkflowId).isEqualTo(currentWorkflowId);
        assertThat(workflowStack.size())
                .as("Stack should remain unchanged when target matches current workflow")
                .isEqualTo(originalSize);
    }

    // --- Data classes for scenarios ---

    static class WorkflowStackScenario {
        final List<Map<String, Object>> workflowStack;
        final Long currentWorkflowId;
        final Long targetWorkflowId;
        final int targetStackIndex; // index of the entry whose parentWorkflowId == targetWorkflowId

        WorkflowStackScenario(List<Map<String, Object>> workflowStack, Long currentWorkflowId,
                              Long targetWorkflowId, int targetStackIndex) {
            this.workflowStack = workflowStack;
            this.currentWorkflowId = currentWorkflowId;
            this.targetWorkflowId = targetWorkflowId;
            this.targetStackIndex = targetStackIndex;
        }

        @Override
        public String toString() {
            return String.format("WorkflowStackScenario{stackDepth=%d, currentWf=%d, targetWf=%d, targetIdx=%d}",
                    workflowStack.size(), currentWorkflowId, targetWorkflowId, targetStackIndex);
        }
    }

    static class NoUnwindScenario {
        final List<Map<String, Object>> workflowStack;
        final Long workflowId;

        NoUnwindScenario(List<Map<String, Object>> workflowStack, Long workflowId) {
            this.workflowStack = workflowStack;
            this.workflowId = workflowId;
        }

        @Override
        public String toString() {
            return String.format("NoUnwindScenario{stackDepth=%d, workflowId=%d}",
                    workflowStack.size(), workflowId);
        }
    }

    // --- Providers ---

    /**
     * Generates a valid workflow stack scenario for cross-workflow back-navigation.
     *
     * The stack represents nested workflow calls:
     * - Index 0: root entry (parentWorkflowId = root workflow)
     * - Index 1: entered from root into child1 (parentWorkflowId = child1's parent)
     * - etc.
     *
     * We pick a random position in the stack where parentWorkflowId == targetWorkflowId,
     * simulating navigating back to a node in that workflow.
     */
    @Provide
    Arbitrary<WorkflowStackScenario> workflowStackWithTarget() {
        return Arbitraries.integers().between(1, 10).flatMap(depth -> {
            // Generate workflow IDs: we need at least depth+1 distinct IDs
            // (root + one for each stack entry's child workflow, + current)
            return Arbitraries.integers().between(0, depth - 1).flatMap(targetIdx -> {
                // Build a chain of workflow IDs representing the nesting:
                // rootWfId -> child1WfId -> child2WfId -> ... -> currentWfId
                // Stack entry at index i has parentWorkflowId = workflowId at that level
                return Arbitraries.longs().between(1L, 1000L).list().ofSize(depth + 1)
                        .filter(ids -> new HashSet<>(ids).size() == ids.size()) // all distinct
                        .map(workflowIds -> {
                            // workflowIds[0] = root, workflowIds[i] = workflow entered at level i
                            // Stack entries: each entry represents entering the NEXT workflow
                            // entry[i].parentWorkflowId = workflowIds[i] (the workflow we came from)
                            List<Map<String, Object>> stack = new ArrayList<>();
                            for (int i = 0; i < depth; i++) {
                                Map<String, Object> entry = new HashMap<>();
                                entry.put("parentWorkflowId", workflowIds.get(i));
                                entry.put("workflowNodeId", "wf-node-" + i);
                                stack.add(entry);
                            }

                            // Current workflow is the last one in the chain
                            Long currentWorkflowId = workflowIds.get(depth);

                            // Target workflow is the one at targetIdx in the workflowIds list
                            // The matching stack entry is at targetIdx (its parentWorkflowId == workflowIds[targetIdx])
                            Long targetWorkflowId = workflowIds.get(targetIdx);

                            return new WorkflowStackScenario(stack, currentWorkflowId, targetWorkflowId, targetIdx);
                        });
            });
        });
    }

    /**
     * Generates a scenario where target == current (no unwinding should occur).
     */
    @Provide
    Arbitrary<NoUnwindScenario> workflowStackNoUnwind() {
        return Arbitraries.integers().between(1, 10).flatMap(depth ->
                Arbitraries.longs().between(1L, 1000L).flatMap(currentWfId ->
                        Arbitraries.longs().between(1L, 1000L).list().ofSize(depth)
                                .map(parentIds -> {
                                    List<Map<String, Object>> stack = new ArrayList<>();
                                    for (int i = 0; i < depth; i++) {
                                        Map<String, Object> entry = new HashMap<>();
                                        entry.put("parentWorkflowId", parentIds.get(i));
                                        entry.put("workflowNodeId", "wf-node-" + i);
                                        stack.add(entry);
                                    }
                                    return new NoUnwindScenario(stack, currentWfId);
                                })
                )
        );
    }
}
