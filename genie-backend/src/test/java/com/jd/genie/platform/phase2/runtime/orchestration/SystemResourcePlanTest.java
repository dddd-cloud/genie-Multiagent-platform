package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.resource.SystemResourceBuilder;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemResourcePlanTest {
    @Test
    void prependsHiddenBuilderThenPreservesWorkForTheCurrentVisibleAgent() {
        OrchestrationPlan input = new OrchestrationPlan(List.of(
                new OrchestrationStep("step-1", "current-team-agent", "继续生成软件", List.of())
        ));
        OrchestrationPlan plan = Phase2OrchestrationRuntime.enforceSystemResourceStep(
                "创建一个开发团队并且生成一个软件", input);

        assertEquals(List.of(SystemResourceBuilder.AGENT_ID, "current-team-agent"),
                plan.steps().stream().map(OrchestrationStep::agentId).toList());
        new OrchestrationPlanValidator().validate(plan, List.of(
                new AgentCapabilitySummary("current-team-agent", 1L, "当前 Team Agent", ""),
                SystemResourceBuilder.candidate()
        ));
    }
}
