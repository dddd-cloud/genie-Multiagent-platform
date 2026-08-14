package com.jd.genie.platform.phase2.configuration.skill.service;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.exception.SkillConfigurationException;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillManifest;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageArchiveReader;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLoader;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SkillPackageImportService {
    private static final int MAX_NAME_CODE_POINTS = 128;
    private static final int MAX_DESCRIPTION_CODE_POINTS = 1000;
    private static final int MAX_INSTRUCTION_CODE_POINTS = 20_000;

    private final SkillDefinitionService skillService;
    private final SkillDefinitionMapper skillMapper;
    private final SkillPackageLoader packageLoader;
    private final SkillPackageArchiveReader archiveReader;

    @Transactional
    public SkillResponse importPackage(CurrentUser user, byte[] zipBytes, String skillId) {
        requireUser(user);
        SkillPackageArchiveReader.ExtractedPackage extracted = archiveReader.read(zipBytes);
        SkillManifest manifest = extracted.manifest();
        String name = uniqueName(user, requireText(manifest.name(), MAX_NAME_CODE_POINTS, true), blankToNull(skillId));
        String description = requireText(manifest.description(), MAX_DESCRIPTION_CODE_POINTS, false);
        String instruction = requireText(manifest.instructionMarkdown(), MAX_INSTRUCTION_CODE_POINTS, false);

        if (blankToNull(skillId) != null) {
            SkillResponse existing = skillService.getSkill(user, skillId);
            writeFiles(user, existing.id(), extracted.files());
            return skillService.updateSkill(user, existing.id(), new SkillUpdateRequest(
                existing.version(),
                name,
                description,
                instruction,
                existing.outputRequirement(),
                existing.capabilityKeys()
            ));
        }

        SkillResponse created = skillService.createSkill(user, new SkillCreateRequest(
            name, description, instruction, "", List.of()));
        try {
            writeFiles(user, created.id(), extracted.files());
        } catch (RuntimeException e) {
            try {
                skillService.deleteSkill(user, created.id(), created.version());
            } catch (RuntimeException ignored) {
                // keep original write failure
            }
            throw e;
        }
        return skillService.getSkill(user, created.id());
    }

    private void writeFiles(CurrentUser user, String skillId, Map<String, byte[]> files) {
        Path target = packageLoader.ownedPackageRoot(user, skillId);
        Path parent = target.getParent();
        if (parent == null) {
            throw invalid("skill package root escaped owner directory");
        }
        Path staging = parent.resolve(skillId + ".staging-" + UUID.randomUUID());
        try {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                Path dest = staging.resolve(entry.getKey()).normalize();
                if (!dest.startsWith(staging)) {
                    throw invalid("path traversal rejected");
                }
                Path destParent = dest.getParent();
                if (destParent != null) {
                    Files.createDirectories(destParent);
                }
                Files.write(dest, entry.getValue());
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                deleteRecursively(target);
            }
            Files.move(staging, target);
        } catch (Phase2ContractException e) {
            deleteQuietly(staging);
            throw e;
        } catch (IOException e) {
            deleteQuietly(staging);
            throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "cannot write skill package", e);
        }
    }

    private String uniqueName(CurrentUser user, String base, String excludeSkillId) {
        if (!skillMapper.existsOwnedActiveName(user.tenantId(), user.userId(), base, excludeSkillId)) {
            return base;
        }
        for (int i = 2; i <= 99; i++) {
            String suffix = " (" + i + ")";
            String candidate = trimCodePoints(base, MAX_NAME_CODE_POINTS - suffix.length()) + suffix;
            if (!skillMapper.existsOwnedActiveName(user.tenantId(), user.userId(), candidate, excludeSkillId)) {
                return candidate;
            }
        }
        String suffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        return trimCodePoints(base, MAX_NAME_CODE_POINTS - suffix.length()) + suffix;
    }

    private String requireText(String value, int maxCodePoints, boolean truncate) {
        if (value == null) {
            throw invalid("SKILL.md missing required field");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw invalid("SKILL.md missing required field");
        }
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= maxCodePoints) {
            return normalized;
        }
        if (!truncate) {
            throw invalid("SKILL.md field too long");
        }
        return trimCodePoints(normalized, maxCodePoints);
    }

    private String trimCodePoints(String value, int maxCodePoints) {
        if (maxCodePoints < 1) {
            return "";
        }
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void deleteQuietly(Path dir) {
        try {
            deleteRecursively(dir);
        } catch (IOException ignored) {
            // best-effort cleanup of staging
        }
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.userId() == null) {
            throw new SkillConfigurationException(MvpErrorCode.VALIDATION_ERROR, MvpErrorCode.VALIDATION_ERROR.name());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Phase2ContractException invalid(String message) {
        return new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, message);
    }
}
