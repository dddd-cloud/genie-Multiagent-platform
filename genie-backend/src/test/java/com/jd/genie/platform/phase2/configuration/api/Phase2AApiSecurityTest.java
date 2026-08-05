package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2AApiSecurityTest extends Phase2AApiTestSupport {

    @Test
    void forgedTenantAndOwnerFieldsDoNotOverrideCurrentUserProvider() throws Exception {
        AgentDefinitionService service = mock(AgentDefinitionService.class);
        when(service.createAgent(any(), any())).thenReturn(rawAgent("agent-1", "DRAFT", 0));
        var mvc = mvc(new Phase2AgentController(service, currentUserProvider, objectMapper));

        mvc.perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content("""
            {
              "tenantId":"evil-tenant",
              "ownerId":"evil-owner",
              "userId":"evil-user",
              "name":"Raw Agent",
              "description":"Uses raw prompt",
              "promptMode":"RAW",
              "systemPrompt":"raw source",
              "modelName":"system-default",
              "skills":[],
              "capabilityKeys":[]
            }
            """))
            .andExpect(status().isOk());

        org.mockito.Mockito.verify(service).createAgent(argThat(u ->
            "tenant-a".equals(u.tenantId()) && "owner-a".equals(u.userId())), any(AgentCreateRequest.class));
    }
}
