package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2AAgentApiContractTest extends Phase2AApiTestSupport {

    @Test
    void exposesFrozenAgentCrudAndStateRoutes() throws Exception {
        AgentDefinitionService service = mock(AgentDefinitionService.class);
        when(service.createAgent(any(), any())).thenReturn(agent("agent-1", "DRAFT", 0));
        when(service.getAgent(any(), eq("agent-1"))).thenReturn(agent("agent-1", "DRAFT", 0));
        when(service.listAgents(any(), eq(1), eq(20))).thenReturn(new PageResponse<>(List.of(agent("agent-1", "DRAFT", 0)), 1, 20, false));
        when(service.updateAgent(any(), eq("agent-1"), any())).thenReturn(agent("agent-1", "DRAFT", 1));
        when(service.onlineAgent(any(), eq("agent-1"), eq(1L))).thenReturn(agent("agent-1", "ONLINE", 2));
        when(service.offlineAgent(any(), eq("agent-1"), eq(2L))).thenReturn(agent("agent-1", "OFFLINE", 3));
        var mvc = mvc(new Phase2AgentController(service, currentUserProvider, objectMapper));

        mvc.perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content(json(new AgentCreateRequest(
                "Research Agent", "Researches topics", "STRUCTURED", "{\"role\":\"researcher\"}", null,
                "qwen-plus", List.of(new AgentSkillBindingRequest("skill-1", 1)), List.of("builtin:file")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.promptConfig.role").value("researcher"))
            .andExpect(jsonPath("$.data.tenantId").doesNotExist())
            .andExpect(jsonPath("$.data.ownerId").doesNotExist());
        mvc.perform(get("/api/v2/agents?page=1&pageSize=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value("agent-1"))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.hasMore").value(false));
        mvc.perform(get("/api/v2/agents/agent-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("agent-1"));
        mvc.perform(put("/api/v2/agents/agent-1").contentType(MediaType.APPLICATION_JSON).content(json(new AgentUpdateRequest(
                0L, "Research Agent", "Researches topics", "RAW", null, "raw source", "qwen-plus", List.of(), List.of()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(post("/api/v2/agents/agent-1/online").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ONLINE"));
        mvc.perform(post("/api/v2/agents/agent-1/offline").contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OFFLINE"));
        mvc.perform(delete("/api/v2/agents/agent-1").contentType(MediaType.APPLICATION_JSON).content("{\"version\":3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").doesNotExist());
        verify(service).deleteAgent(any(), eq("agent-1"), eq(3L));
    }

    @Test
    void mapsAgentFrozenErrorsAndDoesNotExposeTestEndpoint() throws Exception {
        AgentDefinitionService service = mock(AgentDefinitionService.class);
        when(service.onlineAgent(any(), eq("agent-1"), eq(1L)))
            .thenThrow(new AgentConfigurationException(MvpErrorCode.AGENT_INVALID_STATE, "internal detail"));
        doThrow(new AgentConfigurationException(MvpErrorCode.AGENT_MUST_BE_OFFLINE, "internal detail"))
            .when(service).deleteAgent(any(), eq("agent-2"), eq(1L));
        when(service.getAgent(any(), eq("missing")))
            .thenThrow(new AgentConfigurationException(MvpErrorCode.RESOURCE_NOT_FOUND, "internal detail"));
        var mvc = mvc(new Phase2AgentController(service, currentUserProvider, objectMapper));

        mvc.perform(post("/api/v2/agents/agent-1/online").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AGENT_INVALID_STATE"))
            .andExpect(jsonPath("$.message").value("AGENT_INVALID_STATE"));
        mvc.perform(delete("/api/v2/agents/agent-2").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AGENT_MUST_BE_OFFLINE"));
        mvc.perform(get("/api/v2/agents/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/api/v2/agents/agent-1/test").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        verify(service, never()).onlineAgent(any(), eq("agent-1/test"), any());
    }
}
