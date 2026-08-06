package com.jd.genie.platform.phase2.runtime.plan;

import java.util.List;

public record OrchestrationStep(String stepId, String agentId, String objective, List<String> inputRefs) {
}
