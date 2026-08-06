package com.jd.genie.platform.phase2.runtime.agent;

import java.util.List;

public record AgentTestResponse(
        String model,
        List<String> skillSummary,
        List<String> capabilityKeys,
        AgentTaskResult result,
        long elapsedMillis,
        int progressEventCount
) {
    public AgentTestResponse {
        skillSummary = skillSummary == null ? List.of() : List.copyOf(skillSummary);
        capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
    }
}
