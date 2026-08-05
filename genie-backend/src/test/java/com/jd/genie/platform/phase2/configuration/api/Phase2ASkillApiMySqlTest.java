package com.jd.genie.platform.phase2.configuration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2ASkillApiMySqlTest extends Phase2AApiMySqlTestSupport {

    @Test
    void skillCrudStateAndReferenceDeleteProtectionUseRealDatabase() throws Exception {
        var skillMvc = skillMvc();
        String createBody = skillMvc.perform(post("/api/v2/skills").contentType(MediaType.APPLICATION_JSON).content(skillBody("Skill One")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ENABLED"))
            .andReturn().getResponse().getContentAsString();
        String skillId = read(createBody).get("data").get("id").asText();
        assertEquals("Skill One", jdbcTemplate.queryForObject("SELECT name FROM skill_definition WHERE id = ?", String.class, skillId));

        skillMvc.perform(put("/api/v2/skills/" + skillId).contentType(MediaType.APPLICATION_JSON).content("""
            {"version":0,"name":"Skill One Updated","description":"description","instruction":"Instruction","outputRequirement":"Requirement","capabilityKeys":[]}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));
        skillMvc.perform(post("/api/v2/skills/" + skillId + "/disable").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DISABLED"));
        skillMvc.perform(post("/api/v2/skills/" + skillId + "/enable").contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ENABLED"));
        agentMvc().perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content(structuredAgentBody("Agent Uses Skill", skillId)))
            .andExpect(status().isOk());
        skillMvc.perform(delete("/api/v2/skills/" + skillId).contentType(MediaType.APPLICATION_JSON).content("{\"version\":3}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SKILL_IN_USE"));
    }
}
