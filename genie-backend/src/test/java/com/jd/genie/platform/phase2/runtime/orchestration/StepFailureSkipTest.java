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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StepFailureSkipTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void failureBlocksAllRemainingStepsBeforeTheirRuntimeIsLoaded() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        catalog.registerProfile(profile("agent-b"));
        catalog.registerProfile(profile("agent-c"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doReturn(AgentTaskResult.failure("TOOL_TIMEOUT", true))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        SerialOrchestrationService service = new SerialOrchestrationService(catalog, tools, executor, 10);
        List<String> events = new ArrayList<>();

        Map<String, AgentTaskResult> results = service.execute(USER, "query", List.of(
                new OrchestrationStep("first", "agent-a", "first objective", List.of()),
                new OrchestrationStep("second", "agent-b", "second objective", List.of()),
                new OrchestrationStep("third", "agent-c", "third objective", List.of())
        ), (eventType, step, result, details) ->
                events.add(eventType + ":" + step.stepId() + ":" + details.getOrDefault("reasonCode", "")));

        assertEquals(List.of("agent-a"), catalog.getCalls().stream()
                .map(FakeAgentRuntimeCatalogPort.CallRecord::agentId)
                .toList());
        assertEquals(List.of("agent-a"), tools.getCalls().stream()
                .map(FakeRuntimeToolCollectionPort.CallRecord::agentId)
                .toList());
        assertEquals(List.of(
                "STEP_STARTED:first:",
                "STEP_FAILED:first:",
                "STEP_SKIPPED:second:PREVIOUS_STEP_FAILED",
                "STEP_SKIPPED:third:PREVIOUS_STEP_FAILED"
        ), events);
        assertEquals("TOOL_TIMEOUT", results.get("first").errorCode());
        assertEquals("EXECUTION_ERROR", results.get("second").errorCode());
        assertEquals("EXECUTION_ERROR", results.get("third").errorCode());
        verify(executor, times(1)).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
    }

    private AgentRuntimeProfile profile(String agentId) {
        return new AgentRuntimeProfile(
                agentId, 1L, agentId, "description", "prompt", "model", List.of(), List.of()
        );
    }
}
