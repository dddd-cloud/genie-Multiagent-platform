package com.jd.genie.platform.phase2.configuration.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2AAgentApiMySqlTest extends Phase2AApiMySqlTestSupport {

    @Test
    void rawAgentCrudPersistsSourcePromptAndSoftDeleteAllowsNameReuse() throws Exception {
        var mvc = agentMvc();
        String createBody = mvc.perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content(rawAgentBody("Raw Agent")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.promptMode").value("RAW"))
            .andExpect(jsonPath("$.data.promptConfig").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        var created = read(createBody).get("data");
        String id = created.get("id").asText();
        long version = created.get("version").asLong();

        var row = jdbcTemplate.queryForMap("SELECT prompt_mode, prompt_config, system_prompt, version FROM agent_definition WHERE id = ?", id);
        assertEquals("RAW", row.get("prompt_mode"));
        assertNull(row.get("prompt_config"));
        assertEquals("raw prompt # Skills {\"json\":true}", row.get("system_prompt"));
        assertEquals(0L, ((Number) row.get("version")).longValue());

        mvc.perform(get("/api/v2/agents/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(get("/api/v2/agents?page=1&pageSize=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(id));
        mvc.perform(put("/api/v2/agents/" + id).contentType(MediaType.APPLICATION_JSON).content("""
            {
              "version":0,
              "name":"Raw Agent Updated",
              "description":"description",
              "promptMode":"RAW",
              "systemPrompt":"new raw prompt",
              "modelName":"system-default",
              "skills":[],
              "capabilityKeys":[]
            }
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(delete("/api/v2/agents/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
            .andExpect(status().isOk());
        assertNotNull(jdbcTemplate.queryForObject("SELECT deleted_at FROM agent_definition WHERE id = ?", Object.class, id));
        mvc.perform(get("/api/v2/agents/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content(rawAgentBody("Raw Agent Updated")))
            .andExpect(status().isOk());
    }

    @Test
    void structuredAgentStoresCanonicalPromptConfigAndRejectsStaleVersion() throws Exception {
        var skillResult = skillMvc().perform(post("/api/v2/skills").contentType(MediaType.APPLICATION_JSON).content(skillBody("Skill One")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String skillId = read(skillResult).get("data").get("id").asText();
        var mvc = agentMvc();
        String createBody = mvc.perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content(structuredAgentBody("Structured Agent", skillId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.promptConfig.role").value("Assistant"))
            .andReturn().getResponse().getContentAsString();
        String id = read(createBody).get("data").get("id").asText();
        assertEquals(objectMapper.readTree("{\"role\":\"Assistant\",\"objective\":\"Do research\"}"),
            objectMapper.readTree(jdbcTemplate.queryForObject("SELECT CAST(prompt_config AS CHAR) FROM agent_definition WHERE id = ?", String.class, id)));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT sort_order FROM agent_skill_binding WHERE agent_id = ? AND skill_id = ?", Integer.class, id, skillId));

        mvc.perform(put("/api/v2/agents/" + id).contentType(MediaType.APPLICATION_JSON).content("""
            {"version":99,"name":"Structured Agent v2","description":"description","promptMode":"RAW","systemPrompt":"raw","modelName":"system-default","skills":[],"capabilityKeys":[]}
            """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }
}
