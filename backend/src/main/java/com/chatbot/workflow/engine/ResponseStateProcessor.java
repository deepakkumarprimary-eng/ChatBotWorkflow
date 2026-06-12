package com.chatbot.workflow.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.StateOutcome;
import com.chatbot.workflow.model.StateType;

/**
 * Processor for Response states. Interpolates context variables into message templates
 * using {{variableName}} syntax and stores the resulting message in output variables.
 * 
 * Undefined variables are replaced with the string "null".
 * The interpolated message is stored as the output variable "_responseMessage".
 */
@Component
public class ResponseStateProcessor implements StateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ResponseStateProcessor.class);
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    @Override
    public StateType getType() {
        return StateType.RESPONSE;
    }

    @Override
    public StateProcessorResult process(StateDefinition state, ExecutionContext context) {
        Map<String, Object> config = state.getConfig();
        if (config == null) {
            return StateProcessorResult.failure("Response state has no configuration");
        }

        Object templateObj = config.get("template");
        if (templateObj == null) {
            return StateProcessorResult.failure("Response state is missing 'template' in configuration");
        }

        String template = templateObj.toString();
        String interpolatedMessage = interpolateTemplate(template, context);

        // Log the message (real delivery will be integrated when the chatbot messaging layer is built)
        logger.info("Response message for execution {}: {}", context.getExecutionId(), interpolatedMessage);

        // Store the interpolated message as output variable
        Map<String, Object> outputVariables = new HashMap<>();
        outputVariables.put("_responseMessage", interpolatedMessage);

        return StateProcessorResult.builder()
                .outcome(StateOutcome.SUCCEEDED)
                .outputVariables(outputVariables)
                .build();
    }

    /**
     * Interpolates all {{variableName}} references in the given template string
     * with values from the execution context. Undefined variables are replaced with "null".
     */
    String interpolateTemplate(String template, ExecutionContext context) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = context.getVariable(variableName);
            String replacement = value != null ? value.toString() : "null";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
