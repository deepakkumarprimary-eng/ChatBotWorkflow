package com.xpressbees.chatbot.service;

import java.util.List;

import com.xpressbees.chatbot.dto.ApiConfigRequest;
import com.xpressbees.chatbot.dto.ApiConfigResponse;

public interface ApiConfigService {
    ApiConfigResponse create(ApiConfigRequest request);
    ApiConfigResponse getById(Long id);
    List<ApiConfigResponse> listAll();
    ApiConfigResponse update(Long id, ApiConfigRequest request);
    void delete(Long id);
}
