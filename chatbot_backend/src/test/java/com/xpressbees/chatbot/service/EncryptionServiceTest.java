package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.exception.EncryptionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for EncryptionService covering startup validation,
 * null handling, and decryption failure scenarios.
 *
 * Validates: Requirements 1.3, 1.4, 1.5
 */
class EncryptionServiceTest {

    // Valid 32-byte key encoded as Base64 (dev key)
    private static final String VALID_KEY = "ZGV2LWxvY2FsLWVuY3J5cHRpb24ta2V5LTMyYnl0ZXM=";

    @Nested
    @DisplayName("Constructor validation - key missing or invalid")
    class ConstructorValidation {

        @Test
        @DisplayName("Throws IllegalStateException when key is null")
        void throwsWhenKeyIsNull() {
            assertThatThrownBy(() -> new EncryptionService(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Encryption key is not configured");
        }

        @Test
        @DisplayName("Throws IllegalStateException when key is empty")
        void throwsWhenKeyIsEmpty() {
            assertThatThrownBy(() -> new EncryptionService(""))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Encryption key is not configured");
        }

        @Test
        @DisplayName("Throws IllegalStateException when key is blank (whitespace only)")
        void throwsWhenKeyIsBlank() {
            assertThatThrownBy(() -> new EncryptionService("   "))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Encryption key is not configured");
        }

        @Test
        @DisplayName("Throws IllegalStateException when key is not valid Base64")
        void throwsWhenKeyIsInvalidBase64() {
            assertThatThrownBy(() -> new EncryptionService("not-valid-base64!!!@@@"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not valid Base64");
        }

        @Test
        @DisplayName("Throws IllegalStateException when key is valid Base64 but not 32 bytes")
        void throwsWhenKeyIsWrongLength() {
            // "c2hvcnRrZXk=" decodes to "shortkey" which is 8 bytes, not 32
            assertThatThrownBy(() -> new EncryptionService("c2hvcnRrZXk="))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be exactly 32 bytes");
        }
    }

    @Nested
    @DisplayName("Decryption failure with corrupted ciphertext")
    class DecryptionFailure {

        @Test
        @DisplayName("Throws EncryptionException when ciphertext is corrupted")
        void throwsEncryptionExceptionOnCorruptedCiphertext() {
            EncryptionService service = new EncryptionService(VALID_KEY);

            // Valid Base64 but not a valid AES-GCM ciphertext
            String corruptedCiphertext = "dGhpcyBpcyBub3QgYSB2YWxpZCBjaXBoZXJ0ZXh0IGF0IGFsbA==";

            assertThatThrownBy(() -> service.decrypt(corruptedCiphertext))
                    .isInstanceOf(EncryptionException.class)
                    .hasMessageContaining("Decryption failed");
        }
    }

    @Nested
    @DisplayName("Null input handling")
    class NullInputHandling {

        @Test
        @DisplayName("encrypt(null) returns null")
        void encryptNullReturnsNull() {
            EncryptionService service = new EncryptionService(VALID_KEY);

            assertThat(service.encrypt(null)).isNull();
        }

        @Test
        @DisplayName("decrypt(null) returns null")
        void decryptNullReturnsNull() {
            EncryptionService service = new EncryptionService(VALID_KEY);

            assertThat(service.decrypt(null)).isNull();
        }
    }
}
