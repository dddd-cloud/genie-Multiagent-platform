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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRuntimeCatalogMySqlTest extends AgentRuntimeCatalogTestSupport {

    @Test
    void loadsRuntimeProfileWithOrderedEnabledSkillSnapshotAndVersions() {
        SkillResponse second = skill("Second", 2);
        SkillResponse first = skill("First", 1);
        AgentResponse online = onlineAgent("Runtime", List.of(first, second));

        AgentRuntimeProfile profile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertEquals(online.id(), profile.agentId());
        assertEquals(online.version(), profile.agentVersion());
        assertEquals("Runtime", profile.name());
        assertEquals("qwen-plus", profile.resolvedModelName());
        assertEquals(List.of(first.id(), second.id()), profile.skills().stream().map(AgentRuntimeSkill::skillId).toList());
        assertEquals(List.of(1, 2), profile.skills().stream().map(AgentRuntimeSkill::sortOrder).toList());
        assertEquals(List.of(first.version(), second.version()), profile.skills().stream().map(AgentRuntimeSkill::skillVersion).toList());
        assertTrue(profile.compiledSystemPromptTemplate().contains("Instruction 1"));
        assertTrue(profile.compiledSystemPromptTemplate().contains("Instruction 2"));
    }

    @Test
    void skipsDisabledSkillsAtRuntimeAndKeepsAgentOnline() {
        SkillResponse enabled = skill("Enabled", 1);
        SkillResponse disabled = skill("Disabled", 2);
        AgentResponse online = onlineAgent("Runtime", List.of(enabled, disabled));
        skillService.disableSkill(userA(), disabled.id(), disabled.version());

        AgentRuntimeProfile profile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertEquals(List.of(enabled.id()), profile.skills().stream().map(AgentRuntimeSkill::skillId).toList());
        assertTrue(profile.compiledSystemPromptTemplate().contains("Instruction 1"));
        assertTrue(!profile.compiledSystemPromptTemplate().contains("Instruction 2"));
    }

    @Test
    void modelDefaultAndExplicitModelResolveToRealKeys() {
        AgentResponse defaultModel = onlineAgentWithModel("Default model", null);
        AgentResponse explicitModel = onlineAgentWithModel("Explicit model", "qwen-max");

        assertEquals("qwen-plus", runtimeCatalogPort.loadOnlineProfile(userA(), defaultModel.id()).resolvedModelName());
        assertEquals("qwen-max", runtimeCatalogPort.loadOnlineProfile(userA(), explicitModel.id()).resolvedModelName());
    }

    @Test
    void unavailableRuntimeModelFailsWithoutReturningProfile() {
        AgentResponse online = onlineAgentWithModel("Broken model", "qwen-plus");
        jdbcTemplate.update("UPDATE agent_definition SET model_name = ? WHERE id = ?", "missing-model", online.id());

        Phase2ContractException ex = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.loadOnlineProfile(userA(), online.id()));
        assertEquals(MvpErrorCode.MODEL_NOT_AVAILABLE, ex.errorCode());
    }
}
