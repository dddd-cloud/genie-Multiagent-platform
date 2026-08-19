package com.jd.genie.platform.usage.mapper;

public class UsageUserAggregateRow {
    private String userId;
    private String username;
    private String displayName;
    private long calls;
    private long completedCalls;
    private long failedCalls;
    private long totalDurationMs;
    private long totalTokens;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public long getCalls() { return calls; }
    public void setCalls(long calls) { this.calls = calls; }
    public long getCompletedCalls() { return completedCalls; }
    public void setCompletedCalls(long completedCalls) { this.completedCalls = completedCalls; }
    public long getFailedCalls() { return failedCalls; }
    public void setFailedCalls(long failedCalls) { this.failedCalls = failedCalls; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
}
