package com.company.workflowbuilder.service.data;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Service
public class CredentialCipherService {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipherService(@Value("${app.pipeline.encryption-key}") String secret) {
        if (secret == null || secret.length() < 16) throw new IllegalStateException("PIPELINE_ENCRYPTION_KEY must contain at least 16 characters");
        try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length); System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Cannot encrypt connector credentials", ex); }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return "{}";
        try {
            byte[] packed = Base64.getDecoder().decode(value);
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) { throw new IllegalStateException("Cannot decrypt connector credentials", ex); }
    }
}
