package com.chatbot.workflow.engine;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateType;

/**
 * Interface for processing a specific type of workflow state.
 * Each state type has a corresponding implementation.
 */
public interface StateProcessor {

    /**
     * Returns the state type this processor handles.
     */
    StateType getType();

    /**
     * Process the given state with the given execution context.
     *
     * @param state   the state definition to process
     * @param context the current execution context (mutable)
     * @return the result of processing the state
     */
    StateProcessorResult process(StateDefinition state, ExecutionContext context);
}
