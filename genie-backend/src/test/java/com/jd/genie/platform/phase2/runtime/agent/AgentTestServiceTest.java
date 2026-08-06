package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class AgentTestServiceTest {
    private final CurrentUser user = new CurrentUser("tenant-1", "user-1", "user", "User", UserRole.USER);

    @Test
    void runsOnlineAgentThroughSharedRuntimeWithoutConversationPorts() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-1"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doReturn(AgentTaskResult.success("safe result"))
                .when(executor).execute(any(AgentContext.class), any(AgentRuntimeProfile.class), any(ConfiguredAgentPrinter.class), anyInt());

        AgentTestResponse response = service(catalog, tools, executor).test("agent-1", new AgentTestRequest("test task"));

        assertEquals("model-1", response.model());
        assertEquals(List.of("skill-1"), response.skillSummary());
        assertEquals(List.of("capability-1"), response.capabilityKeys());
        assertEquals(AgentTaskResult.Status.SUCCESS, response.result().status());
        assertEquals(1, catalog.getCalls().size());
        assertEquals(1, tools.getCalls().size());
    }

    @Test
    void rejectsOfflineAndInvisibleAgentsBeforeToolConstruction() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("offline"));
        catalog.registerProfile(profile("hidden"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        catalog.markOffline("offline");
        catalog.hideFromUser("hidden");

        assertError(MvpErrorCode.AGENT_OFFLINE, () -> service(catalog, tools, mock(ConfiguredAgentExecutor.class))
                .test("offline", new AgentTestRequest("task")));
        assertError(MvpErrorCode.RESOURCE_NOT_FOUND, () -> service(catalog, tools, mock(ConfiguredAgentExecutor.class))
                .test("hidden", new AgentTestRequest("task")));
        assertEquals(0, tools.getCalls().size());
    }

    @Test
    void propagatesControlledToolAndExecutionFailuresWithoutPersistence() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        catalog.registerProfile(profile("agent-1"));
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();
        tools.setBuildException(new AgentBridgeException(MvpErrorCode.TOOL_TIMEOUT, "tool failed"));

        assertError(MvpErrorCode.TOOL_TIMEOUT, () -> service(catalog, tools, mock(ConfiguredAgentExecutor.class))
                .test("agent-1", new AgentTestRequest("task")));

        tools.setBuildException(null);
        ConfiguredAgentExecutor executor = mock(ConfiguredAgentExecutor.class);
        doReturn(AgentTaskResult.failure("EXECUTION_ERROR", true))
                .when(executor).execute(any(), any(), any(), anyInt());
        AgentTestResponse response = service(catalog, tools, executor).test("agent-1", new AgentTestRequest("task"));

        assertEquals(AgentTaskResult.Status.FAILURE, response.result().status());
        assertEquals("EXECUTION_ERROR", response.result().errorCode());
    }

    @Test
    void rejectsInvalidRequestBeforeReadingAgentRuntime() {
        FakeAgentRuntimeCatalogPort catalog = new FakeAgentRuntimeCatalogPort();
        FakeRuntimeToolCollectionPort tools = new FakeRuntimeToolCollectionPort();

        assertError(MvpErrorCode.VALIDATION_ERROR, () -> service(catalog, tools, mock(ConfiguredAgentExecutor.class))
                .test("agent-1", new AgentTestRequest(" ")));
        assertEquals(0, catalog.getCalls().size());
        assertEquals(0, tools.getCalls().size());
    }

    private AgentTestService service(
            FakeAgentRuntimeCatalogPort catalog,
            FakeRuntimeToolCollectionPort tools,
            ConfiguredAgentExecutor executor
    ) {
        CurrentUserProvider users = () -> user;
        return new AgentTestService(users, catalog, tools, executor);
    }

    private AgentRuntimeProfile profile(String agentId) {
        return new AgentRuntimeProfile(
                agentId, 1L, "Agent", "description", "secret prompt", "model-1",
                List.of(new AgentRuntimeSkill("skill-1", 1L, 1, "secret instruction", "secret requirement")),
                List.of("capability-1")
        );
    }

    private void assertError(MvpErrorCode expected, Runnable action) {
        AgentBridgeException error = assertThrows(AgentBridgeException.class, action::run);
        assertEquals(expected, error.getErrorCode());
    }
}
