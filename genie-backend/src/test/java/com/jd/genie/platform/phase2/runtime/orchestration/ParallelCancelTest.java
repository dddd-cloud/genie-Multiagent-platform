package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.parallelStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.subTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ParallelCancelTest {

    @Test
    void cancellationConvergesParallelGroupAndNeverStartsTheNextTopLevelStep() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b", "agent-c");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        CountDownLatch firstStarted = new CountDownLatch(1);
        AtomicBoolean cancellationSeen = new AtomicBoolean();
        AtomicReference<AgentBridgeException> thrown = new AtomicReference<>();
        List<String> started = new java.util.concurrent.CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            started.add(context.getRequestId());
            if ("sub-a".equals(context.getRequestId())) {
                firstStarted.countDown();
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            } else {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            return AgentTaskResult.success(context.getRequestId() + " output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), executor, 10
        );
        AtomicBoolean cancel = new AtomicBoolean(false);
        Thread runner = new Thread(() -> {
            try {
                service.execute(
                        AgentStage6TestSupport.USER,
                        "query",
                        List.of(
                                parallelStep("parallel", List.of(
                                        subTask("sub-a", "agent-a", "angle A"),
                                        subTask("sub-b", "agent-b", "angle B")
                                )),
                                singleStep("after", "agent-c", "must not run", List.of("parallel"))
                        ),
                        (eventType, step, result, details) -> { },
                        cancel::get
                );
            } catch (AgentBridgeException error) {
                thrown.set(error);
            }
        });
        runner.start();

        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        cancel.set(true);
        cancellationSeen.set(true);
        runner.join(5_000);
        assertTrue(!runner.isAlive());

        assertEquals(MvpErrorCode.CLIENT_DISCONNECTED, thrown.get().getErrorCode());
        assertTrue(started.stream().noneMatch("after"::equals));
        assertNull(service.runningStepId());
        assertTrue(cancellationSeen.get());
    }
}
