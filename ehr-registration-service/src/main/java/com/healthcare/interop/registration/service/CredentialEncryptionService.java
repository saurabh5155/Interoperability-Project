package com.healthcare.interop.registration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * AES-256-GCM encryption for EHR auth credentials stored in DB.
 */
@Service
@Slf4j
public class CredentialEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${security.aes-encryption-key}")
    private String encryptionKey;

    private final SecureRandom random = new SecureRandom();

    public Map<String, String> encrypt(Map<String, String> config) {
        if (config == null) return new HashMap<>();
        Map<String, String> encrypted = new HashMap<>();
        config.forEach((k, v) -> {
            if (v != null && isSensitiveKey(k)) {
                encrypted.put(k, encryptValue(v));
            } else {
                encrypted.put(k, v);
            }
        });
        return encrypted;
    }

    public Map<String, String> decrypt(Map<String, String> config) {
        if (config == null) return new HashMap<>();
        Map<String, String> decrypted = new HashMap<>();
        config.forEach((k, v) -> {
            if (v != null && isSensitiveKey(k) && v.startsWith("ENC:")) {
                decrypted.put(k, decryptValue(v.substring(4)));
            } else {
                decrypted.put(k, v);
            }
        });
        return decrypted;
    }

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("token") || lower.contains("secret")
            || lower.contains("password") || lower.contains("key");
    }

    private String encryptValue(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                Base64.getDecoder().decode(encryptionKey), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return "ENC:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    private String decryptValue(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                Base64.getDecoder().decode(encryptionKey), "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
