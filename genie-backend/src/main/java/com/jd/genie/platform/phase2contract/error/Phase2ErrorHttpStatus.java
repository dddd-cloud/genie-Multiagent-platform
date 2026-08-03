package com.jd.genie.platform.phase2contract.error;

import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Unique HTTP status mapping for Phase2 (and shared MVP) error codes.
 * A/B/C handlers must call this mapper; do not copy the switch.
 */
public final class Phase2ErrorHttpStatus {

    private static final int CLIENT_CLOSED_REQUEST = 499;

    private static final Map<MvpErrorCode, Integer> STATUS_BY_CODE = buildStatusMap();

    private Phase2ErrorHttpStatus() {
    }

    public static int httpStatus(MvpErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "errorCode");
        Integer status = STATUS_BY_CODE.get(errorCode);
        if (status == null) {
            throw new IllegalStateException("No HTTP status mapping for " + errorCode);
        }
        return status;
    }

    public static boolean isMapped(MvpErrorCode errorCode) {
        return errorCode != null && STATUS_BY_CODE.containsKey(errorCode);
    }

    private static Map<MvpErrorCode, Integer> buildStatusMap() {
        Map<MvpErrorCode, Integer> map = new EnumMap<>(MvpErrorCode.class);

        // 400
        map.put(MvpErrorCode.VALIDATION_ERROR, 400);
        map.put(MvpErrorCode.PROMPT_INVALID, 400);
        map.put(MvpErrorCode.TOOL_BINDING_INVALID, 400);
        map.put(MvpErrorCode.MCP_URL_REJECTED, 400);
        map.put(MvpErrorCode.MCP_DISCOVERY_INVALID, 400);
        map.put(MvpErrorCode.TOOL_INVALID_INPUT, 400);
        map.put(MvpErrorCode.LOCAL_CONTEXT_INVALID, 400);
        map.put(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, 400);
        map.put(MvpErrorCode.AGENT_INVALID_RESULT, 400);
        map.put(MvpErrorCode.SNAPSHOT_INVALID, 400);

        // 401
        map.put(MvpErrorCode.AUTH_REQUIRED, 401);
        map.put(MvpErrorCode.AUTH_INVALID_CREDENTIALS, 401);

        // 403
        map.put(MvpErrorCode.ACCESS_DENIED, 403);
        map.put(MvpErrorCode.CSRF_INVALID, 403);
        map.put(MvpErrorCode.INTERNAL_TOKEN_INVALID, 403);

        // 404
        map.put(MvpErrorCode.RESOURCE_NOT_FOUND, 404);

        // 409
        map.put(MvpErrorCode.VERSION_CONFLICT, 409);
        map.put(MvpErrorCode.AGENT_INVALID_STATE, 409);
        map.put(MvpErrorCode.AGENT_OFFLINE, 409);
        map.put(MvpErrorCode.AGENT_MUST_BE_OFFLINE, 409);
        map.put(MvpErrorCode.SKILL_IN_USE, 409);
        map.put(MvpErrorCode.MODEL_NOT_AVAILABLE, 409);
        map.put(MvpErrorCode.TOOL_NOT_BOUND, 409);
        map.put(MvpErrorCode.NO_SUITABLE_AGENT, 409);
        map.put(MvpErrorCode.USER_ALREADY_EXISTS, 409);
        map.put(MvpErrorCode.CONVERSATION_BUSY, 409);
        map.put(MvpErrorCode.DUPLICATE_REQUEST, 409);
        map.put(MvpErrorCode.MESSAGE_STATE_CONFLICT, 409);

        // 413
        map.put(MvpErrorCode.SNAPSHOT_TOO_LARGE, 413);
        map.put(MvpErrorCode.LOCAL_CONTEXT_TOO_LARGE, 413);
        map.put(MvpErrorCode.CONTEXT_BUDGET_EXCEEDED, 413);

        // 499 / connection interrupt
        map.put(MvpErrorCode.CLIENT_DISCONNECTED, CLIENT_CLOSED_REQUEST);
        map.put(MvpErrorCode.SERVICE_RESTARTED, CLIENT_CLOSED_REQUEST);

        // 502
        map.put(MvpErrorCode.MCP_AUTH_INVALID, 502);
        map.put(MvpErrorCode.MCP_UNAVAILABLE, 502);
        map.put(MvpErrorCode.TOOL_INVALID_RESPONSE, 502);
        map.put(MvpErrorCode.MEMORY_ANALYSIS_FAILED, 502);
        map.put(MvpErrorCode.SUMMARY_FAILED, 502);
        map.put(MvpErrorCode.AGENT_DOWNSTREAM_ERROR, 502);
        map.put(MvpErrorCode.AGENT_NO_FINAL_EVENT, 502);
        map.put(MvpErrorCode.AGENT_STREAM_INTERRUPTED, 502);

        // 503
        map.put(MvpErrorCode.DATABASE_UNAVAILABLE, 503);

        // 504
        map.put(MvpErrorCode.TOOL_TIMEOUT, 504);

        // 500
        map.put(MvpErrorCode.INTERNAL_ERROR, 500);

        for (MvpErrorCode code : MvpErrorCode.values()) {
            if (!map.containsKey(code)) {
                throw new IllegalStateException("Missing HTTP status mapping for " + code);
            }
        }
        return Map.copyOf(map);
    }
}
