package com.jd.genie.platform.phase2.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialEnvelopeServiceTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);
    @Test void encryptsAndDecryptsWithAad() {
        var service = new CredentialEnvelopeService(new ObjectMapper(), key);
        String envelope = service.encrypt("secret", "tenant", "owner", "server", AuthType.BEARER_TOKEN);
        assertEquals("secret", service.decrypt(envelope, "tenant", "owner", "server", AuthType.BEARER_TOKEN));
        assertThrows(Phase2ContractException.class, () -> service.decrypt(envelope, "other", "owner", "server", AuthType.BEARER_TOKEN));
    }
    @Test void rejectsInvalidKey() {
        var service = new CredentialEnvelopeService(new ObjectMapper(), Base64.getEncoder().encodeToString(new byte[8]));
        Phase2ContractException ex = assertThrows(Phase2ContractException.class, () -> service.encrypt("secret", "t", "o", "s", AuthType.NONE));
        assertEquals(MvpErrorCode.MCP_AUTH_INVALID, ex.errorCode());
    }
}
