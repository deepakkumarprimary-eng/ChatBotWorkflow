package com.chatbot.workflow.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.chatbot.workflow.model.StateDefinition;
import com.chatbot.workflow.model.TransitionDefinition;
import com.chatbot.workflow.model.WorkflowDefinition;

/**
 * Validates the structure of an imported WorkflowDefinition.
 * Returns a list of validation error messages for any issues found.
 */
@Service
public class WorkflowImportValidator {

    /**
     * Validates an imported WorkflowDefinition for structural correctness.
     *
     * @param definition the workflow definition to validate
     * @return list of error messages; empty list indicates valid definition
     */
    public List<String> validate(WorkflowDefinition definition) {
        List<String> errors = new ArrayList<>();

        if (definition == null) {
            errors.add("Workflow definition must not be null");
            return errors;
        }

        validateMetadata(definition, errors);
        validateStates(definition, errors);
        validateTransitions(definition, errors);

        return errors;
    }

    private void validateMetadata(WorkflowDefinition definition, List<String> errors) {
        if (definition.getMetadata() == null) {
            errors.add("metadata is required");
        } else if (definition.getMetadata().getName() == null || definition.getMetadata().getName().trim().isEmpty()) {
            errors.add("metadata.name is required and must not be empty");
        }
    }

    private void validateStates(WorkflowDefinition definition, List<String> errors) {
        if (definition.getStates() == null) {
            errors.add("states list is required");
            return;
        }

        for (int i = 0; i < definition.getStates().size(); i++) {
            StateDefinition state = definition.getStates().get(i);
            if (state.getId() == null) {
                errors.add("states[" + i + "].id is required");
            }
            if (state.getType() == null) {
                errors.add("states[" + i + "].type is required and must be a valid state type");
            }
        }
    }

    private void validateTransitions(WorkflowDefinition definition, List<String> errors) {
        if (definition.getTransitions() == null) {
            errors.add("transitions list is required");
            return;
        }

        for (int i = 0; i < definition.getTransitions().size(); i++) {
            TransitionDefinition transition = definition.getTransitions().get(i);
            if (transition.getSource() == null) {
                errors.add("transitions[" + i + "].source is required");
            }
            if (transition.getTarget() == null) {
                errors.add("transitions[" + i + "].target is required");
            }
        }
    }
}
