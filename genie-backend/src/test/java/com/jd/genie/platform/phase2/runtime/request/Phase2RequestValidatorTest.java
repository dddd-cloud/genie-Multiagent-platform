package com.jd.genie.platform.phase2.runtime.request;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Phase2RequestValidatorTest {
    private static final CurrentUser USER = new CurrentUser("tenant", "user", "alice", "Alice", UserRole.USER);
    private final Phase2GptQueryRequestValidator validator = new Phase2GptQueryRequestValidator();

    @Test
    void acceptsFrozenBoundaryValuesUsingUnicodeCodePoints() {
        Phase2GptQueryRequest request = request("AUTO", 0, List.of("agent-1"));
        request.setQuery("😀".repeat(20_000));
        request.getLocalContext().setLongTermMemory("😀".repeat(6_000));
        request.getLocalContext().setConversationSummary("😀".repeat(10_000));

        Phase2GptQueryRequestValidator.ValidatedPhase2Request actual = validator.validate(request, USER);

        assertEquals("AUTO", actual.executionMode());
        assertEquals(20_000, actual.trustedRequest().getQuery().codePointCount(0, actual.trustedRequest().getQuery().length()));
        assertEquals(List.of("agent-1"), actual.allowedAgentIds());
        assertEquals(List.of(), actual.attachmentIds());
    }

    @Test
    void acceptsUpToTenAttachmentIdsAndRejectsInvalidOnes() {
        Phase2GptQueryRequest request = request("AUTO", 0, List.of());
        request.setAttachmentIds(java.util.Collections.nCopies(10, "123e4567-e89b-12d3-a456-426614174000")
            .stream()
            .map(id -> java.util.UUID.randomUUID().toString())
            .toList());
        assertEquals(10, validator.validate(request, USER).attachmentIds().size());

        Phase2GptQueryRequest tooMany = request("AUTO", 0, List.of());
        tooMany.setAttachmentIds(java.util.Collections.nCopies(11, "123e4567-e89b-12d3-a456-426614174000"));
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(tooMany, USER));

        Phase2GptQueryRequest bad = request("AUTO", 0, List.of());
        bad.setAttachmentIds(List.of("not-a-uuid"));
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(bad, USER));
    }

    @Test
    void rejectsInvalidFrozenRequestFields() {
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(request("UNKNOWN", 0, List.of()), USER));
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(request("AUTO", 2, List.of()), USER));
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(request("DIRECT", 0, List.of("agent-1")), USER));
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(request("AUTO", 0, List.of("agent-1", "agent-1")), USER));
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(request("AUTO", 0, List.of(" ")), USER));
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(request("AUTO", 0, java.util.Collections.nCopies(21, "agent")), USER));

        Phase2GptQueryRequest invalidSession = request("AUTO", 0, List.of());
        invalidSession.setSessionId("not-a-uuid");
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(invalidSession, USER));
    }

    @Test
    void rejectsOversizedOrMalformedLocalContextBeforeItCanReachRuntime() {
        Phase2GptQueryRequest invalidSchema = request("AUTO", 0, List.of());
        invalidSchema.getLocalContext().setSchemaVersion(2);
        assertError(MvpErrorCode.LOCAL_CONTEXT_INVALID, () -> validator.validate(invalidSchema, USER));

        Phase2GptQueryRequest oversized = request("AUTO", 0, List.of());
        oversized.getLocalContext().setLongTermMemory("x".repeat(12_001));
        assertError(MvpErrorCode.LOCAL_CONTEXT_TOO_LARGE, () -> validator.validate(oversized, USER));

        Phase2GptQueryRequest oversizedQuery = request("AUTO", 0, List.of());
        oversizedQuery.setQuery("😀".repeat(20_001));
        assertError(MvpErrorCode.VALIDATION_ERROR, () -> validator.validate(oversizedQuery, USER));
    }

    private Phase2GptQueryRequest request(String mode, int deepThink, List<String> allowedAgentIds) {
        return Phase2GptQueryRequest.builder()
                .sessionId("123e4567-e89b-12d3-a456-426614174000")
                .requestId("request-1")
                .query("question")
                .executionMode(mode)
                .deepThink(deepThink)
                .outputStyle("docs")
                .allowedAgentIds(allowedAgentIds)
                .localContext(Phase2GptQueryRequest.LocalContext.builder()
                        .schemaVersion(1).longTermMemory("").conversationSummary("").build())
                .build();
    }

    private void assertError(MvpErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(AgentBridgeException.class, action::run).getErrorCode());
    }
}
