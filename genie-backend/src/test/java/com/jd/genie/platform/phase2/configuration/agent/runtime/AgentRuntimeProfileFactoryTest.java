package com.jd.genie.platform.phase2.configuration.agent.runtime;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeProfileFactoryTest extends AgentRuntimeCatalogTestSupport {

    @Test
    void recompilesPromptFromCurrentAgentAndEnabledSkillConfiguration() {
        SkillResponse skill = skill("Skill", 1);
        AgentResponse online = onlineAgent("Runtime", List.of(skill));
        SkillResponse updatedSkill = updateSkill(skill, "Updated instruction with {{query}}");

        AgentRuntimeProfile profile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertTrue(profile.compiledSystemPromptTemplate().contains("Updated instruction with {{query}}"));
        assertEquals(updatedSkill.version(), profile.skills().get(0).skillVersion());
    }

    @Test
    void promptInvalidAtRuntimeFailsAsInvalidState() {
        AgentResponse online = onlineAgent("Runtime", List.of());
        jdbcTemplate.update("UPDATE agent_definition SET system_prompt = ? WHERE id = ?", "bad {{secret}}", online.id());

        Phase2ContractException ex = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.loadOnlineProfile(userA(), online.id()));
        assertEquals(MvpErrorCode.AGENT_INVALID_STATE, ex.errorCode());
    }

    @Test
    void runtimeProfileContainsOnlyFrozenDtoFields() {
        SkillResponse skill = skill("Skill", 1);
        AgentResponse online = onlineAgent("Runtime", List.of(skill));

        AgentRuntimeProfile profile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertEquals(online.id(), profile.agentId());
        assertEquals(online.version(), profile.agentVersion());
        assertEquals("Runtime description", profile.description());
        AgentRuntimeSkill runtimeSkill = profile.skills().get(0);
        assertEquals(skill.id(), runtimeSkill.skillId());
        assertEquals(skill.version(), runtimeSkill.skillVersion());
        assertEquals(1, runtimeSkill.sortOrder());
        assertEquals("Instruction 1", runtimeSkill.instruction());
        assertEquals("Requirement 1", runtimeSkill.outputRequirement());
    }

    @Test
    void nullAndBlankProfileInputsFailValidation() {
        assertEquals(MvpErrorCode.VALIDATION_ERROR, assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.loadOnlineProfile(null, "a")).errorCode());
        assertEquals(MvpErrorCode.VALIDATION_ERROR, assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.loadOnlineProfile(userA(), " ")).errorCode());
    }
}
