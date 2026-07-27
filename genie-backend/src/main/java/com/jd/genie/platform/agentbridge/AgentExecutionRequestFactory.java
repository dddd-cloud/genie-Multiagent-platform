package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.util.ChateiUtils;

import java.util.Set;
import java.util.UUID;

public final class AgentExecutionRequestFactory {
    private static final Set<String> OUTPUT_STYLES = Set.of(
            "dataAgent",
            "html",
            "docs",
            "ppt",
            "table"
    );
    private static final String DEFAULT_OUTPUT_STYLE = "docs";
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_QUERY_LENGTH = 20_000;

    public GptQueryReq trustedRequest(GptQueryReq externalRequest, CurrentUser currentUser) {
        if (externalRequest == null) {
            throw validationError("request must not be null");
        }
        if (!isUuid(externalRequest.getSessionId())) {
            throw validationError("sessionId must be a UUID");
        }
        if (!hasText(externalRequest.getRequestId())
                || externalRequest.getRequestId().length() > MAX_REQUEST_ID_LENGTH) {
            throw validationError("requestId must contain 1 to 64 characters");
        }

        String query = externalRequest.getQuery() == null
                ? null
                : externalRequest.getQuery().trim();
        if (!hasText(query) || query.length() > MAX_QUERY_LENGTH) {
            throw validationError("query must contain 1 to 20000 characters after trim");
        }

        Integer deepThink = externalRequest.getDeepThink() == null
                ? 0
                : externalRequest.getDeepThink();
        if (deepThink != 0 && deepThink != 1) {
            throw validationError("deepThink must be 0 or 1");
        }

        String outputStyle = hasText(externalRequest.getOutputStyle())
                ? externalRequest.getOutputStyle()
                : DEFAULT_OUTPUT_STYLE;
        if (!OUTPUT_STYLES.contains(outputStyle)) {
            throw validationError("outputStyle is not supported");
        }
        if (currentUser == null || !hasText(currentUser.username())) {
            throw new AgentBridgeException(
                    MvpErrorCode.AUTH_REQUIRED,
                    "Current user has no username"
            );
        }

        GptQueryReq trusted = GptQueryReq.builder()
                .sessionId(externalRequest.getSessionId())
                .requestId(externalRequest.getRequestId())
                .query(query)
                .deepThink(deepThink)
                .outputStyle(outputStyle)
                .user(currentUser.username())
                .build();
        trusted.setTraceId(ChateiUtils.getRequestId(trusted));
        return trusted;
    }

    private AgentBridgeException validationError(String message) {
        return new AgentBridgeException(MvpErrorCode.VALIDATION_ERROR, message);
    }

    private boolean isUuid(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
