package com.jd.genie.platform.phase2.configuration.agent.runtime;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.entity.AgentDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.agent.model.AgentStatus;
import com.jd.genie.platform.phase2.configuration.agent.model.PromptMode;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.configuration.model.ModelResolutionResult;
import com.jd.genie.platform.phase2.configuration.prompt.AgentPromptCompiler;
import com.jd.genie.platform.phase2.configuration.prompt.PromptCompilationRequest;
import com.jd.genie.platform.phase2.configuration.prompt.PromptCompilationResult;
import com.jd.genie.platform.phase2.configuration.prompt.PromptSkillFragment;
import com.jd.genie.platform.phase2.configuration.prompt.PromptValidationException;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.dto.SkillRuntimePackage;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.SkillRuntimePort;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentRuntimeCatalogService implements AgentRuntimeCatalogPort {
    private static final int MAX_ALLOWED_AGENT_IDS = 20;

    private final AgentDefinitionMapper agentMapper;
    private final SkillRuntimePort skillRuntimePort;
    private final AgentPromptCompiler promptCompiler;
    private final ModelCatalogService modelCatalogService;
    private final ObjectProvider<ToolBindingPort> toolBindingPortProvider;

    @Override
    @Transactional(readOnly = true)
    public List<AgentCapabilitySummary> listOnlineCandidates(CurrentUser user, List<String> allowedAgentIds) {
        requireUser(user);
        if (allowedAgentIds == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        List<AgentDefinitionEntity> rows;
        if (allowedAgentIds.isEmpty()) {
            rows = agentMapper.selectOwnedOnlineCandidates(user.tenantId(), user.userId());
        } else {
            List<String> normalizedIds = normalizeAllowedAgentIds(allowedAgentIds);
            if (normalizedIds.isEmpty()) {
                return List.of();
            }
            rows = agentMapper.selectOwnedOnlineCandidatesByIds(user.tenantId(), user.userId(), normalizedIds);
        }
        return rows.stream()
            .map(entity -> new AgentCapabilitySummary(entity.getId(), entity.getVersion(), entity.getName(), entity.getDescription()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AgentRuntimeProfile loadOnlineProfile(CurrentUser user, String agentId) {
        requireUser(user);
        validateId(agentId);
        AgentDefinitionEntity agent = agentMapper.selectOwnedById(user.tenantId(), user.userId(), agentId.trim());
        if (agent == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!AgentStatus.ONLINE.name().equals(agent.getStatus())) {
            throw error(MvpErrorCode.AGENT_OFFLINE);
        }

        // Preserve V1/004 runtime behavior: disabled bound skills are ignored at profile load.
        // Online transition still fail-closes via resolveForBindings(..., true).
        List<SkillRuntimePackage> enabledSkills = skillRuntimePort.resolveForAgent(user, agent.getId(), false);
        PromptCompilationResult prompt = recompilePrompt(agent, enabledSkills);
        ModelResolutionResult model = modelCatalogService.resolveForStorage(null);
        String resolvedModelName = model.resolvedModelName();
        if (resolvedModelName == null || resolvedModelName.isBlank()) {
            resolvedModelName = "default";
        }

        List<String> enabledSkillIds = enabledSkills.stream().map(SkillRuntimePackage::skillId).toList();
        ToolBindingView bindings = toolBindingPort().resolveBindings(user, agent.getId(), enabledSkillIds);
        if (bindings == null || !bindings.invalidCapabilities().isEmpty()) {
            throw error(MvpErrorCode.AGENT_INVALID_STATE);
        }
        List<String> capabilityKeys = mergeCapabilities(bindings, enabledSkills);
        List<AgentRuntimeSkill> skills = enabledSkills.stream()
            .map(skill -> new AgentRuntimeSkill(
                skill.skillId(),
                skill.skillVersion(),
                skill.sortOrder(),
                skill.instructionMarkdown(),
                skill.outputRequirement(),
                skill.skillKey(),
                skill.packageMode() == null ? null : skill.packageMode().name(),
                skill.packageVersion(),
                skill.packageHash(),
                skill.capabilityKeys(),
                skill.entrypoints()
            ))
            .toList();

        return new AgentRuntimeProfile(
            agent.getId(),
            agent.getVersion(),
            agent.getName(),
            agent.getDescription(),
            prompt.compiledSystemPromptTemplate(),
            resolvedModelName,
            skills,
            capabilityKeys
        );
    }

    private List<String> normalizeAllowedAgentIds(List<String> allowedAgentIds) {
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String agentId : allowedAgentIds) {
            if (agentId == null || agentId.isBlank()) {
                continue;
            }
            deduplicated.add(agentId.trim());
        }
        if (deduplicated.size() > MAX_ALLOWED_AGENT_IDS) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        return List.copyOf(deduplicated);
    }

    private PromptCompilationResult recompilePrompt(AgentDefinitionEntity agent, List<SkillRuntimePackage> skills) {
        List<PromptSkillFragment> fragments = skills.stream()
            .map(skill -> new PromptSkillFragment(
                skill.skillId(),
                skill.skillVersion(),
                skill.name(),
                skill.sortOrder(),
                skill.instructionMarkdown(),
                skill.outputRequirement()
            ))
            .toList();
        try {
            String rawPrompt = PromptMode.RAW.name().equals(agent.getPromptMode())
                ? agent.getSystemPrompt()
                : null;
            return promptCompiler.compile(new PromptCompilationRequest(
                agent.getPromptMode(),
                agent.getPromptConfig(),
                rawPrompt,
                fragments
            ));
        } catch (PromptValidationException ex) {
            throw error(MvpErrorCode.AGENT_INVALID_STATE);
        }
    }

    private List<String> mergeCapabilities(ToolBindingView bindings, List<SkillRuntimePackage> enabledSkills) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addCapabilities(merged, bindings.directCapabilities());
        for (SkillRuntimePackage skill : enabledSkills) {
            addCapabilities(merged, bindings.skillCapabilities().get(skill.skillId()));
            addCapabilities(merged, skill.capabilityKeys());
        }
        return List.copyOf(merged);
    }

    private void addCapabilities(LinkedHashSet<String> merged, List<String> capabilityKeys) {
        if (capabilityKeys == null) {
            return;
        }
        for (String capabilityKey : capabilityKeys) {
            CapabilityKeys.requireValid(capabilityKey);
            merged.add(capabilityKey);
        }
    }

    private ToolBindingPort toolBindingPort() {
        ToolBindingPort port = toolBindingPortProvider.getIfAvailable();
        if (port == null) {
            throw error(MvpErrorCode.TOOL_BINDING_INVALID);
        }
        return port;
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.tenantId().isBlank()
            || user.userId() == null || user.userId().isBlank()) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private Phase2ContractException error(MvpErrorCode code) {
        return new Phase2ContractException(code, code.name());
    }
}
