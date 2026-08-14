package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationSubTask;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.enums.StepMode;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.subTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ParallelMaxFourTest {

    @Test
    void runsExactlyFourSubTasksConcurrentlyInsideOneParallelStep() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b", "agent-c", "agent-d");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        CountDownLatch allStarted = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        doAnswer(invocation -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            allStarted.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            active.decrementAndGet();
            return AgentTaskResult.success("ok");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), executor, 10
        );
        Thread runner = new Thread(() -> service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(parallelFourStep()),
                (eventType, step, result, details) -> { }
        ));
        runner.start();

        assertTrue(allStarted.await(5, TimeUnit.SECONDS));
        assertEquals(4, maximum.get());
        release.countDown();
        runner.join(5_000);
        assertTrue(!runner.isAlive());
    }

    @Test
    void rejectsFiveOrMoreSubTasksBeforeAnyAgentCanStart() {
        OrchestrationPlanValidator validator = new OrchestrationPlanValidator();
        OrchestrationPlan plan = new OrchestrationPlan(List.of(
                new OrchestrationStep(
                        "parallel",
                        StepMode.PARALLEL_AGENTS,
                        "five angles",
                        List.of(),
                        null,
                        List.of(
                                new OrchestrationSubTask("sub-1", "agent-a", "one"),
                                new OrchestrationSubTask("sub-2", "agent-a", "two"),
                                new OrchestrationSubTask("sub-3", "agent-a", "three"),
                                new OrchestrationSubTask("sub-4", "agent-a", "four"),
                                new OrchestrationSubTask("sub-5", "agent-a", "five")
                        )
                )
        ));

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> validator.validate(plan, List.of(AgentStage6TestSupport.summary("agent-a")))
        );
        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }

    private OrchestrationStep parallelFourStep() {
        return new OrchestrationStep(
                "parallel",
                StepMode.PARALLEL_AGENTS,
                "four independent angles",
                List.of(),
                null,
                List.of(
                        subTask("sub-1", "agent-a", "angle one"),
                        subTask("sub-2", "agent-b", "angle two"),
                        subTask("sub-3", "agent-c", "angle three"),
                        subTask("sub-4", "agent-d", "angle four")
                )
        );
    }
}
