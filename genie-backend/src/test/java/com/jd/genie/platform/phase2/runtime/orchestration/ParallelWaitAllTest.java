package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.catalog;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.parallelStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.singleStep;
import static com.jd.genie.platform.phase2.runtime.orchestration.AgentStage6TestSupport.subTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ParallelWaitAllTest {

    @Test
    void waitsForEverySubTaskBeforeReviewAndTheNextTopLevelStep() throws Exception {
        FakeAgentRuntimeCatalogPort catalog = catalog("agent-a", "agent-b", "agent-c");
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch fastStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        Map<String, AtomicInteger> invocations = new ConcurrentHashMap<>();

        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            invocations.computeIfAbsent(context.getRequestId(), key -> new AtomicInteger()).incrementAndGet();
            if ("sub-slow".equals(context.getRequestId())) {
                slowStarted.countDown();
                assertTrue(releaseSlow.await(5, TimeUnit.SECONDS));
            } else {
                fastStarted.countDown();
            }
            return AgentTaskResult.success(context.getRequestId() + " output");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());

        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), executor, 10
        );
        OrchestrationStep next = singleStep("after", "agent-c", "consume", List.of("parallel"));
        Thread runner = new Thread(() -> service.execute(
                AgentStage6TestSupport.USER,
                "query",
                List.of(
                        parallelStep("parallel", List.of(
                                subTask("sub-slow", "agent-a", "slow angle"),
                                subTask("sub-fast", "agent-b", "fast angle")
                        )),
                        next
                ),
                (eventType, step, result, details) -> { }
        ));
        runner.start();

        assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
        assertTrue(fastStarted.await(5, TimeUnit.SECONDS));
        assertEquals(1, invocations.getOrDefault("sub-fast", new AtomicInteger()).get());
        // Next top-level step must not start before the slow subTask finishes.
        assertFalse(invocations.containsKey("after"));
        releaseSlow.countDown();
        runner.join(5_000);
        assertTrue(!runner.isAlive());

        assertEquals(1, invocations.getOrDefault("sub-slow", new AtomicInteger()).get());
        assertEquals(1, invocations.get("after").get());
    }
}
