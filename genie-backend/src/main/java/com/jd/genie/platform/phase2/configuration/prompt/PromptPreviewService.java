package com.jd.genie.platform.phase2.configuration.prompt;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.configuration.model.ModelResolutionResult;
import com.jd.genie.platform.phase2.configuration.skill.entity.SkillDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.skill.model.SkillStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromptPreviewService {
    private static final int MAX_SKILLS = 20;

    private final AgentPromptCompiler promptCompiler;
    private final ModelCatalogService modelCatalogService;
    private final SkillDefinitionMapper skillMapper;

    @Transactional(readOnly = true)
    public PromptPreviewResponse preview(CurrentUser user, PromptPreviewRequest request) {
        requireUser(user);
        if (request == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        List<AgentSkillBindingRequest> bindings = normalizeSkillBindings(request.skills());
        Map<String, SkillDefinitionEntity> skillsById = loadOwnedSkills(user, bindings.stream().map(AgentSkillBindingRequest::skillId).toList());
        List<PromptSkillFragment> fragments = buildEnabledFragments(bindings, skillsById);
        ModelResolutionResult model = modelCatalogService.requireAvailableForStorage(request.modelName());
        PromptCompilationResult compiled = promptCompiler.compile(new PromptCompilationRequest(
            request.promptMode(),
            request.promptConfig(),
            request.systemPrompt(),
            fragments
        ));
        List<PromptSkillFragmentView> views = fragments.stream()
            .map(fragment -> new PromptSkillFragmentView(fragment.skillId(), fragment.skillVersion(), fragment.sortOrder()))
            .toList();
        return new PromptPreviewResponse(compiled.compiledSystemPromptTemplate(), views,
            model.resolvedModelName(), compiled.codePointLength());
    }

    private List<PromptSkillFragment> buildEnabledFragments(List<AgentSkillBindingRequest> bindings,
                                                            Map<String, SkillDefinitionEntity> skillsById) {
        return bindings.stream().map(binding -> {
            SkillDefinitionEntity skill = skillsById.get(binding.skillId());
            if (skill == null) {
                throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
            }
            if (!SkillStatus.ENABLED.name().equals(skill.getStatus())) {
                throw error(MvpErrorCode.AGENT_INVALID_STATE);
            }
            return new PromptSkillFragment(skill.getId(), skill.getVersion(), skill.getName(), binding.sortOrder(),
                skill.getInstruction(), skill.getOutputRequirement());
        }).toList();
    }

    private Map<String, SkillDefinitionEntity> loadOwnedSkills(CurrentUser user, List<String> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        return skillMapper.selectOwnedByIds(user.tenantId(), user.userId(), skillIds).stream()
            .collect(Collectors.toMap(SkillDefinitionEntity::getId, Function.identity()));
    }

    private List<AgentSkillBindingRequest> normalizeSkillBindings(List<AgentSkillBindingRequest> raw) {
        List<AgentSkillBindingRequest> bindings = raw == null ? List.of() : raw;
        if (bindings.size() > MAX_SKILLS) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        LinkedHashSet<String> skillIds = new LinkedHashSet<>();
        LinkedHashSet<Integer> orders = new LinkedHashSet<>();
        for (AgentSkillBindingRequest binding : bindings) {
            if (binding == null || binding.skillId() == null || binding.skillId().isBlank() || binding.sortOrder() == null) {
                throw error(MvpErrorCode.VALIDATION_ERROR);
            }
            String skillId = binding.skillId().trim();
            if (!skillIds.add(skillId) || !orders.add(binding.sortOrder())) {
                throw error(MvpErrorCode.VALIDATION_ERROR);
            }
        }
        List<Integer> sorted = orders.stream().sorted().toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) != i + 1) {
                throw error(MvpErrorCode.VALIDATION_ERROR);
            }
        }
        return bindings.stream()
            .map(binding -> new AgentSkillBindingRequest(binding.skillId().trim(), binding.sortOrder()))
            .sorted(Comparator.comparing(AgentSkillBindingRequest::sortOrder))
            .toList();
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.userId() == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private AgentConfigurationException error(MvpErrorCode code) {
        return new AgentConfigurationException(code, code.name());
    }
}
