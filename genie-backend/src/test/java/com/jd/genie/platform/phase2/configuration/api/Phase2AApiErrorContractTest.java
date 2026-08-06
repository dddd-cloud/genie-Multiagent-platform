package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2AApiErrorContractTest extends Phase2AApiTestSupport {

    @Test
    void malformedJsonAndBusinessErrorsUseUnifiedEnvelope() throws Exception {
        AgentDefinitionService service = mock(AgentDefinitionService.class);
        when(service.createAgent(any(), any())).thenThrow(new AgentConfigurationException(MvpErrorCode.MODEL_NOT_AVAILABLE,
            "baseUrl and apiKey must never leak"));
        var mvc = mvc(new Phase2AgentController(service, currentUserProvider, objectMapper));

        mvc.perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MODEL_NOT_AVAILABLE"))
            .andExpect(jsonPath("$.message").value("MODEL_NOT_AVAILABLE"));
    }
}
