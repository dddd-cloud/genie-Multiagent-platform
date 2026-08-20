package com.jd.genie.platform.phase2.configuration.agent.runtime;

import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void rawRuntimeProfileRecompilesFromStoredSourceWithCurrentEnabledSkillsOnly() {
        SkillResponse skill = skill("First", 1);
        String rawPrompt = """
            # Skills
            This is a literal user-authored heading.

            # 运行时上下文
            This is not a generated runtime section.

            # Agent Configuration
            This is not a generated configuration boundary.

            Produce {"ok": true} for {{query}}.
            """.trim();
        AgentResponse draft = agentService.createAgent(userA(), new AgentCreateRequest(
            "Runtime Raw",
            "description",
            "RAW",
            null,
            rawPrompt,
            null,
            List.of(new AgentSkillBindingRequest(skill.id(), 1)),
            List.of()
        ));
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of(), Map.of(), List.of()));
        AgentResponse online = agentService.onlineAgent(userA(), draft.id(), draft.version());

        AgentRuntimeProfile firstProfile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());
        AgentRuntimeProfile secondProfile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertTrue(firstProfile.compiledSystemPromptTemplate().contains(rawPrompt));
        assertEquals(1, occurrences(firstProfile.compiledSystemPromptTemplate(), "Instruction 1"));
        assertEquals(1, occurrences(secondProfile.compiledSystemPromptTemplate(), "Instruction 1"));

        SkillResponse updatedSkill = updateSkill(skill, "Fresh runtime instruction");
        AgentRuntimeProfile updatedProfile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());
        assertTrue(updatedProfile.compiledSystemPromptTemplate().contains("Fresh runtime instruction"));
        assertTrue(!updatedProfile.compiledSystemPromptTemplate().contains("Instruction 1"));

        skillService.disableSkill(userA(), updatedSkill.id(), updatedSkill.version());
        AgentRuntimeProfile disabledProfile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertEquals(List.of(skill.id()), firstProfile.skills().stream().map(AgentRuntimeSkill::skillId).toList());
        assertEquals(List.of(), disabledProfile.skills());
        assertTrue(firstProfile.compiledSystemPromptTemplate().contains("Instruction 1"));
        assertTrue(!disabledProfile.compiledSystemPromptTemplate().contains("Fresh runtime instruction"));
        assertTrue(disabledProfile.compiledSystemPromptTemplate().contains("No enabled skills are attached."));
    }

    @Test
    void modelDefaultAndExplicitModelResolveToSharedCatalogDefault() {
        AgentResponse defaultModel = onlineAgentWithModel("Default model", null);
        AgentResponse explicitModel = onlineAgentWithModel("Explicit model", "qwen-max");

        assertEquals("qwen-plus", runtimeCatalogPort.loadOnlineProfile(userA(), defaultModel.id()).resolvedModelName());
        assertEquals("qwen-plus", runtimeCatalogPort.loadOnlineProfile(userA(), explicitModel.id()).resolvedModelName());
    }

    @Test
    void missingAgentModelStillLoadsUsingCatalogDefault() {
        AgentResponse online = onlineAgentWithModel("Broken model", "qwen-plus");
        jdbcTemplate.update("UPDATE agent_definition SET model_name = ? WHERE id = ?", "missing-model", online.id());

        assertEquals("qwen-plus", runtimeCatalogPort.loadOnlineProfile(userA(), online.id()).resolvedModelName());
    }

    private int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while (true) {
            int next = value.indexOf(needle, offset);
            if (next < 0) {
                return count;
            }
            count++;
            offset = next + needle.length();
        }
    }
}
