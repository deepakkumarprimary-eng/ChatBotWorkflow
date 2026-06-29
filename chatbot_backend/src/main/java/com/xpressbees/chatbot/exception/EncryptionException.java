package com.xpressbees.chatbot.exception;

/**
 * Runtime exception thrown when encryption or decryption operations fail.
 * Includes contextual information (e.g., affected ApiConfig identifier) for traceability.
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
