package com.jd.genie.platform.usage.dto;

public record UsageDailyPoint(
    String day,
    long calls,
    long completedCalls,
    long failedCalls,
    long totalTokens
) {
}
