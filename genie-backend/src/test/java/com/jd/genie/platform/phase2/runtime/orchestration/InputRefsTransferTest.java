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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class InputRefsTransferTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1", "user-1", "alice", "Alice", UserRole.USER
    );

    @Test
    void transfersOnlyExplicitSuccessfulReferencesToTheDependentAgent() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-a"));
        catalog.registerProfile(profile("agent-b"));
        catalog.registerProfile(profile("agent-c"));
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        Map<String, AgentContext> contexts = new HashMap<>();
        doAnswer(invocation -> {
            AgentContext context = invocation.getArgument(0);
            contexts.put(context.getRequestId(), context);
            return switch (context.getRequestId()) {
                case "referenced" -> AgentTaskResult.success("allowed-output");
                case "unrelated" -> AgentTaskResult.success("unrelated-output");
                default -> AgentTaskResult.success("final-output");
            };
        }).when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(), anyInt());
        SerialOrchestrationService service = new SerialOrchestrationService(
                catalog, new FakeRuntimeToolCollectionPort(), executor, 10
        );

        service.execute(USER, "original-query", List.of(
                new OrchestrationStep("referenced", "agent-a", "collect facts", List.of()),
                new OrchestrationStep("unrelated", "agent-b", "collect unrelated facts", List.of()),
                new OrchestrationStep("consumer", "agent-c", "compose answer", List.of("referenced"))
        ), (eventType, step, result, details) -> { });

        AgentContext consumer = contexts.get("consumer");
        assertNotNull(consumer);
        assertTrue(consumer.getBasePrompt().contains("allowed-output"));
        assertFalse(consumer.getBasePrompt().contains("unrelated-output"));
        // The user question is carried as a topic bound only, never as the step's own task.
        assertTrue(consumer.getBasePrompt().contains("只用于限定主题"));
    }

    private AgentRuntimeProfile profile(String agentId) {
        return new AgentRuntimeProfile(
                agentId, 1L, agentId, "description", "prompt", "model", List.of(), List.of()
        );
    }
}
