package com.jd.genie.platform.phase2.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
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
        assertThrows(IllegalStateException.class, () -> new CredentialEnvelopeService(new ObjectMapper(), Base64.getEncoder().encodeToString(new byte[8])));
    }

    @Test void springContextRejectsMissingOrMalformedKeysAtBeanCreation() {
        for (String value : new String[]{null, "not-base64", Base64.getEncoder().encodeToString(new byte[31]), Base64.getEncoder().encodeToString(new byte[33])}) {
            try (var context = context(value)) {
                assertThrows(Exception.class, context::refresh);
            }
        }
    }

    @Test void springContextAcceptsExactly32Bytes() {
        try (var context = context(key)) {
            context.refresh();
            assertEquals(CredentialEnvelopeService.class, context.getBean(CredentialEnvelopeService.class).getClass());
        }
    }

    private AnnotationConfigApplicationContext context(String value) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("GENIE_MCP_CREDENTIAL_KEY", value == null ? "" : value)));
        Supplier<ObjectMapper> mapperSupplier = ObjectMapper::new;
        context.registerBean(ObjectMapper.class, mapperSupplier);
        context.registerBean(CredentialEnvelopeService.class);
        return context;
    }
}
