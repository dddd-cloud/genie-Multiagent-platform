package com.jd.genie.platform.phase2contract.dto;

public record AgentCapabilitySummary(
    String agentId,
    long agentVersion,
    String name,
    String description
) {
}
