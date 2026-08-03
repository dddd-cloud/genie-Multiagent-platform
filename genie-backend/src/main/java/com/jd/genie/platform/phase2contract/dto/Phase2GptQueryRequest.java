package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.phase2contract.enums.ExecutionMode;

import java.util.List;

public record Phase2GptQueryRequest(
    String sessionId,
    String requestId,
    String query,
    ExecutionMode executionMode,
    Integer deepThink,
    String outputStyle,
    List<String> allowedAgentIds,
    Phase2LocalContext localContext
) {
    public Phase2GptQueryRequest {
        allowedAgentIds = allowedAgentIds == null
            ? null
            : List.copyOf(allowedAgentIds);
    }
}
