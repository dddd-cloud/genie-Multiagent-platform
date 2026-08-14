package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.summary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TerminalRaceTest {

    @Test
    void clientDisconnectDuringExecutionConvergesToASingleInterruptedTerminal() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        CountDownLatch agentStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        doAnswer(invocation -> {
            agentStarted.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return AgentTaskResult.success("late result");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        AgentStage6TestSupport.RecordingModel model = new AgentStage6TestSupport.RecordingModel(
                new OrchestrationPlan(List.of(singleStep("step-1", "agent-a", "slow task")))
        );
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                model,
                new OrchestrationPlanValidator(),
                new SerialOrchestrationService(catalog, new FakeRuntimeToolCollectionPort(), null, executor, 10, model),
                new OrchestrationEventMapper()
        );
        AgentStage6TestSupport.RecordingChannel channel = AgentStage6TestSupport.channel();
        var observer = AgentStage6TestSupport.observer(channel);

        Thread runner = new Thread(() -> runtime.execute(
                AgentStage6TestSupport.USER,
                "request-1",
                "123e4567-e89b-12d3-a456-426614174000",
                "question",
                "summary",
                List.of(summary("agent-a")),
                new RouteDecision(RouteDecision.Route.ORCHESTRATED, "FORCED_BY_REQUEST"),
                observer
        ));
        runner.start();

        assertTrue(agentStarted.await(5, TimeUnit.SECONDS));
        // Disconnect races the in-flight execution.
        assertTrue(observer.onClientDisconnected());
        release.countDown();
        runner.join(5_000);
        assertTrue(!runner.isAlive());

        assertEquals(ConversationStreamObserver.TerminalState.INTERRUPTED, observer.state());
        assertEquals(0, AgentStage6TestSupport.finishedCount(channel.events()));
        assertEquals(1, channel.completionCount());
    }
}
