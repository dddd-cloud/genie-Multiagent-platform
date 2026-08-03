package com.jd.genie.platform.phase2contract;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ErrorHttpStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase2ErrorCodeContractTest {

    private static final List<String> PHASE1_CODES = List.of(
        "VALIDATION_ERROR",
        "AUTH_REQUIRED",
        "AUTH_INVALID_CREDENTIALS",
        "INTERNAL_TOKEN_INVALID",
        "ACCESS_DENIED",
        "CSRF_INVALID",
        "RESOURCE_NOT_FOUND",
        "USER_ALREADY_EXISTS",
        "CONVERSATION_BUSY",
        "DUPLICATE_REQUEST",
        "MESSAGE_STATE_CONFLICT",
        "SNAPSHOT_TOO_LARGE",
        "AGENT_DOWNSTREAM_ERROR",
        "AGENT_NO_FINAL_EVENT",
        "INTERNAL_ERROR",
        "DATABASE_UNAVAILABLE",
        "CLIENT_DISCONNECTED",
        "SERVICE_RESTARTED",
        "AGENT_STREAM_INTERRUPTED",
        "SNAPSHOT_INVALID"
    );

    private static final List<String> PHASE2_CODES = List.of(
        "VERSION_CONFLICT",
        "AGENT_INVALID_STATE",
        "AGENT_OFFLINE",
        "AGENT_MUST_BE_OFFLINE",
        "SKILL_IN_USE",
        "MODEL_NOT_AVAILABLE",
        "PROMPT_INVALID",
        "TOOL_BINDING_INVALID",
        "MCP_URL_REJECTED",
        "MCP_AUTH_INVALID",
        "MCP_UNAVAILABLE",
        "MCP_DISCOVERY_INVALID",
        "TOOL_NOT_BOUND",
        "TOOL_INVALID_INPUT",
        "TOOL_TIMEOUT",
        "TOOL_INVALID_RESPONSE",
        "LOCAL_CONTEXT_INVALID",
        "LOCAL_CONTEXT_TOO_LARGE",
        "NO_SUITABLE_AGENT",
        "ORCHESTRATION_PLAN_INVALID",
        "AGENT_INVALID_RESULT",
        "CONTEXT_BUDGET_EXCEEDED",
        "MEMORY_ANALYSIS_FAILED",
        "SUMMARY_FAILED"
    );

    @Test
    void phase1CodesRemainPrefixInOrder() {
        String[] actual = Arrays.stream(MvpErrorCode.values()).map(Enum::name).toArray(String[]::new);
        for (int i = 0; i < PHASE1_CODES.size(); i++) {
            assertEquals(PHASE1_CODES.get(i), actual[i], "phase1 order broken at index " + i);
        }
    }

    @Test
    void phase2CodesAppendedAtEnd() {
        String[] actual = Arrays.stream(MvpErrorCode.values()).map(Enum::name).toArray(String[]::new);
        List<String> expected = new java.util.ArrayList<>(PHASE1_CODES);
        expected.addAll(PHASE2_CODES);
        assertEquals(expected, Arrays.asList(actual));
    }

    @Test
    void everyErrorCodeHasUniqueHttpStatusMapping() {
        Set<MvpErrorCode> seen = new HashSet<>();
        for (MvpErrorCode code : MvpErrorCode.values()) {
            assertTrue(Phase2ErrorHttpStatus.isMapped(code), "unmapped " + code);
            int status = Phase2ErrorHttpStatus.httpStatus(code);
            assertTrue(status >= 400 && status < 600 || status == 499, "invalid status for " + code);
            seen.add(code);
        }
        assertEquals(MvpErrorCode.values().length, seen.size());
    }

    @Test
    void phase2CodesHaveExpectedStatuses() {
        assertEquals(409, Phase2ErrorHttpStatus.httpStatus(MvpErrorCode.VERSION_CONFLICT));
        assertEquals(409, Phase2ErrorHttpStatus.httpStatus(MvpErrorCode.AGENT_OFFLINE));
        assertEquals(400, Phase2ErrorHttpStatus.httpStatus(MvpErrorCode.TOOL_BINDING_INVALID));
        assertEquals(413, Phase2ErrorHttpStatus.httpStatus(MvpErrorCode.LOCAL_CONTEXT_TOO_LARGE));
        assertEquals(502, Phase2ErrorHttpStatus.httpStatus(MvpErrorCode.MCP_UNAVAILABLE));
        assertEquals(504, Phase2ErrorHttpStatus.httpStatus(MvpErrorCode.TOOL_TIMEOUT));
        assertEquals(502, Phase2ErrorHttpStatus.httpStatus(MvpErrorCode.SUMMARY_FAILED));
    }
}
