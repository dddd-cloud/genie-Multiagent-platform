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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

abstract class SerialOrchestrationTestSupport {
    protected static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );
    protected static final OrchestrationEventSink NO_EVENTS = (eventType, step, result, details) -> { };

    protected FakeAgentRuntimeCatalogPort catalog(AgentRuntimeProfile... profiles) {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        for (AgentRuntimeProfile profile : profiles) {
            catalog.registerProfile(profile);
        }
        return catalog;
    }

    protected SerialOrchestrationService service(
            FakeAgentRuntimeCatalogPort catalog,
            ConfiguredAgentExecutor executor
    ) {
        return new SerialOrchestrationService(catalog, new FakeRuntimeToolCollectionPort(), executor, 10);
    }

    protected ConfiguredAgentExecutor successfulExecutor() {
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doAnswer(invocation -> AgentTaskResult.success(
                invocation.getArgument(0, AgentContext.class).getRequestId() + " result"
        )).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        return executor;
    }

    protected AgentRuntimeProfile profile(String agentId, long version) {
        return new AgentRuntimeProfile(
                agentId, version, agentId, "description", "prompt", "model", List.of(), List.of()
        );
    }

    protected OrchestrationStep step(String stepId, String agentId, String objective, List<String> inputRefs) {
        return new OrchestrationStep(stepId, agentId, objective, inputRefs);
    }
}
