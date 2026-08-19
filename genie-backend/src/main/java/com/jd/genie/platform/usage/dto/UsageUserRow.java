package com.jd.genie.platform.usage.dto;

public record UsageUserRow(
    String userId,
    String username,
    String displayName,
    long calls,
    long completedCalls,
    long failedCalls,
    long totalDurationMs,
    long totalTokens
) {
}
