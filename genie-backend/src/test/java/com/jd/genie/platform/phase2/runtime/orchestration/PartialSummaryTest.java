package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.summary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class PartialSummaryTest {

    @Test
    void failedStepSkipsTheRestAndProducesOneHonestPartialFinalResponse() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        CopyOnWriteArrayList<String> executed = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            executed.add(context.getRequestId());
            return "step-1".equals(context.getRequestId())
                    ? AgentTaskResult.failure("AGENT_INVALID_RESULT", false)
                    : AgentTaskResult.success("step-2 result");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        OrchestrationStep step1 = singleStep("step-1", "agent-a", "broken task");
        OrchestrationStep step2 = singleStep("step-2", "agent-b", "must be skipped", List.of("step-1"));
        AgentStage6TestSupport.RecordingModel model = new AgentStage6TestSupport.RecordingModel(
                new OrchestrationPlan(List.of(step1, step2))
        );
        SerialOrchestrationService serial = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, model,
                (objective, observer, cancellableCall) -> AgentTaskResult.failure("EXECUTION_ERROR", false)
        );
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                model,
                new OrchestrationPlanValidator(),
                serial,
                new OrchestrationEventMapper()
        );
        AgentStage6TestSupport.RecordingChannel channel = AgentStage6TestSupport.channel();
        var observer = AgentStage6TestSupport.observer(channel);

        runtime.execute(
                AgentStage6TestSupport.USER,
                "request-1",
                "123e4567-e89b-12d3-a456-426614174000",
                "question",
                "summary",
                List.of(summary("agent-a"), summary("agent-b")),
                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST"),
                observer
        );

        List<String> types = AgentStage6TestSupport.eventTypes(channel.events());
        assertTrue(types.contains("STEP_FAILED"));
        assertTrue(types.contains("STEP_SKIPPED"));
        assertTrue(executed.stream().noneMatch("step-2"::equals));
        assertEquals("PARTIAL", AgentStage6TestSupport.finalEvent(channel.events()).get("completionStatus"));
        assertEquals(1, AgentStage6TestSupport.finishedCount(channel.events()));
        String finalAnswer = channel.events().stream()
                .filter(event -> event.isFinished())
                .findFirst()
                .orElseThrow()
                .getResponse();
        assertTrue(finalAnswer.contains("final answer"));
    }
}
