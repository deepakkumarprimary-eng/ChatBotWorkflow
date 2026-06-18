package com.xpressbees.chatbot.dto;

import lombok.Data;

@Data
public class ApiResponseMappingDto {
    private String responsePath;
    private String contextVariableName;
}
