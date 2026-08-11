package com.jd.genie.platform.phase2.configuration.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2contract.dto.VersionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/agents")
@RequiredArgsConstructor
public class Phase2AgentController {
    private static final String OK = "OK";
    private static final String SUCCESS = "success";

    private final AgentDefinitionService agentService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ApiResponse<AgentApiAssembler.AgentView> create(@RequestBody(required = false) AgentCreateRequest request) {
        return success(assembler().agent(agentService.createAgent(currentUser(), request)));
    }

    @GetMapping
    public ApiResponse<PageResponse<AgentApiAssembler.AgentView>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        PageResponse<AgentResponse> response = agentService.listAgents(currentUser(), page, pageSize);
        return success(new PageResponse<>(
            response.items().stream().map(assembler()::agent).toList(),
            response.page(),
            response.pageSize(),
            response.hasMore()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentApiAssembler.AgentView> detail(@PathVariable String id) {
        return success(assembler().agent(agentService.getAgent(currentUser(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AgentApiAssembler.AgentView> update(@PathVariable String id,
                                                             @RequestBody(required = false) AgentUpdateRequest request) {
        return success(assembler().agent(agentService.updateAgent(currentUser(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        agentService.deleteAgent(currentUser(), id, version(request));
        return success(null);
    }

    @PostMapping("/{id}/online")
    public ApiResponse<AgentApiAssembler.AgentView> online(@PathVariable String id,
                                                             @RequestBody(required = false) VersionRequest request) {
        return success(assembler().agent(agentService.onlineAgent(currentUser(), id, version(request))));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<AgentApiAssembler.AgentView> offline(@PathVariable String id,
                                                              @RequestBody(required = false) VersionRequest request) {
        return success(assembler().agent(agentService.offlineAgent(currentUser(), id, version(request))));
    }

    private Long version(VersionRequest request) {
        return request == null ? null : request.version();
    }

    private CurrentUser currentUser() {
        return currentUserProvider.requireCurrentUser();
    }

    private AgentApiAssembler assembler() {
        return new AgentApiAssembler(objectMapper);
    }

    private <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(OK, SUCCESS, data);
    }
}
