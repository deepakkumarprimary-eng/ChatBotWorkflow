package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

/**
 * Defines a single state within a workflow, including its type, configuration,
 * canvas position, retry policy, and output mapping.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StateDefinition {

    private final UUID id;
    private final StateType type;
    private final String name;
    private final Position position;
    private final Map<String, Object> config;
    private final RetryPolicy retryPolicy;
    private final Map<String, String> outputMapping;

    @JsonCreator
    public StateDefinition(
            @JsonProperty("id") UUID id,
            @JsonProperty("type") StateType type,
            @JsonProperty("name") String name,
            @JsonProperty("position") Position position,
            @JsonProperty("config") Map<String, Object> config,
            @JsonProperty("retryPolicy") RetryPolicy retryPolicy,
            @JsonProperty("outputMapping") Map<String, String> outputMapping) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.position = position;
        this.config = config;
        this.retryPolicy = retryPolicy;
        this.outputMapping = outputMapping;
    }

    @JsonProperty("id")
    public UUID getId() {
        return id;
    }

    @JsonProperty("type")
    public StateType getType() {
        return type;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("position")
    public Position getPosition() {
        return position;
    }

    @JsonProperty("config")
    public Map<String, Object> getConfig() {
        return config;
    }

    @JsonProperty("retryPolicy")
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    @JsonProperty("outputMapping")
    public Map<String, String> getOutputMapping() {
        return outputMapping;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateDefinition that = (StateDefinition) o;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (type != that.type) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (position != null ? !position.equals(that.position) : that.position != null) return false;
        if (config != null ? !config.equals(that.config) : that.config != null) return false;
        if (retryPolicy != null ? !retryPolicy.equals(that.retryPolicy) : that.retryPolicy != null) return false;
        return outputMapping != null ? outputMapping.equals(that.outputMapping) : that.outputMapping == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (position != null ? position.hashCode() : 0);
        result = 31 * result + (config != null ? config.hashCode() : 0);
        result = 31 * result + (retryPolicy != null ? retryPolicy.hashCode() : 0);
        result = 31 * result + (outputMapping != null ? outputMapping.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "StateDefinition{id=" + id + ", type=" + type + ", name='" + name + "'}";
    }
}
