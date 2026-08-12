package com.jd.genie.platform.phase2.skillruntime;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.skill.binding.AgentRuntimeSkillSnapshot;
import com.jd.genie.platform.phase2.configuration.skill.binding.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2.configuration.skill.entity.SkillDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.skill.model.SkillStatus;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingSpec;
import com.jd.genie.platform.phase2contract.dto.SkillExecutionCommand;
import com.jd.genie.platform.phase2contract.dto.SkillExecutionResult;
import com.jd.genie.platform.phase2contract.dto.SkillResource;
import com.jd.genie.platform.phase2contract.dto.SkillRuntimePackage;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.enums.SkillPackageMode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.SkillRuntimePort;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.LoadedSkillPackage;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves owner-scoped filesystem Skill packages when present and otherwise
 * preserves the C0 LEGACY_SYNTHETIC snapshot. Stage1 does not execute scripts.
 */
@Service
@RequiredArgsConstructor
public class LegacyCompatibleSkillRuntimeService implements SkillRuntimePort {
    private final SkillDefinitionMapper skillMapper;
    private final AgentSkillBindingMapper bindingMapper;
    private final ObjectProvider<ToolBindingPort> toolBindingPortProvider;
    private final SkillPackageLoader packageLoader;
    private final SkillPackageHasher packageHasher;

    @Override
    @Transactional(readOnly = true)
    public List<SkillRuntimePackage> resolveForBindings(
        CurrentUser user,
        List<AgentSkillBindingSpec> bindings,
        boolean requireEnabled
    ) {
        requireUser(user);
        List<AgentSkillBindingSpec> normalized = normalizeBindings(bindings);
        if (normalized.isEmpty()) {
            return List.of();
        }
        Map<String, SkillDefinitionEntity> skills = loadOwnedSkills(
            user,
            normalized.stream().map(AgentSkillBindingSpec::skillId).toList()
        );
        List<SkillRuntimePackage> packages = new ArrayList<>();
        for (AgentSkillBindingSpec binding : normalized) {
            SkillDefinitionEntity skill = skills.get(binding.skillId());
            if (skill == null) {
                throw new Phase2ContractException(MvpErrorCode.RESOURCE_NOT_FOUND, "skill not found");
            }
            if (!SkillStatus.ENABLED.name().equals(skill.getStatus())) {
                if (requireEnabled) {
                    throw new Phase2ContractException(MvpErrorCode.AGENT_INVALID_STATE, "skill not enabled");
                }
                continue;
            }
            packages.add(toRuntimePackage(user, skill, binding.sortOrder(), List.of()));
        }
        return List.copyOf(packages);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillRuntimePackage> resolveForAgent(CurrentUser user, String agentId, boolean requireEnabled) {
        requireUser(user);
        if (agentId == null || agentId.isBlank()) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "agentId required");
        }
        List<AgentRuntimeSkillSnapshot> snapshots = bindingMapper
            .selectOwnedRuntimeSkillSnapshots(user.tenantId(), user.userId(), agentId.trim());
        List<String> skillIds = snapshots.stream().map(AgentRuntimeSkillSnapshot::getSkillId).toList();
        ToolBindingView bindings = toolBindingPort().resolveBindings(user, agentId.trim(), skillIds);
        Map<String, List<String>> skillCapabilities = bindings == null
            ? Map.of()
            : bindings.skillCapabilities();

        List<SkillRuntimePackage> packages = new ArrayList<>();
        for (AgentRuntimeSkillSnapshot snapshot : snapshots) {
            if (!SkillStatus.ENABLED.name().equals(snapshot.getStatus())) {
                if (requireEnabled) {
                    throw new Phase2ContractException(MvpErrorCode.AGENT_INVALID_STATE, "skill not enabled");
                }
                continue;
            }
            packages.add(toRuntimePackage(user, snapshot, skillCapabilities.getOrDefault(snapshot.getSkillId(), List.of())));
        }
        return List.copyOf(packages);
    }

    @Override
    public SkillResource readResource(CurrentUser user, String skillId, String relativePath) {
        requireUser(user);
        if (skillId == null || skillId.isBlank() || relativePath == null || relativePath.isBlank()) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "skillId and relativePath required");
        }
        SkillDefinitionEntity skill = skillMapper.selectOwnedByIds(user.tenantId(), user.userId(), List.of(skillId)).stream()
            .findFirst().orElseThrow(() -> new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "skill not found"));
        if (packageLoader.load(user, skill.getId()).isEmpty())
            throw new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "legacy skill has no package resources");
        return packageLoader.readResource(user, skill.getId(), relativePath);
    }

    @Override
    public List<BaseTool> buildRuntimeTools(CurrentUser user, AgentRuntimeProfile profile, AgentContext context) {
        // C0: no script tools from LEGACY_SYNTHETIC packages.
        return List.of();
    }

    @Override
    public SkillExecutionResult executeEntrypoint(CurrentUser user, SkillExecutionCommand command) {
        requireUser(user);
        return new SkillExecutionResult(
            false,
            "",
            "",
            null,
            MvpErrorCode.SKILL_ENTRYPOINT_NOT_FOUND,
            "legacy synthetic skill does not execute entrypoints"
        );
    }

    private SkillRuntimePackage toLegacyPackage(SkillDefinitionEntity skill, int sortOrder, List<String> capabilityKeys) {
        String instruction = skill.getInstruction() == null ? "" : skill.getInstruction();
        long version = skill.getVersion() == null ? 0L : skill.getVersion();
        String hash = packageHasher.legacyHash(skill.getId(), version, skill.getName(), skill.getDescription(),
            instruction, skill.getOutputRequirement(), capabilityKeys);
        return new SkillRuntimePackage(
            skill.getId(),
            skill.getVersion() == null ? 0L : skill.getVersion(),
            sortOrder,
            skill.getStatus(),
            skill.getName(),
            skill.getName(),
            skill.getDescription(),
            SkillPackageMode.LEGACY_SYNTHETIC,
            String.valueOf(skill.getVersion() == null ? 0L : skill.getVersion()),
            hash,
            instruction,
            skill.getOutputRequirement(),
            List.of(),
            List.of(),
            capabilityKeys
        );
    }

    private SkillRuntimePackage toLegacyPackage(AgentRuntimeSkillSnapshot snapshot, List<String> capabilityKeys) {
        String instruction = snapshot.getInstruction() == null ? "" : snapshot.getInstruction();
        long version = snapshot.getSkillVersion() == null ? 0L : snapshot.getSkillVersion();
        String hash = packageHasher.legacyHash(snapshot.getSkillId(), version, snapshot.getSkillName(), snapshot.getDescription(),
            instruction, snapshot.getOutputRequirement(), capabilityKeys);
        return new SkillRuntimePackage(
            snapshot.getSkillId(),
            snapshot.getSkillVersion() == null ? 0L : snapshot.getSkillVersion(),
            snapshot.getSortOrder() == null ? 0 : snapshot.getSortOrder(),
            snapshot.getStatus(),
            snapshot.getSkillName(),
            snapshot.getSkillName(),
            snapshot.getDescription(),
            SkillPackageMode.LEGACY_SYNTHETIC,
            String.valueOf(snapshot.getSkillVersion() == null ? 0L : snapshot.getSkillVersion()),
            hash,
            instruction,
            snapshot.getOutputRequirement(),
            List.of(),
            List.of(),
            capabilityKeys
        );
    }

    private SkillRuntimePackage toRuntimePackage(CurrentUser user, SkillDefinitionEntity skill, int sortOrder,
                                                 List<String> capabilityKeys) {
        return packageLoader.load(user, skill.getId())
            .map(loaded -> toFilesystemPackage(skill.getId(), skill.getVersion() == null ? 0L : skill.getVersion(),
                sortOrder, skill.getStatus(), skill.getOutputRequirement(), capabilityKeys, loaded))
            .orElseGet(() -> toLegacyPackage(skill, sortOrder, capabilityKeys));
    }

    private SkillRuntimePackage toRuntimePackage(CurrentUser user, AgentRuntimeSkillSnapshot skill,
                                                 List<String> capabilityKeys) {
        return packageLoader.load(user, skill.getSkillId())
            .map(loaded -> toFilesystemPackage(skill.getSkillId(), skill.getSkillVersion() == null ? 0L : skill.getSkillVersion(),
                skill.getSortOrder() == null ? 0 : skill.getSortOrder(), skill.getStatus(), skill.getOutputRequirement(),
                capabilityKeys, loaded))
            .orElseGet(() -> toLegacyPackage(skill, capabilityKeys));
    }

    private SkillRuntimePackage toFilesystemPackage(String skillId, long skillVersion, int sortOrder, String status,
                                                    String outputRequirement, List<String> capabilityKeys,
                                                    LoadedSkillPackage loaded) {
        return new SkillRuntimePackage(skillId, skillVersion, sortOrder, status, loaded.name(), loaded.name(),
            loaded.description(), SkillPackageMode.FILESYSTEM, loaded.packageVersion(), loaded.packageHash(),
            loaded.instructionMarkdown(), outputRequirement, loaded.resourceManifest(), loaded.entrypoints(), capabilityKeys);
    }

    private List<AgentSkillBindingSpec> normalizeBindings(List<AgentSkillBindingSpec> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
            .sorted(Comparator.comparingInt(AgentSkillBindingSpec::sortOrder))
            .toList();
    }

    private Map<String, SkillDefinitionEntity> loadOwnedSkills(CurrentUser user, List<String> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        return skillMapper.selectOwnedByIds(user.tenantId(), user.userId(), skillIds).stream()
            .collect(Collectors.toMap(SkillDefinitionEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private ToolBindingPort toolBindingPort() {
        ToolBindingPort port = toolBindingPortProvider.getIfAvailable();
        if (port == null) {
            throw new Phase2ContractException(MvpErrorCode.TOOL_BINDING_INVALID, "tool binding port unavailable");
        }
        return port;
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.userId() == null) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "user required");
        }
    }
}
