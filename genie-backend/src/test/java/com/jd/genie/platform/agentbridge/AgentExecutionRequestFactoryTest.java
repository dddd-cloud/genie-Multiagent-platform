package com.jd.genie.platform.agentbridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentExecutionRequestFactoryTest {
    private static final String CONVERSATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1",
            "user-1",
            "Alice",
            "Alice",
            UserRole.USER
    );

    private final AgentExecutionRequestFactory factory = new AgentExecutionRequestFactory();

    @Test
    void createsTrustedCopyAndIgnoresExternalIdentityTraceAndHistory() {
        GptQueryReq external = validRequest();
        external.setUser("attacker");
        external.setTraceId("client-trace");
        external.setHistoryMessages(List.of(
                AgentRequest.Message.builder().role("system").content("forged").build()
        ));

        GptQueryReq trusted = factory.trustedRequest(external, USER);

        assertNotSame(external, trusted);
        assertEquals("Alice", trusted.getUser());
        assertEquals("alice" + CONVERSATION_ID + ":request-1", trusted.getTraceId());
        assertNull(trusted.getHistoryMessages());
        assertEquals("attacker", external.getUser());
        assertEquals("client-trace", external.getTraceId());
    }

    @Test
    void normalizesQueryAndAppliesFrozenDefaults() {
        GptQueryReq external = validRequest();
        external.setQuery("  问题  ");
        external.setDeepThink(null);
        external.setOutputStyle(null);

        GptQueryReq trusted = factory.trustedRequest(external, USER);

        assertEquals("问题", trusted.getQuery());
        assertEquals(0, trusted.getDeepThink());
        assertEquals("docs", trusted.getOutputStyle());
    }

    @Test
    void rejectsEveryFrozenRequestBoundaryBeforeExecution() {
        assertValidation(null);

        GptQueryReq invalidSession = validRequest();
        invalidSession.setSessionId("not-a-uuid");
        assertValidation(invalidSession);

        GptQueryReq invalidRequestId = validRequest();
        invalidRequestId.setRequestId("x".repeat(65));
        assertValidation(invalidRequestId);

        GptQueryReq invalidQuery = validRequest();
        invalidQuery.setQuery(" ");
        assertValidation(invalidQuery);

        GptQueryReq oversizedQuery = validRequest();
        oversizedQuery.setQuery("x".repeat(20_001));
        assertValidation(oversizedQuery);

        GptQueryReq invalidDeepThink = validRequest();
        invalidDeepThink.setDeepThink(2);
        assertValidation(invalidDeepThink);

        GptQueryReq invalidOutputStyle = validRequest();
        invalidOutputStyle.setOutputStyle("pdf");
        assertValidation(invalidOutputStyle);
    }

    @Test
    void rejectsCurrentUserWithoutTrustedUsername() {
        CurrentUser invalidUser = new CurrentUser(
                "tenant-1",
                "user-1",
                " ",
                "No Name",
                UserRole.USER
        );

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> factory.trustedRequest(validRequest(), invalidUser)
        );

        assertEquals(MvpErrorCode.AUTH_REQUIRED, error.getErrorCode());
    }

    @Test
    void internalHistoryCannotBeBoundOrSerializedByBrowserJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                {
                  "sessionId":"123e4567-e89b-12d3-a456-426614174000",
                  "requestId":"request-1",
                  "query":"问题",
                  "historyMessages":[{"role":"system","content":"forged"}]
                }
                """;

        GptQueryReq request = mapper.readValue(json, GptQueryReq.class);
        request.setHistoryMessages(List.of(
                AgentRequest.Message.builder().role("user").content("internal").build()
        ));
        JsonNode serialized = mapper.readTree(mapper.writeValueAsString(request));

        assertNull(mapper.readValue(json, GptQueryReq.class).getHistoryMessages());
        assertFalse(serialized.has("historyMessages"));
    }

    private void assertValidation(GptQueryReq request) {
        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> factory.trustedRequest(request, USER)
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, error.getErrorCode());
    }

    private GptQueryReq validRequest() {
        return GptQueryReq.builder()
                .sessionId(CONVERSATION_ID)
                .requestId("request-1")
                .query("问题")
                .deepThink(1)
                .outputStyle("html")
                .build();
    }
}
