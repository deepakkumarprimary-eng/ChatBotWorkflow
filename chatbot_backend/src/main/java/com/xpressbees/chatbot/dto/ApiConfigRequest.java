package com.xpressbees.chatbot.dto;

import java.util.List;

import lombok.Data;

@Data
public class ApiConfigRequest {
    private String name;
    private String url;
    private String method;
    private Integer timeoutMs;
    private Integer retryCount;
    private String username;
    private String password;
    private String clientId;
    private List<ApiHeaderDto> headers;
    private Object payloadTemplate;
    private List<ApiResponseMappingDto> responseMappings;
}
