package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CredentialEnvelopeService {
    private final ObjectMapper objectMapper;
    private final String configuredKey;
    private final byte[] validatedKey;
    private final SecureRandom random = new SecureRandom();

    public CredentialEnvelopeService(ObjectMapper objectMapper,
                                     @Value("${GENIE_MCP_CREDENTIAL_KEY:}") String configuredKey) {
        this.objectMapper = objectMapper;
        this.configuredKey = configuredKey;
        this.validatedKey = decodeKey(configuredKey);
    }

    public String encrypt(String plaintext, String tenantId, String ownerId, String serverId, AuthType authType) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        try {
            byte[] key = key();
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(tenantId, ownerId, serverId, authType));
            byte[] combined = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            int tagLength = 16;
            Envelope envelope = new Envelope(1, "AES-256-GCM", "primary", Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(combined, combined.length - tagLength)),
                Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(combined, combined.length - tagLength, combined.length)));
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) { throw invalid(); }
    }

    public String decrypt(String envelopeJson, String tenantId, String ownerId, String serverId, AuthType authType) {
        if (envelopeJson == null || envelopeJson.isBlank()) return null;
        try {
            Envelope e = objectMapper.readValue(envelopeJson, Envelope.class);
            if (e.version != 1 || !"AES-256-GCM".equals(e.algorithm)) throw invalid();
            byte[] iv = Base64.getDecoder().decode(e.iv); byte[] cipherText = Base64.getDecoder().decode(e.ciphertext);
            byte[] tag = Base64.getDecoder().decode(e.tag); byte[] combined = new byte[cipherText.length + tag.length];
            System.arraycopy(cipherText, 0, combined, 0, cipherText.length); System.arraycopy(tag, 0, combined, cipherText.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(tenantId, ownerId, serverId, authType));
            return new String(cipher.doFinal(combined), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw invalid(); }
    }

    private byte[] key() {
        return validatedKey.clone();
    }
    private byte[] decodeKey(String raw) {
        try {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException();
            byte[] key = Base64.getDecoder().decode(raw.trim());
            if (key.length != 32) throw new IllegalArgumentException();
            return key;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("GENIE_MCP_CREDENTIAL_KEY must be a valid Base64-encoded 32-byte key");
        }
    }
    private byte[] aad(String tenantId, String ownerId, String serverId, AuthType authType) {
        return (tenantId + "|" + ownerId + "|" + serverId + "|" + authType.name()).getBytes(StandardCharsets.UTF_8);
    }
    private Phase2ContractException invalid() { return new Phase2ContractException(MvpErrorCode.MCP_AUTH_INVALID, "MCP authentication is invalid"); }
    private record Envelope(int version, String algorithm, String keyId, String iv, String ciphertext, String tag) { }
}
