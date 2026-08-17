package com.jd.genie.platform.phase2.configuration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentTeamCrudApiTest extends Phase2AApiMySqlTestSupport {

    @Test
    void teamCrudPersistsMembersAndSoftDeletesWithVersion() throws Exception {
        String masterId = onlineAgent("Team Master");
        String memberId = onlineAgent("Team Member");
        MockMvc mvc = teamMvc();

        String createBody = mvc.perform(post("/api/v2/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Research Team", masterId, memberId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.masterAgentId").value(masterId))
            .andExpect(jsonPath("$.data.masterAgentName").value("Team Master"))
            .andExpect(jsonPath("$.data.memberAgentIds[0]").value(memberId))
            .andExpect(jsonPath("$.data.version").value(0))
            .andReturn().getResponse().getContentAsString();
        String teamId = read(createBody).get("data").get("id").asText();

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT sort_order FROM agent_team_member WHERE team_id = ? AND agent_id = ?",
            Integer.class, teamId, memberId));

        mvc.perform(get("/api/v2/teams/" + teamId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(teamId));
        mvc.perform(get("/api/v2/teams?page=1&pageSize=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(teamId));

        mvc.perform(put("/api/v2/teams/" + teamId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":0,"name":"Research Team v2","description":"updated",
                     "masterAgentId":"%s","memberAgentIds":["%s"]}
                    """.formatted(masterId, memberId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Research Team v2"))
            .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(delete("/api/v2/teams/" + teamId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
            .andExpect(status().isOk());
        assertNotNull(jdbcTemplate.queryForObject(
            "SELECT deleted_at FROM agent_team WHERE id = ?", Object.class, teamId));
        mvc.perform(get("/api/v2/teams/" + teamId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        // Soft delete frees the active name.
        mvc.perform(post("/api/v2/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Research Team v2", masterId, memberId)))
            .andExpect(status().isOk());
    }

    @Test
    void staleVersionConflictsAndInvalidCompositionIsRejected() throws Exception {
        String masterId = onlineAgent("Master A");
        String memberId = onlineAgent("Member A");
        String draftId = createAgent("Draft Agent");
        MockMvc mvc = teamMvc();

        String createBody = mvc.perform(post("/api/v2/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Team A", masterId, memberId)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String teamId = read(createBody).get("data").get("id").asText();

        mvc.perform(put("/api/v2/teams/" + teamId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"version":99,"name":"Team A","description":"desc",
                     "masterAgentId":"%s","memberAgentIds":["%s"]}
                    """.formatted(masterId, memberId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // Master must be ONLINE.
        mvc.perform(post("/api/v2/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Team Draft Master", draftId, memberId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TEAM_MASTER_INVALID"));

        // Master cannot also be a member.
        mvc.perform(post("/api/v2/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Team Self Member", masterId, masterId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TEAM_MEMBERS_INVALID"));

        // At least one member is required.
        mvc.perform(post("/api/v2/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Team Empty","description":"desc","masterAgentId":"%s","memberAgentIds":[]}
                    """.formatted(masterId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TEAM_MEMBERS_INVALID"));
    }

    @Test
    void otherOwnerCannotSeeOrDeleteTeam() throws Exception {
        String masterId = onlineAgent("Owner A Master");
        String memberId = onlineAgent("Owner A Member");
        MockMvc mvc = teamMvc();
        String createBody = mvc.perform(post("/api/v2/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Owner A Team", masterId, memberId)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String teamId = read(createBody).get("data").get("id").asText();

        currentUser = userB();
        try {
            MockMvc otherMvc = teamMvc();
            otherMvc.perform(get("/api/v2/teams/" + teamId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            otherMvc.perform(get("/api/v2/teams?page=1&pageSize=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
            otherMvc.perform(delete("/api/v2/teams/" + teamId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":0}"))
                .andExpect(status().isNotFound());
        } finally {
            currentUser = userA();
        }
    }

    private String createAgent(String name) throws Exception {
        String body = agentMvc().perform(post("/api/v2/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawAgentBody(name)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return read(body).get("data").get("id").asText();
    }

    private String onlineAgent(String name) throws Exception {
        String id = createAgent(name);
        agentMvc().perform(post("/api/v2/agents/" + id + "/online")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
            .andExpect(status().isOk());
        return id;
    }

    private String teamBody(String name, String masterAgentId, String memberAgentId) {
        return """
            {
              "name":"%s",
              "description":"team description",
              "masterAgentId":"%s",
              "memberAgentIds":["%s"]
            }
            """.formatted(name, masterAgentId, memberAgentId);
    }
}
