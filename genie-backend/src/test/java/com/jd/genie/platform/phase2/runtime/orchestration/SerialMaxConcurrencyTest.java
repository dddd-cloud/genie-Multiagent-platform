package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class SerialMaxConcurrencyTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void maintainsAtMostOneRunningBusinessAgentForTheWholePlan() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        catalog.registerProfile(profile("agent-b"));
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), executor, 10
        );
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            assertEquals(context.getRequestId(), service.runningStepId());
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            try {
                return AgentTaskResult.success(context.getRequestId() + " result");
            } finally {
                active.decrementAndGet();
            }
        }).when(executor).execute(any(), any(), any(), any(Integer.TYPE));

        service.execute(USER, "question", List.of(
                new OrchestrationStep("step-1", "agent-a", "first", List.of()),
                new OrchestrationStep("step-2", "agent-b", "second", List.of())
        ), (eventType, step, result, details) -> { });

        assertEquals(1, maximum.get());
        assertEquals(0, active.get());
        assertEquals(null, service.runningStepId());
    }

    private AgentRuntimeProfile profile(String agentId) {
        return new AgentRuntimeProfile(
                agentId, 1L, agentId, "description", "prompt", "model", List.of(), List.of()
        );
    }
}
