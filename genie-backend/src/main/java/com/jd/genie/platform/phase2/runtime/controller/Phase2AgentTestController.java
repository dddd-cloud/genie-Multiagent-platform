package com.jd.genie.platform.phase2.runtime.controller;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResultParser;
import com.jd.genie.platform.phase2.runtime.agent.AgentTestRequest;
import com.jd.genie.platform.phase2.runtime.agent.AgentTestResponse;
import com.jd.genie.platform.phase2.runtime.agent.AgentTestService;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredReactAgentFactory;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/agents")
public final class Phase2AgentTestController {
    private final CurrentUserProvider currentUserProvider;
    private final ObjectProvider<AgentRuntimeCatalogPort> catalogPortProvider;
    private final ObjectProvider<RuntimeToolCollectionPort> toolCollectionPortProvider;

    public Phase2AgentTestController(
            CurrentUserProvider currentUserProvider,
            ObjectProvider<AgentRuntimeCatalogPort> catalogPortProvider,
            ObjectProvider<RuntimeToolCollectionPort> toolCollectionPortProvider
    ) {
        this.currentUserProvider = currentUserProvider;
        this.catalogPortProvider = catalogPortProvider;
        this.toolCollectionPortProvider = toolCollectionPortProvider;
    }

    @PostMapping("/{id}/test")
    public ApiResponse<AgentTestResponse> test(@PathVariable("id") String agentId, @RequestBody AgentTestRequest request) {
        AgentRuntimeCatalogPort catalogPort = catalogPortProvider.getIfAvailable();
        RuntimeToolCollectionPort toolCollectionPort = toolCollectionPortProvider.getIfAvailable();
        if (catalogPort == null || toolCollectionPort == null) {
            throw new AgentBridgeException(MvpErrorCode.INTERNAL_ERROR, "Agent test runtime is unavailable");
        }
        AgentTestService service = new AgentTestService(
                currentUserProvider,
                catalogPort,
                toolCollectionPort,
                new ConfiguredAgentExecutor(new ConfiguredReactAgentFactory(), new AgentTaskResultParser())
        );
        return new ApiResponse<>("OK", "OK", service.test(agentId, request));
    }
}
