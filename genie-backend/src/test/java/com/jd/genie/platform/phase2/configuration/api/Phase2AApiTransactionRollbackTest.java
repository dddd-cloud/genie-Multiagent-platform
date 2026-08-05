package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2AApiTransactionRollbackTest extends Phase2AApiMySqlTestSupport {

    @Test
    void toolBindingFailureRollsBackAgentAndSkillWrites() throws Exception {
        fakeToolBindingPort.setWriteException(new Phase2ContractException(MvpErrorCode.TOOL_BINDING_INVALID, "capability failure"));

        agentMvc().perform(post("/api/v2/agents").contentType(MediaType.APPLICATION_JSON).content(rawAgentBody("Rollback Agent")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOOL_BINDING_INVALID"));
        skillMvc().perform(post("/api/v2/skills").contentType(MediaType.APPLICATION_JSON).content(skillBody("Rollback Skill")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOOL_BINDING_INVALID"));

        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_definition WHERE name = 'Rollback Agent'", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM skill_definition WHERE name = 'Rollback Skill'", Long.class));
    }
}
