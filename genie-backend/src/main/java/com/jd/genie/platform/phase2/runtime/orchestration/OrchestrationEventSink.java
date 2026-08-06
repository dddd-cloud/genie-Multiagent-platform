package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;

import java.util.Map;

@FunctionalInterface
public interface OrchestrationEventSink {
    void emit(String eventType, OrchestrationStep step, AgentTaskResult result, Map<String, Object> details);
}
