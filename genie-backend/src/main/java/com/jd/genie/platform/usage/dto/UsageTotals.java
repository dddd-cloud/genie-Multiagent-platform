package com.jd.genie.platform.usage.dto;

/**
 * {@code tokensAvailable} is false when recorded calls exist but none reported token counts.
 */
public record UsageTotals(
    long calls,
    long completedCalls,
    long failedCalls,
    long interruptedCalls,
    long totalDurationMs,
    long promptTokens,
    long completionTokens,
    long totalTokens,
    boolean tokensAvailable
) {
}
