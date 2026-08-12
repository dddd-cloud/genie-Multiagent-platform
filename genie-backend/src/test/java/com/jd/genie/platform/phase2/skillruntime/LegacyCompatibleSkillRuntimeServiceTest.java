package com.jd.genie.platform.phase2.skillruntime;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.skill.binding.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2.configuration.skill.entity.SkillDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifestParser;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLoader;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageValidator;
import com.jd.genie.platform.phase2.skillruntime.execution.BrowserSkillExecutionCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingSpec;
import com.jd.genie.platform.phase2contract.dto.SkillRuntimePackage;
import com.jd.genie.platform.phase2contract.enums.SkillPackageMode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyCompatibleSkillRuntimeServiceTest {
    @TempDir Path temp;
    private final CurrentUser user = new CurrentUser("tenant-a", "owner-a", "owner", "Owner", UserRole.USER);

    @Test
    void missingPackagePreservesLegacySnapshotAndUsesSha256() {
        LegacyCompatibleSkillRuntimeService service = service(skill());
        SkillRuntimePackage result = service.resolveForBindings(user, List.of(new AgentSkillBindingSpec("skill-a", 3)), true).get(0);

        assertEquals(SkillPackageMode.LEGACY_SYNTHETIC, result.packageMode());
        assertEquals("database instruction", result.instructionMarkdown());
        assertTrue(result.packageHash().matches("[0-9a-f]{64}"));
        assertEquals(List.of(), result.resourceManifest());
        assertEquals(List.of(), result.entrypoints());
    }

    @Test
    void validPackageOverridesInstructionAndProducesFilesystemSnapshot() throws IOException {
        Path root = temp.resolve("users/tenant-a/owner-a/skill-a");
        Files.createDirectories(root.resolve("references"));
        Files.writeString(root.resolve("SKILL.md"), """
            ---
            schemaVersion: 1
            name: filesystem name
            description: filesystem description
            version: 2.0.0
            ---

            filesystem instruction
            """);
        Files.writeString(root.resolve("references/info.txt"), "resource");

        SkillRuntimePackage result = service(skill()).resolveForBindings(
            user, List.of(new AgentSkillBindingSpec("skill-a", 0)), true).get(0);

        assertEquals(SkillPackageMode.FILESYSTEM, result.packageMode());
        assertEquals("filesystem instruction", result.instructionMarkdown().trim());
        assertEquals("2.0.0", result.packageVersion());
        assertEquals(List.of("references/info.txt"), result.resourceManifest());
    }

    @Test
    void existingInvalidPackageFailsClosedInsteadOfFallingBack() throws IOException {
        Path root = temp.resolve("users/tenant-a/owner-a/skill-a");
        Files.createDirectories(root);
        Files.writeString(root.resolve("SKILL.md"), "invalid");

        Phase2ContractException error = assertThrows(Phase2ContractException.class, () -> service(skill())
            .resolveForBindings(user, List.of(new AgentSkillBindingSpec("skill-a", 0)), true));
        assertEquals(MvpErrorCode.SKILL_PACKAGE_INVALID, error.errorCode());
    }

    private LegacyCompatibleSkillRuntimeService service(SkillDefinitionEntity skill) {
        SkillDefinitionMapper skillMapper = mock(SkillDefinitionMapper.class);
        when(skillMapper.selectOwnedByIds("tenant-a", "owner-a", List.of("skill-a"))).thenReturn(List.of(skill));
        AgentSkillBindingMapper bindingMapper = mock(AgentSkillBindingMapper.class);
        @SuppressWarnings("unchecked") ObjectProvider<ToolBindingPort> provider = mock(ObjectProvider.class);
        SkillPackageHasher hasher = new SkillPackageHasher();
        SkillPackageLoader loader = new SkillPackageLoader(temp.toString(), new SkillManifestParser(),
            new SkillPackageValidator(), hasher);
        return new LegacyCompatibleSkillRuntimeService(skillMapper, bindingMapper, provider, loader, hasher,
            new BrowserSkillExecutionCoordinator(), new ObjectMapper());
    }

    private SkillDefinitionEntity skill() {
        SkillDefinitionEntity skill = new SkillDefinitionEntity();
        skill.setId("skill-a");
        skill.setTenantId("tenant-a");
        skill.setOwnerId("owner-a");
        skill.setName("database name");
        skill.setDescription("database description");
        skill.setInstruction("database instruction");
        skill.setOutputRequirement("database output");
        skill.setStatus("ENABLED");
        skill.setVersion(7L);
        return skill;
    }
}
