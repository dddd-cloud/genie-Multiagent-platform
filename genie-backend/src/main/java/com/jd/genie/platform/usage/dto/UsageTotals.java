package com.jd.genie.platform.usage.dto;

/**
 * {@code tokensAvailable} is false while the streaming path does not report token counts, so the
 * client can render "unavailable" instead of a misleading zero.
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
