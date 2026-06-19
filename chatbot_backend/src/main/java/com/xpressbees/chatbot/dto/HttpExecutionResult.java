package com.xpressbees.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HttpExecutionResult {
    private boolean success;
    private int statusCode;
    private String responseBody;
    private String errorMessage;
}
