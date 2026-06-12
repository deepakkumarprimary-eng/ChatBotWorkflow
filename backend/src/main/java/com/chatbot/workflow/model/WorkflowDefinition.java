package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The canonical JSON representation of a workflow containing all states,
 * transitions, context variables, and metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowDefinition {

    private final WorkflowMetadata metadata;
    private final List<StateDefinition> states;
    private final List<TransitionDefinition> transitions;
    private final List<ContextVariable> contextVariables;

    @JsonCreator
    public WorkflowDefinition(
            @JsonProperty("metadata") WorkflowMetadata metadata,
            @JsonProperty("states") List<StateDefinition> states,
            @JsonProperty("transitions") List<TransitionDefinition> transitions,
            @JsonProperty("contextVariables") List<ContextVariable> contextVariables) {
        this.metadata = metadata;
        this.states = states;
        this.transitions = transitions;
        this.contextVariables = contextVariables;
    }

    @JsonProperty("metadata")
    public WorkflowMetadata getMetadata() {
        return metadata;
    }

    @JsonProperty("states")
    public List<StateDefinition> getStates() {
        return states;
    }

    @JsonProperty("transitions")
    public List<TransitionDefinition> getTransitions() {
        return transitions;
    }

    @JsonProperty("contextVariables")
    public List<ContextVariable> getContextVariables() {
        return contextVariables;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowDefinition that = (WorkflowDefinition) o;
        if (metadata != null ? !metadata.equals(that.metadata) : that.metadata != null) return false;
        if (states != null ? !states.equals(that.states) : that.states != null) return false;
        if (transitions != null ? !transitions.equals(that.transitions) : that.transitions != null) return false;
        return contextVariables != null ? contextVariables.equals(that.contextVariables) : that.contextVariables == null;
    }

    @Override
    public int hashCode() {
        int result = metadata != null ? metadata.hashCode() : 0;
        result = 31 * result + (states != null ? states.hashCode() : 0);
        result = 31 * result + (transitions != null ? transitions.hashCode() : 0);
        result = 31 * result + (contextVariables != null ? contextVariables.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "WorkflowDefinition{metadata=" + metadata +
                ", states=" + (states != null ? states.size() : 0) +
                ", transitions=" + (transitions != null ? transitions.size() : 0) + "}";
    }
}
