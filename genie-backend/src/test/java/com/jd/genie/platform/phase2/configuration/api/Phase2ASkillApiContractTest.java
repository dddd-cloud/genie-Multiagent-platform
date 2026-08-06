package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.exception.SkillConfigurationException;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2ASkillApiContractTest extends Phase2AApiTestSupport {

    @Test
    void exposesFrozenSkillCrudAndStateRoutes() throws Exception {
        SkillDefinitionService service = mock(SkillDefinitionService.class);
        when(service.createSkill(any(), any())).thenReturn(skill("skill-1", "ENABLED", 0));
        when(service.getSkill(any(), eq("skill-1"))).thenReturn(skill("skill-1", "ENABLED", 0));
        when(service.listSkills(any(), eq(1), eq(20))).thenReturn(new PageResponse<>(List.of(skill("skill-1", "ENABLED", 0)), 1, 20, false));
        when(service.updateSkill(any(), eq("skill-1"), any())).thenReturn(skill("skill-1", "ENABLED", 1));
        when(service.enableSkill(any(), eq("skill-1"), eq(1L))).thenReturn(skill("skill-1", "ENABLED", 1));
        when(service.disableSkill(any(), eq("skill-1"), eq(1L))).thenReturn(skill("skill-1", "DISABLED", 2));
        var mvc = mvc(new Phase2SkillController(service, currentUserProvider, objectMapper));

        mvc.perform(post("/api/v2/skills").contentType(MediaType.APPLICATION_JSON).content(json(new SkillCreateRequest(
                "Summarize", "Summarize source material", "Produce a concise summary.",
                "Markdown summary with key points.", List.of("builtin:file")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("skill-1"))
            .andExpect(jsonPath("$.data.tenantId").doesNotExist())
            .andExpect(jsonPath("$.data.ownerId").doesNotExist());
        mvc.perform(get("/api/v2/skills?page=1&pageSize=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value("skill-1"));
        mvc.perform(get("/api/v2/skills/skill-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ENABLED"));
        mvc.perform(put("/api/v2/skills/skill-1").contentType(MediaType.APPLICATION_JSON).content(json(new SkillUpdateRequest(
                0L, "Summarize", "Summarize source material", "Instruction", "Requirement", List.of()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(post("/api/v2/skills/skill-1/enable").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ENABLED"));
        mvc.perform(post("/api/v2/skills/skill-1/disable").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mvc.perform(delete("/api/v2/skills/skill-1").contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}"))
            .andExpect(status().isOk());
        verify(service).deleteSkill(any(), eq("skill-1"), eq(2L));
    }

    @Test
    void mapsSkillFrozenErrors() throws Exception {
        SkillDefinitionService service = mock(SkillDefinitionService.class);
        when(service.updateSkill(any(), eq("skill-1"), any()))
            .thenThrow(new SkillConfigurationException(MvpErrorCode.VERSION_CONFLICT, "db version text"));
        doThrow(new SkillConfigurationException(MvpErrorCode.SKILL_IN_USE, "referenced by agent"))
            .when(service).deleteSkill(any(), eq("skill-1"), eq(1L));
        var mvc = mvc(new Phase2SkillController(service, currentUserProvider, objectMapper));

        mvc.perform(put("/api/v2/skills/skill-1").contentType(MediaType.APPLICATION_JSON).content(json(new SkillUpdateRequest(
                0L, "Summarize", "Desc", "Instruction", null, List.of()))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
            .andExpect(jsonPath("$.message").value("VERSION_CONFLICT"));
        mvc.perform(delete("/api/v2/skills/skill-1").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SKILL_IN_USE"));
    }
}
