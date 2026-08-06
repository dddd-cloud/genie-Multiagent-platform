package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResult;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationStep;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ResultReuseSignatureTest extends SerialOrchestrationTestSupport {

    @Test
    void reusesWhenOnlyObjectiveWhitespaceChanges() {
        FakeAgentRuntimeCatalogPort catalog = catalog(profile("agent-a", 1));
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doReturn(AgentTaskResult.success("cached result"))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        SerialOrchestrationService service = service(catalog, executor);
        Map<String, AgentTaskResult> reusable = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();

        service.execute(USER, "query", List.of(step("first", "agent-a", "normalize\n objective", List.of())),
                (eventType, current, result, details) -> reasons.add(eventType + ":" + details.getOrDefault("reasonCode", "")),
                () -> false, reusable);
        service.execute(USER, "query", List.of(step("second", "agent-a", " normalize   objective ", List.of())),
                (eventType, current, result, details) -> reasons.add(eventType + ":" + details.getOrDefault("reasonCode", "")),
                () -> false, reusable);

        verify(executor, times(1)).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        assertEquals(List.of("STEP_STARTED:", "STEP_COMPLETED:", "STEP_COMPLETED:REUSED"), reasons);
    }

    @Test
    void doesNotReuseWhenAgentIdentityChanges() {
        FakeAgentRuntimeCatalogPort catalog = catalog(profile("agent-a", 1), profile("agent-b", 1));
        ConfiguredAgentExecutor executor = successfulExecutor();
        SerialOrchestrationService service = service(catalog, executor);
        Map<String, AgentTaskResult> reusable = new LinkedHashMap<>();

        service.execute(USER, "query", List.of(step("first", "agent-a", "same objective", List.of())),
                NO_EVENTS, () -> false, reusable);
        service.execute(USER, "query", List.of(step("second", "agent-b", "same objective", List.of())),
                NO_EVENTS, () -> false, reusable);

        verify(executor, times(2)).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
    }

    @Test
    void doesNotReuseWhenAgentVersionChanges() {
        FakeAgentRuntimeCatalogPort catalog = catalog(profile("agent-a", 1));
        ConfiguredAgentExecutor executor = successfulExecutor();
        SerialOrchestrationService service = service(catalog, executor);
        Map<String, AgentTaskResult> reusable = new LinkedHashMap<>();

        service.execute(USER, "query", List.of(step("first", "agent-a", "same objective", List.of())),
                NO_EVENTS, () -> false, reusable);
        catalog.registerProfile(profile("agent-a", 2));
        service.execute(USER, "query", List.of(step("second", "agent-a", "same objective", List.of())),
                NO_EVENTS, () -> false, reusable);

        verify(executor, times(2)).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
    }

    @Test
    void doesNotReuseWhenSuccessfulDependencyOutputChanges() {
        FakeAgentRuntimeCatalogPort catalog = catalog(profile("source", 1), profile("consumer", 1));
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        AtomicInteger sourceExecutions = new AtomicInteger();
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            if ("source".equals(context.getRequestId())) {
                return AgentTaskResult.success("dependency-" + sourceExecutions.incrementAndGet());
            }
            return AgentTaskResult.success("consumer-result");
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        SerialOrchestrationService service = service(catalog, executor);
        Map<String, AgentTaskResult> reusable = new LinkedHashMap<>();
        List<OrchestrationStep> steps = List.of(
                step("source", "source", "collect dependency", List.of()),
                step("consumer", "consumer", "compose result", List.of("source"))
        );

        service.execute(USER, "query", steps, NO_EVENTS, () -> false, reusable);
        catalog.registerProfile(profile("source", 2));
        service.execute(USER, "query", steps, NO_EVENTS, () -> false, reusable);

        assertEquals(2, sourceExecutions.get());
        verify(executor, times(4)).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
    }
}
