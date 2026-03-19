package com.erp.entitybuilder.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PiiCrypto {

    private static final int IV_BYTES = 12; // recommended for GCM
    private static final int TAG_BITS = 128;

    private final byte[] keyBytes;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public PiiCrypto(@Value("${entitybuilder.pii.key}") String keyMaterial) {
        // Derive a fixed-length AES-256 key from arbitrary keyMaterial.
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            this.keyBytes = sha.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive PII encryption key", e);
        }
        this.keyId = "default";
    }

    public EncryptedValue encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // payload = iv || ciphertext
            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);

            return new EncryptedValue(keyId, Base64.getEncoder().encodeToString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("PII encryption failed", e);
        }
    }

    public String decrypt(String encryptedBase64) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedBase64);
            if (payload.length <= IV_BYTES) throw new IllegalArgumentException("Invalid encrypted payload");
            byte[] iv = new byte[IV_BYTES];
            byte[] cipherText = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII decryption failed", e);
        }
    }

    public record EncryptedValue(String keyId, String encryptedValueBase64) {}
}

