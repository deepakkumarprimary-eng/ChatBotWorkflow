package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a workflow-level context variable with a name and optional default value.
 * Names must match the pattern ^[a-zA-Z0-9_]{1,64}$.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContextVariable {

    private final String name;
    private final Object defaultValue;

    @JsonCreator
    public ContextVariable(
            @JsonProperty("name") String name,
            @JsonProperty("defaultValue") Object defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("defaultValue")
    public Object getDefaultValue() {
        return defaultValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextVariable that = (ContextVariable) o;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return defaultValue != null ? defaultValue.equals(that.defaultValue) : that.defaultValue == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (defaultValue != null ? defaultValue.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ContextVariable{name='" + name + "', defaultValue=" + defaultValue + "}";
    }
}
