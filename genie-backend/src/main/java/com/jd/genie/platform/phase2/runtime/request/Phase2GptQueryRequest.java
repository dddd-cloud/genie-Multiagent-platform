package com.jd.genie.platform.phase2.runtime.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Phase2GptQueryRequest {
    private String sessionId;
    private String requestId;
    private String query;
    private String executionMode;
    private Integer deepThink;
    private String outputStyle;
    private List<String> allowedAgentIds;
    private String teamId;
    private LocalContext localContext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalContext {
        private Integer schemaVersion;
        private String longTermMemory;
        private String conversationSummary;
    }
}
