package com.xpressbees.chatbot.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.xpressbees.chatbot.dto.WorkflowRequest;
import com.xpressbees.chatbot.dto.WorkflowResponse;
import com.xpressbees.chatbot.entity.Workflow;
import com.xpressbees.chatbot.exception.WorkflowNotFoundException;
import com.xpressbees.chatbot.repository.WorkflowRepository;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowRepository workflowRepository;

    public WorkflowServiceImpl(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    @Override
    public WorkflowResponse create(WorkflowRequest request) {
        Workflow workflow = mapToEntity(request);
        Workflow saved = workflowRepository.save(workflow);
        return mapToResponse(saved);
    }

    @Override
    public WorkflowResponse getById(Long id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
        return mapToResponse(workflow);
    }

    @Override
    public List<WorkflowResponse> listAll() {
        return workflowRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public WorkflowResponse update(Long id, WorkflowRequest request) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
        workflow.setName(request.getName());
        workflow.setWorkflowJson(castToMap(request.getWorkflowJson()));
        Workflow saved = workflowRepository.save(workflow);
        return mapToResponse(saved);
    }

    @Override
    public void delete(Long id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
        workflowRepository.delete(workflow);
    }

    private Workflow mapToEntity(WorkflowRequest request) {
        Workflow workflow = new Workflow();
        workflow.setName(request.getName());
        workflow.setWorkflowJson(castToMap(request.getWorkflowJson()));
        return workflow;
    }

    private WorkflowResponse mapToResponse(Workflow workflow) {
        WorkflowResponse response = new WorkflowResponse();
        response.setId(workflow.getId());
        response.setName(workflow.getName());
        response.setWorkflowJson(workflow.getWorkflowJson());
        response.setCreatedAt(workflow.getCreatedAt());
        response.setUpdatedAt(workflow.getUpdatedAt());
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object json) {
        if (json instanceof Map) {
            return (Map<String, Object>) json;
        }
        return null;
    }
}
