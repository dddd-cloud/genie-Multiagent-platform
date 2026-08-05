package com.jd.genie.platform.phase2.configuration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2AApiOwnershipMySqlTest extends Phase2AApiMySqlTestSupport {

    @Test
    void tenantAndOwnerIsolationHideOtherUsersResources() throws Exception {
        String body = agentMvc().perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content(rawAgentBody("Private Agent")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String agentId = read(body).get("data").get("id").asText();

        currentUser = userB();
        agentMvc().perform(get("/api/v2/agents/" + agentId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        agentMvc().perform(get("/api/v2/agents?page=1&pageSize=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0));

        currentUser = tenantBUser();
        agentMvc().perform(get("/api/v2/agents/" + agentId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
