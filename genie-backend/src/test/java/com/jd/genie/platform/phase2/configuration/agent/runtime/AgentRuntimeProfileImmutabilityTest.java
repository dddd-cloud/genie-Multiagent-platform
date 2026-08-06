package com.jd.genie.platform.phase2.configuration.agent.runtime;

import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeProfileImmutabilityTest extends AgentRuntimeCatalogTestSupport {

    @Test
    void returnedCollectionsAreImmutable() {
        SkillResponse skill = skill("Skill", 1);
        AgentResponse online = onlineAgent("Runtime", List.of(skill));
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of(CapabilityKeys.BUILTIN_FILE), Map.of(), List.of()));

        AgentRuntimeProfile profile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertThrows(UnsupportedOperationException.class, () -> profile.skills().clear());
        assertThrows(UnsupportedOperationException.class, () -> profile.capabilityKeys().clear());
    }

    @Test
    void loadedProfileIsSnapshotButNextLoadSeesNewDatabaseVersion() {
        SkillResponse skill = skill("Skill", 1);
        AgentResponse online = onlineAgent("Runtime", List.of(skill));

        AgentRuntimeProfile first = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());
        SkillResponse updated = updateSkill(skill, "New instruction after first load");
        AgentRuntimeProfile second = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertEquals(skill.version(), first.skills().get(0).skillVersion());
        assertTrue(first.compiledSystemPromptTemplate().contains("Instruction 1"));
        assertEquals(updated.version(), second.skills().get(0).skillVersion());
        assertTrue(second.compiledSystemPromptTemplate().contains("New instruction after first load"));
    }

    @Test
    void runtimeSkillRecordIsImmutableValueSnapshot() {
        SkillResponse skill = skill("Skill", 1);
        AgentResponse online = onlineAgent("Runtime", List.of(skill));

        AgentRuntimeSkill runtimeSkill = runtimeCatalogPort.loadOnlineProfile(userA(), online.id()).skills().get(0);
        updateSkill(skill, "Changed later");

        assertEquals("Instruction 1", runtimeSkill.instruction());
        assertEquals(skill.version(), runtimeSkill.skillVersion());
    }
}
