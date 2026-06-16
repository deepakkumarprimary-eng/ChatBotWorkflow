package com.xpressbees.chatbot.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class ApiConfigResponse {
    private Long id;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
