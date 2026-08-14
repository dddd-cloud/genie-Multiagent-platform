package com.jd.genie.platform.phase2.configuration.skill;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.exception.SkillConfigurationException;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillPackageImportService;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifestParser;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageArchiveReader;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLoader;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageValidator;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillPackageImportServiceTest {
    @TempDir
    Path skillRoot;

    private final CurrentUser userA = new CurrentUser("tenant-a", "owner-a", "owner-a", "Owner A", UserRole.USER);
    private final CurrentUser userB = new CurrentUser("tenant-a", "owner-b", "owner-b", "Owner B", UserRole.USER);

    @Test
    void importCreatesOwnedFilesystemPackage() throws IOException {
        SkillDefinitionService skillService = mock(SkillDefinitionService.class);
        SkillDefinitionMapper skillMapper = mock(SkillDefinitionMapper.class);
        when(skillMapper.existsOwnedActiveName(any(), any(), any(), isNull())).thenReturn(false);
        when(skillService.createSkill(eq(userA), any(SkillCreateRequest.class)))
            .thenReturn(skill("skill-1", "imported-example", "first instruction", 0));
        when(skillService.getSkill(userA, "skill-1"))
            .thenReturn(skill("skill-1", "imported-example", "first instruction", 0));

        SkillResponse imported = importer(skillService, skillMapper)
            .importPackage(userA, zip(validFiles("first instruction")), null);

        assertEquals("skill-1", imported.id());
        assertTrue(Files.exists(packageFile("skill-1", "SKILL.md")));
        assertEquals("first instruction", loader().load(userA, "skill-1").orElseThrow().instructionMarkdown());
        verify(skillService).createSkill(eq(userA), any(SkillCreateRequest.class));
    }

    @Test
    void secondImportToSameSkillUpdatesFiles() throws IOException {
        SkillDefinitionService skillService = mock(SkillDefinitionService.class);
        SkillDefinitionMapper skillMapper = mock(SkillDefinitionMapper.class);
        when(skillMapper.existsOwnedActiveName(any(), any(), any(), any())).thenReturn(false);
        when(skillService.createSkill(eq(userA), any(SkillCreateRequest.class)))
            .thenReturn(skill("skill-1", "imported-example", "first instruction", 0));
        when(skillService.getSkill(userA, "skill-1"))
            .thenReturn(skill("skill-1", "imported-example", "first instruction", 0));
        when(skillService.updateSkill(eq(userA), eq("skill-1"), any(SkillUpdateRequest.class)))
            .thenReturn(skill("skill-1", "imported-example", "updated instruction", 1));

        SkillPackageImportService service = importer(skillService, skillMapper);
        service.importPackage(userA, zip(validFiles("first instruction")), null);
        SkillResponse second = service.importPackage(userA, zip(validFiles("updated instruction")), "skill-1");

        assertEquals("skill-1", second.id());
        assertEquals("updated instruction", loader().load(userA, "skill-1").orElseThrow().instructionMarkdown());
        assertTrue(Files.readString(packageFile("skill-1", "SKILL.md")).contains("updated instruction"));
        verify(skillService).updateSkill(eq(userA), eq("skill-1"), any(SkillUpdateRequest.class));
    }

    @Test
    void otherUserCannotImportIntoOwnedSkillId() throws IOException {
        SkillDefinitionService skillService = mock(SkillDefinitionService.class);
        SkillDefinitionMapper skillMapper = mock(SkillDefinitionMapper.class);
        when(skillMapper.existsOwnedActiveName(any(), any(), any(), any())).thenReturn(false);
        when(skillService.createSkill(eq(userA), any(SkillCreateRequest.class)))
            .thenReturn(skill("skill-1", "imported-example", "owner instruction", 0));
        when(skillService.getSkill(userA, "skill-1"))
            .thenReturn(skill("skill-1", "imported-example", "owner instruction", 0));
        when(skillService.getSkill(userB, "skill-1"))
            .thenThrow(new SkillConfigurationException(MvpErrorCode.RESOURCE_NOT_FOUND, "not found"));

        SkillPackageImportService service = importer(skillService, skillMapper);
        service.importPackage(userA, zip(validFiles("owner instruction")), null);

        SkillConfigurationException error = assertThrows(SkillConfigurationException.class,
            () -> service.importPackage(userB, zip(validFiles("attacker instruction")), "skill-1"));
        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, error.code());
        assertEquals("owner instruction", loader().load(userA, "skill-1").orElseThrow().instructionMarkdown());
        verify(skillService, never()).updateSkill(eq(userB), any(), any());
    }

    @Test
    void traversalZipIsRejectedBeforeWrite() throws IOException {
        SkillDefinitionService skillService = mock(SkillDefinitionService.class);
        SkillDefinitionMapper skillMapper = mock(SkillDefinitionMapper.class);
        Phase2ContractException error = assertThrows(Phase2ContractException.class,
            () -> importer(skillService, skillMapper).importPackage(userA, zip(Map.of(
                "../secret", "nope",
                "SKILL.md", skillMd("x")
            )), null));
        assertEquals(MvpErrorCode.SKILL_PACKAGE_INVALID, error.errorCode());
        verify(skillService, never()).createSkill(any(), any());
    }

    private SkillPackageImportService importer(SkillDefinitionService skillService, SkillDefinitionMapper skillMapper) {
        SkillPackageValidator validator = new SkillPackageValidator();
        SkillManifestParser parser = new SkillManifestParser();
        return new SkillPackageImportService(skillService, skillMapper, loader(validator, parser),
            new SkillPackageArchiveReader(validator, parser));
    }

    private SkillPackageLoader loader() {
        SkillPackageValidator validator = new SkillPackageValidator();
        return loader(validator, new SkillManifestParser());
    }

    private SkillPackageLoader loader(SkillPackageValidator validator, SkillManifestParser parser) {
        return new SkillPackageLoader(skillRoot.toString(), parser, validator, new SkillPackageHasher());
    }

    private Path packageFile(String skillId, String relative) {
        return skillRoot.resolve("users").resolve(userA.tenantId()).resolve(userA.userId())
            .resolve(skillId).resolve(relative);
    }

    private SkillResponse skill(String id, String name, String instruction, long version) {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        return new SkillResponse(id, name, "example package", instruction, null, "ENABLED", version,
            List.of(), now, now);
    }

    private Map<String, String> validFiles(String instruction) {
        return Map.of(
            "SKILL.md", skillMd(instruction),
            "scripts/run.py", "def run(input):\n    return input\n"
        );
    }

    private String skillMd(String instruction) {
        return """
            ---
            schemaVersion: 1
            name: imported-example
            description: example package
            version: 1.0.0
            entrypoints:
              - name: run
                runtime: pyodide
                script: scripts/run.py
            ---

            %s
            """.formatted(instruction);
    }

    private byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
