package com.xpressbees.chatbot.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;

import static org.assertj.core.api.Assertions.assertThat;

// Feature: security-hardening, Property 1: Encryption round-trip preserves plaintext

/**
 * Property-based tests for EncryptionService.
 *
 * Property 1: For any valid string (including empty strings, unicode characters,
 * and strings up to 255 characters), encrypting with encrypt() and then decrypting
 * with decrypt() SHALL produce the original string.
 *
 * Validates: Requirements 1.1, 1.2, 1.6
 */
class EncryptionServicePropertyTest {

    // Dev key from application-dev.properties (Base64-encoded 32-byte key)
    private static final String DEV_ENCRYPTION_KEY = "ZGV2LWxvY2FsLWVuY3J5cHRpb24ta2V5LTMyYnl0ZXM=";

    private final EncryptionService encryptionService = new EncryptionService(DEV_ENCRYPTION_KEY);

    /**
     * Property 1: Encryption round-trip preserves plaintext.
     * For all arbitrary strings up to 255 characters, decrypt(encrypt(s)) == s.
     *
     * Validates: Requirements 1.1, 1.2, 1.6
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 1: Encryption round-trip preserves plaintext")
    void encryptThenDecryptProducesOriginalPlaintext(
            @ForAll @StringLength(max = 255) String plaintext) {
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    /**
     * Explicit test for empty string round-trip.
     *
     * Validates: Requirements 1.1, 1.2, 1.6
     */
    @Property(tries = 1)
    @Tag("Feature: security-hardening, Property 1: Encryption round-trip preserves plaintext")
    void encryptThenDecryptPreservesEmptyString() {
        String encrypted = encryptionService.encrypt("");
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo("");
    }

    /**
     * Explicit test for unicode characters round-trip.
     *
     * Validates: Requirements 1.1, 1.2, 1.6
     */
    @Property(tries = 1000)
    @Tag("Feature: security-hardening, Property 1: Encryption round-trip preserves plaintext")
    void encryptThenDecryptPreservesUnicodeCharacters(
            @ForAll("unicodeStrings") String plaintext) {
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Provide
    Arbitrary<String> unicodeStrings() {
        // Use multiple char ranges to include diverse unicode while excluding
        // unpaired surrogates (U+D800–U+DFFF) which are invalid in UTF-8/UTF-16
        return Arbitraries.strings()
                .withCharRange('\u0000', '\uD7FF')
                .withCharRange('\uE000', '\uFFFD')
                .ofMinLength(1)
                .ofMaxLength(255);
    }
}
