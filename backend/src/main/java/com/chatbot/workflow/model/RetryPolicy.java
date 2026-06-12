package com.chatbot.workflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Defines the retry policy for a state, including max retries and backoff interval.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetryPolicy {

    private final int maxRetries;
    private final int backoffIntervalSeconds;

    @JsonCreator
    public RetryPolicy(
            @JsonProperty("maxRetries") int maxRetries,
            @JsonProperty("backoffInterval") int backoffIntervalSeconds) {
        this.maxRetries = maxRetries;
        this.backoffIntervalSeconds = backoffIntervalSeconds;
    }

    @JsonProperty("maxRetries")
    public int getMaxRetries() {
        return maxRetries;
    }

    @JsonProperty("backoffInterval")
    public int getBackoffIntervalSeconds() {
        return backoffIntervalSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetryPolicy that = (RetryPolicy) o;
        return maxRetries == that.maxRetries && backoffIntervalSeconds == that.backoffIntervalSeconds;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(maxRetries);
        result = 31 * result + Integer.hashCode(backoffIntervalSeconds);
        return result;
    }

    @Override
    public String toString() {
        return "RetryPolicy{maxRetries=" + maxRetries + ", backoffIntervalSeconds=" + backoffIntervalSeconds + "}";
    }
}
