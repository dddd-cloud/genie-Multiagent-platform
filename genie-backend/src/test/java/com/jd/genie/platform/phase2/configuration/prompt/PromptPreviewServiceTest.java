package com.jd.genie.platform.phase2.configuration.prompt;

import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptPreviewServiceTest extends Phase2AMySqlTestSupport {

    @Autowired
    private PromptPreviewService previewService;
    @Autowired
    private SkillDefinitionService skillService;

    @Test
    void previewsEnabledSkillsWithoutWritingDatabaseOrCallingToolBinding() {
        SkillResponse first = skillService.createSkill(userA(), new SkillCreateRequest("First", "desc", "first instruction", null, List.of()));
        SkillResponse second = skillService.createSkill(userA(), new SkillCreateRequest("Second", "desc", "second instruction", null, List.of()));
        fakeToolBindingPort.reset();
        Long agentCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM agent_definition", Long.class);
        Long bindingCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM agent_skill_binding", Long.class);

        PromptPreviewResponse response = previewService.preview(userA(), new PromptPreviewRequest("STRUCTURED",
            "{\"objective\":\"Preview\"}", "forged", "system-default",
            List.of(new AgentSkillBindingRequest(second.id(), 2), new AgentSkillBindingRequest(first.id(), 1))));

        assertEquals("qwen-plus", response.resolvedModelName());
        assertEquals(List.of(
            new PromptSkillFragmentView(first.id(), first.version(), 1),
            new PromptSkillFragmentView(second.id(), second.version(), 2)
        ), response.skillFragments());
        assertTrue(response.compiledSystemPromptTemplate().indexOf("First") < response.compiledSystemPromptTemplate().indexOf("Second"));
        assertEquals(agentCount, jdbcTemplate.queryForObject("SELECT COUNT(1) FROM agent_definition", Long.class));
        assertEquals(bindingCount, jdbcTemplate.queryForObject("SELECT COUNT(1) FROM agent_skill_binding", Long.class));
        assertTrue(fakeToolBindingPort.getCalls().isEmpty());
    }
}
