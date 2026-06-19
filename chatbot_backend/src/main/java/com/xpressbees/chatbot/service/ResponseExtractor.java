package com.xpressbees.chatbot.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.xpressbees.chatbot.dto.ExtractionResult;
import com.xpressbees.chatbot.entity.ApiResponseMapping;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ResponseExtractor {

    public ExtractionResult extract(String jsonBody, List<ApiResponseMapping> mappings) {
        DocumentContext document;
        try {
            document = JsonPath.parse(jsonBody);
        } catch (InvalidJsonException e) {
            return new ExtractionResult(false, null, "Invalid response format: body is not valid JSON");
        }

        Map<String, String> extractedValues = new HashMap<>();

        for (ApiResponseMapping mapping : mappings) {
            String path = mapping.getResponsePath();
            String variableName = mapping.getContextVariableName();

            Object result;
            try {
                result = document.read(path);
            } catch (PathNotFoundException e) {
                // Path is valid but yields no match — skip this mapping
                continue;
            } catch (InvalidPathException e) {
                return new ExtractionResult(false, null,
                        "Failed to extract '" + variableName + "' using path '" + path + "'");
            }

            if (result == null) {
                continue;
            }

            if (result instanceof List<?> list) {
                String joined = list.stream()
                        .filter(item -> item != null)
                        .map(String::valueOf)
                        .collect(Collectors.joining("\n"));
                if (!joined.isEmpty()) {
                    extractedValues.put(variableName, joined);
                }
            } else if (result instanceof String || result instanceof Number || result instanceof Boolean) {
                extractedValues.put(variableName, String.valueOf(result));
            }
        }

        return new ExtractionResult(true, extractedValues, null);
    }
}
