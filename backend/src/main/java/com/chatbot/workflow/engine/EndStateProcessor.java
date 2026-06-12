package com.chatbot.workflow.engine;

import org.springframework.stereotype.Component;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

/**
 * Processor for End states. Returns SUCCEEDED with no output and no next transition.
 */
@Component
public class EndStateProcessor implements StateProcessor {

    @Override
    public StateType getType() {
        return StateType.END;
    }

    @Override
    public StateProcessorResult process(StateDefinition state, ExecutionContext context) {
        return StateProcessorResult.builder()
                .outcome(StateOutcome.SUCCEEDED)
                .build();
    }
}
