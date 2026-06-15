package com.xpressbees.chatbot.service;

import java.util.List;

import com.xpressbees.chatbot.dto.WorkflowRequest;
import com.xpressbees.chatbot.dto.WorkflowResponse;

public interface WorkflowService {
    WorkflowResponse create(WorkflowRequest request);
    WorkflowResponse getById(Long id);
    List<WorkflowResponse> listAll();
    WorkflowResponse update(Long id, WorkflowRequest request);
    void delete(Long id);
}
