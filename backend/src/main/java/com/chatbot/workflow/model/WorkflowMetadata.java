package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Metadata for a workflow definition including name, description, version, and timestamps.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowMetadata {

    private final String name;
    private final String description;
    private final int version;
    private final Instant createdAt;
    private final Instant lastModifiedAt;

    @JsonCreator
    public WorkflowMetadata(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("version") int version,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("lastModifiedAt") Instant lastModifiedAt) {
        this.name = name;
        this.description = description;
        this.version = version;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("version")
    public int getVersion() {
        return version;
    }

    @JsonProperty("createdAt")
    public Instant getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("lastModifiedAt")
    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowMetadata that = (WorkflowMetadata) o;
        if (version != that.version) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        return lastModifiedAt != null ? lastModifiedAt.equals(that.lastModifiedAt) : that.lastModifiedAt == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + version;
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (lastModifiedAt != null ? lastModifiedAt.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "WorkflowMetadata{name='" + name + "', version=" + version + "}";
    }
}
