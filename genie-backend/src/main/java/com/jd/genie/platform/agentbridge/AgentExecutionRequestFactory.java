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
    private static final int MAX_QUERY_CODE_POINTS = 20_000;

    public GptQueryReq trustedRequest(GptQueryReq externalRequest, CurrentUser currentUser) {
        if (externalRequest == null) {
            throw validationError("request must not be null");
        }

        String sessionId = normalizedSessionId(externalRequest.getSessionId());
        String requestId = normalizedRequestId(externalRequest.getRequestId());
        String query = normalizedQuery(externalRequest.getQuery());
        String outputStyle = normalizedOutputStyle(externalRequest.getOutputStyle());
        Integer deepThink = normalizedDeepThink(externalRequest.getDeepThink());

        if (currentUser == null || !hasText(currentUser.username())) {
            throw new AgentBridgeException(
                    MvpErrorCode.AUTH_REQUIRED,
                    "Current user has no username"
            );
        }

        GptQueryReq trusted = GptQueryReq.builder()
                .sessionId(sessionId)
                .requestId(requestId)
                .query(query)
                .deepThink(deepThink)
                .outputStyle(outputStyle)
                .user(currentUser.username())
                .build();
        trusted.setTraceId(ChateiUtils.getRequestId(trusted));
        return trusted;
    }

    private String normalizedSessionId(String value) {
        String trimmed = trim(value);
        if (!hasText(trimmed)) {
            throw validationError("sessionId must be a UUID");
        }
        try {
            UUID uuid = UUID.fromString(trimmed);
            if (!uuid.toString().equalsIgnoreCase(trimmed)) {
                throw new IllegalArgumentException();
            }
            return uuid.toString();
        } catch (IllegalArgumentException ignored) {
            throw validationError("sessionId must be a UUID");
        }
    }

    private String normalizedRequestId(String value) {
        String trimmed = trim(value);
        if (!hasText(trimmed) || trimmed.length() > MAX_REQUEST_ID_LENGTH) {
            throw validationError("requestId must contain 1 to 64 characters after trim");
        }
        return trimmed;
    }

    private String normalizedQuery(String value) {
        String trimmed = trim(value);
        if (!hasText(trimmed) || codePointLength(trimmed) > MAX_QUERY_CODE_POINTS) {
            throw validationError("query must contain 1 to 20000 Unicode code points after trim");
        }
        return trimmed;
    }

    private String normalizedOutputStyle(String value) {
        String trimmed = trim(value);
        String outputStyle = hasText(trimmed) ? trimmed : DEFAULT_OUTPUT_STYLE;
        if (!OUTPUT_STYLES.contains(outputStyle)) {
            throw validationError("outputStyle is not supported");
        }
        return outputStyle;
    }

    private Integer normalizedDeepThink(Integer value) {
        Integer deepThink = value == null ? 0 : value;
        if (deepThink != 0 && deepThink != 1) {
            throw validationError("deepThink must be 0 or 1");
        }
        return deepThink;
    }

    private AgentBridgeException validationError(String message) {
        return new AgentBridgeException(MvpErrorCode.VALIDATION_ERROR, message);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
