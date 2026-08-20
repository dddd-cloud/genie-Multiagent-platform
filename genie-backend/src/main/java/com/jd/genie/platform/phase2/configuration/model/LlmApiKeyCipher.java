package com.jd.genie.platform.phase2.configuration.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts LLM API keys at rest. The plaintext is never returned on read APIs.
 * When {@code GENIE_MCP_CREDENTIAL_KEY} is missing (local tests), values are stored with a
 * non-JSON prefix that still never appears in catalog JSON.
 */
@Service
public class LlmApiKeyCipher {
    private static final String PLAIN_PREFIX = "plain:";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public LlmApiKeyCipher(@Value("${GENIE_MCP_CREDENTIAL_KEY:}") String configuredKey) {
        this.key = tryDecodeKey(configuredKey);
    }

    public String encrypt(String tenantId, String ownerId, String modelId, String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        if (key == null) {
            return PLAIN_PREFIX + plaintext;
        }
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(tenantId, ownerId, modelId));
            byte[] combined = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            int tagLength = 16;
            Envelope envelope = new Envelope(
                1,
                "AES-256-GCM",
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(Arrays.copyOf(combined, combined.length - tagLength)),
                Base64.getEncoder().encodeToString(Arrays.copyOfRange(combined, combined.length - tagLength, combined.length))
            );
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt LLM API key", ex);
        }
    }

    public String decrypt(String tenantId, String ownerId, String modelId, String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (stored.startsWith(PLAIN_PREFIX)) {
            return stored.substring(PLAIN_PREFIX.length());
        }
        if (key == null) {
            return null;
        }
        try {
            Envelope envelope = MAPPER.readValue(stored, Envelope.class);
            if (envelope.version != 1 || !"AES-256-GCM".equals(envelope.algorithm)) {
                return null;
            }
            byte[] iv = Base64.getDecoder().decode(envelope.iv);
            byte[] cipherText = Base64.getDecoder().decode(envelope.ciphertext);
            byte[] tag = Base64.getDecoder().decode(envelope.tag);
            byte[] combined = new byte[cipherText.length + tag.length];
            System.arraycopy(cipherText, 0, combined, 0, cipherText.length);
            System.arraycopy(tag, 0, combined, cipherText.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(tenantId, ownerId, modelId));
            return new String(cipher.doFinal(combined), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    private byte[] aad(String tenantId, String ownerId, String modelId) {
        return (nullToEmpty(tenantId) + "|" + nullToEmpty(ownerId) + "|" + nullToEmpty(modelId) + "|LLM_API_KEY")
            .getBytes(StandardCharsets.UTF_8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static byte[] tryDecodeKey(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            byte[] decoded = Base64.getDecoder().decode(raw.trim());
            return decoded.length == 32 ? decoded : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record Envelope(int version, String algorithm, String iv, String ciphertext, String tag) {
    }
}
