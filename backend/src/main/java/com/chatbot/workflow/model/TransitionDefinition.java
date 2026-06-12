package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Defines a transition between two states in a workflow.
 * The condition field can be "true", "false", "error", "timeout", "fallback", or null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransitionDefinition {

    private final UUID id;
    private final UUID source;
    private final UUID target;
    private final String condition;

    @JsonCreator
    public TransitionDefinition(
            @JsonProperty("id") UUID id,
            @JsonProperty("source") UUID source,
            @JsonProperty("target") UUID target,
            @JsonProperty("condition") String condition) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.condition = condition;
    }

    @JsonProperty("id")
    public UUID getId() {
        return id;
    }

    @JsonProperty("source")
    public UUID getSource() {
        return source;
    }

    @JsonProperty("target")
    public UUID getTarget() {
        return target;
    }

    @JsonProperty("condition")
    public String getCondition() {
        return condition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransitionDefinition that = (TransitionDefinition) o;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (source != null ? !source.equals(that.source) : that.source != null) return false;
        if (target != null ? !target.equals(that.target) : that.target != null) return false;
        return condition != null ? condition.equals(that.condition) : that.condition == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (source != null ? source.hashCode() : 0);
        result = 31 * result + (target != null ? target.hashCode() : 0);
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TransitionDefinition{id=" + id + ", source=" + source +
                ", target=" + target + ", condition='" + condition + "'}";
    }
}
