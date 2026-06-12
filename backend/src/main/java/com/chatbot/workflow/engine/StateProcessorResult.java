package com.chatbot.workflow.engine;

import java.util.Collections;
import java.util.Map;

import com.chatbot.workflow.model.StateOutcome;

/**
 * Result DTO returned by a StateProcessor after processing a state.
 * Contains the output variables, transition condition, pause flag, error info, and outcome.
 */
public class StateProcessorResult {

    private final Map<String, Object> outputVariables;
    private final String nextTransitionCondition;
    private final boolean paused;
    private final String errorMessage;
    private final StateOutcome outcome;

    private StateProcessorResult(Builder builder) {
        this.outputVariables = builder.outputVariables != null ? builder.outputVariables : Collections.emptyMap();
        this.nextTransitionCondition = builder.nextTransitionCondition;
        this.paused = builder.paused;
        this.errorMessage = builder.errorMessage;
        this.outcome = builder.outcome;
    }

    public Map<String, Object> getOutputVariables() {
        return outputVariables;
    }

    /**
     * Which transition to follow after this state.
     * null = default (follow transition with no condition),
     * "true"/"false" = for condition states,
     * "error"/"timeout" = for failures.
     */
    public String getNextTransitionCondition() {
        return nextTransitionCondition;
    }

    /**
     * True if execution should pause (for Input/Wait states).
     */
    public boolean isPaused() {
        return paused;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public StateOutcome getOutcome() {
        return outcome;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience factory for a simple successful result with no output.
     */
    public static StateProcessorResult success() {
        return new Builder().outcome(StateOutcome.SUCCEEDED).build();
    }

    /**
     * Convenience factory for a failed result.
     */
    public static StateProcessorResult failure(String errorMessage) {
        return new Builder()
                .outcome(StateOutcome.FAILED)
                .errorMessage(errorMessage)
                .nextTransitionCondition("error")
                .build();
    }

    public static class Builder {
        private Map<String, Object> outputVariables;
        private String nextTransitionCondition;
        private boolean paused;
        private String errorMessage;
        private StateOutcome outcome;

        public Builder outputVariables(Map<String, Object> outputVariables) {
            this.outputVariables = outputVariables;
            return this;
        }

        public Builder nextTransitionCondition(String nextTransitionCondition) {
            this.nextTransitionCondition = nextTransitionCondition;
            return this;
        }

        public Builder paused(boolean paused) {
            this.paused = paused;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder outcome(StateOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public StateProcessorResult build() {
            return new StateProcessorResult(this);
        }
    }
}
