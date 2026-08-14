package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.skill.api.Phase2SkillImportController;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillPackageImportService;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2SkillImportApiContractTest extends Phase2AApiTestSupport {

    @Test
    void importRouteAcceptsMultipartZipAndOptionalSkillId() throws Exception {
        SkillPackageImportService importService = mock(SkillPackageImportService.class);
        when(importService.importPackage(any(), any(), isNull())).thenReturn(skill("skill-1", "ENABLED", 0));
        when(importService.importPackage(any(), any(), eq("skill-1"))).thenReturn(skill("skill-1", "ENABLED", 1));
        var mvc = mvc(new Phase2SkillImportController(importService, currentUserProvider));

        mvc.perform(multipart("/api/v2/skills/import")
                .file(new MockMultipartFile("file", "skill.zip", "application/zip", "PK".getBytes())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value("skill-1"))
            .andExpect(jsonPath("$.data.tenantId").doesNotExist())
            .andExpect(jsonPath("$.data.ownerId").doesNotExist());
        verify(importService).importPackage(any(), any(), isNull());

        mvc.perform(multipart("/api/v2/skills/import")
                .file(new MockMultipartFile("file", "skill.zip", "application/zip", "PK".getBytes()))
                .param("skillId", "skill-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1));
        verify(importService).importPackage(any(), any(), eq("skill-1"));
    }

    @Test
    void importMapsPackageInvalidAndMissingFile() throws Exception {
        SkillPackageImportService importService = mock(SkillPackageImportService.class);
        when(importService.importPackage(any(), any(), any()))
            .thenThrow(new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "zip too large"));
        var mvc = mvc(new Phase2SkillImportController(importService, currentUserProvider));

        mvc.perform(multipart("/api/v2/skills/import")
                .file(new MockMultipartFile("file", "skill.zip", "application/zip", "PK".getBytes())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("SKILL_PACKAGE_INVALID"));

        mvc.perform(multipart("/api/v2/skills/import")
                .file(new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0])))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("SKILL_PACKAGE_INVALID"));
    }

    @Test
    void importRejectsOversizedZipBeforeService() throws Exception {
        SkillPackageImportService importService = mock(SkillPackageImportService.class);
        var mvc = mvc(new Phase2SkillImportController(importService, currentUserProvider));
        byte[] huge = new byte[(int) SkillPackageLimits.MAX_IMPORT_ZIP_BYTES + 1];
        mvc.perform(multipart("/api/v2/skills/import")
                .file(new MockMultipartFile("file", "huge.zip", "application/zip", huge)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("SKILL_PACKAGE_INVALID"));
    }
}
