package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.summary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SingleFinalResponseTest {

    @Test
    void retryAndSummaryTogetherStillProduceExactlyOneFinalResponse() {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicInteger executions = new AtomicInteger();
        doAnswer(invocation -> executions.incrementAndGet() == 1
                ? AgentTaskResult.failure("TOOL_TIMEOUT", true)
                : AgentTaskResult.success("recovered result")
        ).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        AgentStage6TestSupport.RecordingModel model = new AgentStage6TestSupport.RecordingModel(
                new OrchestrationPlan(List.of(singleStep("step-1", "agent-a", "flaky task"))),
                OrchestrationModelPort.ReviewDecision.RETRY,
                OrchestrationModelPort.ReviewDecision.COMPLETE
        );
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                model,
                new OrchestrationPlanValidator(),
                new SerialOrchestrationService(catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, model),
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
                List.of(summary("agent-a")),
                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST"),
                observer
        );

        assertEquals(2, executions.get());
        assertEquals(1, AgentStage6TestSupport.finishedCount(channel.events()));
        assertEquals("SUCCESS", AgentStage6TestSupport.finalEvent(channel.events()).get("completionStatus"));
        assertEquals(1, channel.completionCount());
        assertTrue(observer.state() == com.jd.genie.platform.agentbridge.ConversationStreamObserver.TerminalState.COMPLETED);
        // The single terminal event is the FINAL_RESPONSE.
        assertEquals("FINAL_RESPONSE", AgentStage6TestSupport.finalEvent(channel.events()).get("eventType"));
    }
}
