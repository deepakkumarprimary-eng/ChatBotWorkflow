package com.xpressbees.chatbot.dto;

/**
 * Result of URL validation for SSRF protection.
 * Indicates whether an outbound URL is allowed or blocked, with a reason if blocked.
 */
public record UrlValidationResult(boolean isAllowed, String reason) {

    public static UrlValidationResult allowed() {
        return new UrlValidationResult(true, null);
    }

    public static UrlValidationResult blocked(String reason) {
        return new UrlValidationResult(false, reason);
    }
}
