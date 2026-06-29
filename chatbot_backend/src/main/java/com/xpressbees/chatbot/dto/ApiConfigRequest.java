package com.xpressbees.chatbot.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiConfigRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 1024)
    private String url;

    @NotBlank
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
