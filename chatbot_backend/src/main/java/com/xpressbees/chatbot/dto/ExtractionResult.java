package com.xpressbees.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ExtractionResult {
    private boolean success;
    private Map<String, String> extractedValues;
    private String errorMessage;
}
