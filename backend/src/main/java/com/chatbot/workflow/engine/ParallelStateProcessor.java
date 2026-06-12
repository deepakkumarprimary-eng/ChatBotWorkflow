package com.chatbot.workflow.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

/**
 * Processor for Parallel states. Executes 2-10 branches concurrently using a thread pool,
 * merges branch outputs in branch-definition order, and handles failures by canceling
 * remaining branches.
 */
@Component
public class ParallelStateProcessor implements StateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ParallelStateProcessor.class);

    private static final int MIN_BRANCHES = 2;
    private static final int MAX_BRANCHES = 10;

    @Override
    public StateType getType() {
        return StateType.PARALLEL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StateProcessorResult process(StateDefinition state, ExecutionContext context) {
        // 1. Validate config exists
        Map<String, Object> config = state.getConfig();
        if (config == null) {
            return StateProcessorResult.failure("Parallel state requires config with branches");
        }

        // 2. Extract branches from config
        Object branchesObj = config.get("branches");
        if (branchesObj == null || !(branchesObj instanceof List)) {
            return StateProcessorResult.failure("Parallel state requires 'branches' list in config");
        }

        List<Map<String, Object>> branches;
        try {
            branches = (List<Map<String, Object>>) branchesObj;
        } catch (ClassCastException e) {
            return StateProcessorResult.failure("Parallel state 'branches' must be a list of branch definitions");
        }

        // 3. Validate branch count (2-10)
        if (branches.size() < MIN_BRANCHES || branches.size() > MAX_BRANCHES) {
            return StateProcessorResult.failure(
                    "Parallel state requires between " + MIN_BRANCHES + " and " + MAX_BRANCHES
                            + " branches, got " + branches.size());
        }

        // 4. Execute branches concurrently
        int branchCount = branches.size();
        ExecutorService executor = Executors.newFixedThreadPool(branchCount);

        try {
            // Create a copy of current context variables for each branch
            Map<String, Object> currentVars = context.getContextVariables();

            List<Callable<BranchResult>> tasks = new ArrayList<>();
            for (int i = 0; i < branchCount; i++) {
                Map<String, Object> branchDef = branches.get(i);
                int branchIndex = i;
                // Each branch gets its own copy of the context
                Map<String, Object> branchContextCopy = new HashMap<>(currentVars);

                tasks.add(() -> executeBranch(branchDef, branchContextCopy, branchIndex));
            }

            // Submit all tasks and collect futures
            List<Future<BranchResult>> futures = new ArrayList<>();
            for (Callable<BranchResult> task : tasks) {
                futures.add(executor.submit(task));
            }

            // 5. Wait for all branches and collect results
            List<BranchResult> results = new ArrayList<>(Collections.nCopies(branchCount, null));
            String failureMessage = null;

            for (int i = 0; i < branchCount; i++) {
                try {
                    BranchResult result = futures.get(i).get();
                    results.set(i, result);

                    if (!result.isSuccess()) {
                        failureMessage = "Branch " + i + " failed: " + result.getErrorMessage();
                        // Cancel remaining branches
                        cancelRemainingFutures(futures, i + 1);
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancelRemainingFutures(futures, i);
                    return StateProcessorResult.failure("Parallel execution interrupted");
                } catch (ExecutionException e) {
                    failureMessage = "Branch " + i + " failed: " +
                            (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    cancelRemainingFutures(futures, i + 1);
                    break;
                } catch (CancellationException e) {
                    // Branch was cancelled, skip
                    failureMessage = "Branch " + i + " was cancelled";
                    break;
                }
            }

            // 6. Handle failure
            if (failureMessage != null) {
                logger.warn("Parallel state '{}' failed: {}", state.getName(), failureMessage);
                return StateProcessorResult.failure(failureMessage);
            }

            // 7. Merge outputs in branch-definition order (later branches overwrite earlier ones)
            Map<String, Object> mergedOutput = new LinkedHashMap<>();
            for (int i = 0; i < branchCount; i++) {
                BranchResult branchResult = results.get(i);
                if (branchResult != null && branchResult.getOutputVariables() != null) {
                    mergedOutput.putAll(branchResult.getOutputVariables());
                }
            }

            return StateProcessorResult.builder()
                    .outcome(StateOutcome.SUCCEEDED)
                    .outputVariables(mergedOutput)
                    .build();

        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Executes a single branch. In this simplified model, each branch config has a "name"
     * and "outputVariables" map that simulates what the branch produces.
     * For full execution, this would recursively process a sub-workflow.
     */
    @SuppressWarnings("unchecked")
    private BranchResult executeBranch(Map<String, Object> branchDef, Map<String, Object> branchContext, int branchIndex) {
        String branchName = branchDef != null ? (String) branchDef.get("name") : "branch-" + branchIndex;

        if (branchDef == null) {
            return BranchResult.failure("Branch " + branchIndex + " definition is null");
        }

        // Check if branch should simulate a failure
        Object simulateFailure = branchDef.get("simulateFailure");
        if (Boolean.TRUE.equals(simulateFailure)) {
            String errorMsg = branchDef.containsKey("errorMessage")
                    ? (String) branchDef.get("errorMessage")
                    : "Simulated branch failure";
            return BranchResult.failure(errorMsg);
        }

        // Extract output variables from branch definition
        Map<String, Object> outputVariables = new HashMap<>();
        Object outputObj = branchDef.get("outputVariables");
        if (outputObj instanceof Map) {
            try {
                outputVariables.putAll((Map<String, Object>) outputObj);
            } catch (ClassCastException e) {
                return BranchResult.failure("Invalid outputVariables format in branch " + branchIndex);
            }
        }

        logger.debug("Branch '{}' (index {}) completed with {} output variables",
                branchName, branchIndex, outputVariables.size());

        return BranchResult.success(outputVariables);
    }

    /**
     * Cancel all futures from startIndex onwards.
     */
    private void cancelRemainingFutures(List<Future<BranchResult>> futures, int startIndex) {
        for (int i = startIndex; i < futures.size(); i++) {
            futures.get(i).cancel(true);
        }
    }

    /**
     * Internal result class for branch execution.
     */
    private static class BranchResult {
        private final boolean success;
        private final Map<String, Object> outputVariables;
        private final String errorMessage;

        private BranchResult(boolean success, Map<String, Object> outputVariables, String errorMessage) {
            this.success = success;
            this.outputVariables = outputVariables;
            this.errorMessage = errorMessage;
        }

        static BranchResult success(Map<String, Object> outputVariables) {
            return new BranchResult(true, outputVariables, null);
        }

        static BranchResult failure(String errorMessage) {
            return new BranchResult(false, null, errorMessage);
        }

        boolean isSuccess() {
            return success;
        }

        Map<String, Object> getOutputVariables() {
            return outputVariables;
        }

        String getErrorMessage() {
            return errorMessage;
        }
    }
}
