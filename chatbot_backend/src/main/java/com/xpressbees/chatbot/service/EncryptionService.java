package com.xpressbees.chatbot.service;

import com.xpressbees.chatbot.exception.EncryptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provides AES-256-GCM encryption and decryption for sensitive fields.
 * Each encryption call generates a random 12-byte IV prepended to the ciphertext.
 * The combined (IV + ciphertext) is returned as a Base64-encoded string.
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public EncryptionService(@Value("${encryption.key}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "Encryption key is not configured. Set the 'encryption.key' property or ENCRYPTION_KEY environment variable.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Encryption key is not valid Base64. Ensure the 'encryption.key' property contains a valid Base64-encoded 32-byte key.", e);
        }

        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "Encryption key must be exactly 32 bytes (256 bits) when decoded. Got " + keyBytes.length + " bytes.");
        }

        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        this.secureRandom = new SecureRandom();
        log.info("EncryptionService initialized successfully with AES-256-GCM.");
    }

    /**
     * Encrypts the given plaintext using AES-256-GCM.
     * Returns Base64(IV + ciphertext) or null if input is null.
     *
     * @param plaintext the string to encrypt, or null
     * @return Base64-encoded (IV + ciphertext), or null if plaintext is null
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] ivAndCiphertext = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, ivAndCiphertext, IV_LENGTH_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(ivAndCiphertext);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts the given Base64-encoded ciphertext (IV + encrypted data) using AES-256-GCM.
     * Returns the original plaintext or null if input is null.
     *
     * @param ciphertext Base64-encoded (IV + ciphertext), or null
     * @return the decrypted plaintext, or null if ciphertext is null
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }

        try {
            byte[] ivAndCiphertext = Base64.getDecoder().decode(ciphertext);

            if (ivAndCiphertext.length < IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Ciphertext is too short to contain an IV.");
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_LENGTH_BYTES);

            byte[] encryptedData = new byte[ivAndCiphertext.length - IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, IV_LENGTH_BYTES, encryptedData, 0, encryptedData.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] plainBytes = cipher.doFinal(encryptedData);
            return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed for ApiConfig field", e);
        }
    }
}
