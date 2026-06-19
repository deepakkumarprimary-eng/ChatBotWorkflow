package com.xpressbees.chatbot.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlaceholderService {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");

    public String resolve(String template, Map<String, Object> context) {
        if (template == null || context == null) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (context.containsKey(variableName)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(context.get(variableName))));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> resolvePayload(Map<String, Object> payloadTemplate, Map<String, Object> context) {
        if (payloadTemplate == null) {
            return null;
        }
        if (context == null) {
            return new HashMap<>(payloadTemplate);
        }

        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : payloadTemplate.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                resolved.put(key, resolve((String) value, context));
            } else if (value instanceof Map) {
                resolved.put(key, resolvePayload((Map<String, Object>) value, context));
            } else {
                resolved.put(key, value);
            }
        }
        return resolved;
    }
}
