package com.jd.genie.platform.usage.mapper;

public class UsageDailyRow {
    private String day;
    private long calls;
    private long completedCalls;
    private long failedCalls;
    private long totalTokens;

    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }
    public long getCalls() { return calls; }
    public void setCalls(long calls) { this.calls = calls; }
    public long getCompletedCalls() { return completedCalls; }
    public void setCompletedCalls(long completedCalls) { this.completedCalls = completedCalls; }
    public long getFailedCalls() { return failedCalls; }
    public void setFailedCalls(long failedCalls) { this.failedCalls = failedCalls; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
}
