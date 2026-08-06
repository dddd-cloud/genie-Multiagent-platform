package com.jd.genie.platform.phase2.runtime.controller;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.support.FakeCurrentUserProvider;
import com.jd.genie.platform.phase2.runtime.agent.AgentTestRequest;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfiguredAgentTestControllerTest {

    @Test
    void coversControllerAvailabilityBoundaryWithoutConversationDependencies() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        Phase2AgentTestController controller = new Phase2AgentTestController(
            new FakeCurrentUserProvider(null),
            beans.getBeanProvider(AgentRuntimeCatalogPort.class),
            beans.getBeanProvider(RuntimeToolCollectionPort.class)
        );

        AgentBridgeException error = assertThrows(
            AgentBridgeException.class,
            () -> controller.test("agent-1", new AgentTestRequest("task"))
        );

        assertEquals(MvpErrorCode.INTERNAL_ERROR, error.getErrorCode());
    }
}
