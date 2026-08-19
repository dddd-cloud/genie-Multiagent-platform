package com.jd.genie.platform.usage.mapper;

/** Mutable row so MyBatis can populate aggregate columns by setter; converted to a DTO in the service. */
public class UsageTotalsRow {
    private long calls;
    private long completedCalls;
    private long failedCalls;
    private long interruptedCalls;
    private long totalDurationMs;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;

    public long getCalls() { return calls; }
    public void setCalls(long calls) { this.calls = calls; }
    public long getCompletedCalls() { return completedCalls; }
    public void setCompletedCalls(long completedCalls) { this.completedCalls = completedCalls; }
    public long getFailedCalls() { return failedCalls; }
    public void setFailedCalls(long failedCalls) { this.failedCalls = failedCalls; }
    public long getInterruptedCalls() { return interruptedCalls; }
    public void setInterruptedCalls(long interruptedCalls) { this.interruptedCalls = interruptedCalls; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }
    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
}
