package com.jd.genie.platform.phase2.configuration.agent.service;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.entity.AgentDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.agent.entity.AgentSkillBindingEntity;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2.configuration.agent.model.AgentStatus;
import com.jd.genie.platform.phase2.configuration.agent.model.PromptMode;
import com.jd.genie.platform.phase2.configuration.skill.entity.SkillDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.skill.model.SkillStatus;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentDefinitionService {
    private static final int MAX_NAME_CODE_POINTS = 128;
    private static final int MAX_DESCRIPTION_CODE_POINTS = 1000;
    private static final int MAX_PROMPT_CODE_POINTS = 20_000;
    private static final int MAX_SKILLS = 20;
    private static final int MAX_CAPABILITIES = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final AgentDefinitionMapper agentMapper;
    private final AgentSkillBindingMapper bindingMapper;
    private final SkillDefinitionMapper skillMapper;
    private final ObjectProvider<ToolBindingPort> toolBindingPortProvider;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public AgentResponse createAgent(CurrentUser user, AgentCreateRequest request) {
        requireUser(user);
        NormalizedAgentInput input = normalizeCreate(request);
        validateActiveNameAvailable(user, input.name(), null);
        List<AgentSkillBindingRequest> skills = normalizeSkillBindings(input.skills());
        List<String> capabilityKeys = normalizeCapabilityKeys(input.capabilityKeys());
        Map<String, SkillDefinitionEntity> ownedSkills = loadOwnedSkills(user, skills.stream().map(AgentSkillBindingRequest::skillId).toList());

        Instant now = Instant.now(clock);
        AgentDefinitionEntity entity = new AgentDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(user.tenantId());
        entity.setOwnerId(user.userId());
        entity.setName(input.name());
        entity.setDescription(input.description());
        entity.setPromptMode(input.promptMode());
        entity.setPromptConfig(input.promptConfig());
        entity.setSystemPrompt(input.systemPrompt());
        entity.setModelName(input.modelName());
        entity.setStatus(AgentStatus.DRAFT.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setVersion(0L);
        agentMapper.insert(entity);
        replaceSkillBindings(user, entity.getId(), skills, now, ownedSkills, false);
        toolBindingPort().replaceAgentBindings(user, entity.getId(), capabilityKeys);
        return toResponse(entity, skills.stream().map(AgentSkillBindingRequest::skillId).toList(), capabilityKeys);
    }

    @Transactional(readOnly = true)
    public AgentResponse getAgent(CurrentUser user, String agentId) {
        AgentDefinitionEntity entity = requireOwnedAgent(user, agentId);
        List<String> skillIds = bindingMapper.selectOwnedBindingsByAgent(user.tenantId(), user.userId(), agentId)
            .stream().map(AgentSkillBindingEntity::getSkillId).toList();
        return toResponse(entity, skillIds, List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentResponse> listAgents(CurrentUser user, Integer page, Integer pageSize) {
        requireUser(user);
        int validPage = page == null ? 1 : page;
        int validPageSize = pageSize == null ? 20 : pageSize;
        validatePage(validPage, validPageSize);
        List<AgentDefinitionEntity> rows = agentMapper.selectOwnedPage(
            user.tenantId(), user.userId(), validPageSize + 1, (validPage - 1) * validPageSize);
        boolean hasMore = rows.size() > validPageSize;
        List<AgentDefinitionEntity> items = hasMore ? rows.subList(0, validPageSize) : rows;
        return new PageResponse<>(items.stream().map(entity -> toResponse(entity, List.of(), List.of())).toList(),
            validPage, validPageSize, hasMore);
    }

    @Transactional
    public AgentResponse updateAgent(CurrentUser user, String agentId, AgentUpdateRequest request) {
        AgentDefinitionEntity current = requireOwnedAgent(user, agentId);
        Long version = requireVersion(request == null ? null : request.version());
        NormalizedAgentInput input = normalizeUpdate(request);
        validateActiveNameAvailable(user, input.name(), agentId);
        List<AgentSkillBindingRequest> skills = normalizeSkillBindings(input.skills());
        List<String> capabilityKeys = normalizeCapabilityKeys(input.capabilityKeys());
        Map<String, SkillDefinitionEntity> ownedSkills = loadOwnedSkills(user, skills.stream().map(AgentSkillBindingRequest::skillId).toList());

        AgentDefinitionEntity update = new AgentDefinitionEntity();
        update.setId(agentId);
        update.setName(input.name());
        update.setDescription(input.description());
        update.setPromptMode(input.promptMode());
        update.setPromptConfig(input.promptConfig());
        update.setSystemPrompt(input.systemPrompt());
        update.setModelName(input.modelName());

        Instant now = Instant.now(clock);
        int updated = agentMapper.updateOwnedWithVersion(user.tenantId(), user.userId(), update, version, now);
        classifyZeroRow(user, agentId, updated);
        bindingMapper.deleteOwnedBindingsByAgent(user.tenantId(), user.userId(), agentId);
        replaceSkillBindings(user, agentId, skills, now, ownedSkills, false);
        toolBindingPort().replaceAgentBindings(user, agentId, capabilityKeys);
        current = requireOwnedAgent(user, agentId);
        return toResponse(current, skills.stream().map(AgentSkillBindingRequest::skillId).toList(), capabilityKeys);
    }

    @Transactional
    public AgentResponse onlineAgent(CurrentUser user, String agentId, Long version) {
        AgentDefinitionEntity entity = requireOwnedAgent(user, agentId);
        if (AgentStatus.ONLINE.name().equals(entity.getStatus())) {
            throw error(MvpErrorCode.AGENT_INVALID_STATE);
        }
        validateOnlineCandidate(user, entity);
        ToolBindingView view = toolBindingPort().resolveBindings(user, agentId, enabledSkillIds(user, agentId));
        if (!view.invalidCapabilities().isEmpty()) {
            throw error(MvpErrorCode.TOOL_BINDING_INVALID);
        }
        Instant now = Instant.now(clock);
        int updated = agentMapper.updateStatusOwnedWithVersion(user.tenantId(), user.userId(), agentId,
            requireVersion(version), AgentStatus.ONLINE.name(), now);
        classifyZeroRow(user, agentId, updated);
        return getAgent(user, agentId);
    }

    @Transactional
    public AgentResponse offlineAgent(CurrentUser user, String agentId, Long version) {
        AgentDefinitionEntity entity = requireOwnedAgent(user, agentId);
        if (AgentStatus.OFFLINE.name().equals(entity.getStatus())) {
            return toResponse(entity, enabledSkillIds(user, agentId), List.of());
        }
        Instant now = Instant.now(clock);
        int updated = agentMapper.updateStatusOwnedWithVersion(user.tenantId(), user.userId(), agentId,
            requireVersion(version), AgentStatus.OFFLINE.name(), now);
        classifyZeroRow(user, agentId, updated);
        return getAgent(user, agentId);
    }

    @Transactional
    public void deleteAgent(CurrentUser user, String agentId, Long version) {
        AgentDefinitionEntity entity = requireOwnedAgent(user, agentId);
        if (AgentStatus.ONLINE.name().equals(entity.getStatus())) {
            throw error(MvpErrorCode.AGENT_MUST_BE_OFFLINE);
        }
        int deleted = agentMapper.softDeleteOwnedWithVersion(user.tenantId(), user.userId(), agentId,
            requireVersion(version), Instant.now(clock));
        classifyZeroRow(user, agentId, deleted);
        bindingMapper.deleteOwnedBindingsByAgent(user.tenantId(), user.userId(), agentId);
        toolBindingPort().removeAgentBindings(user, agentId);
    }

    private void validateOnlineCandidate(CurrentUser user, AgentDefinitionEntity entity) {
        validateText(entity.getName(), MAX_NAME_CODE_POINTS);
        validateText(entity.getDescription(), MAX_DESCRIPTION_CODE_POINTS);
        validateText(entity.getSystemPrompt(), MAX_PROMPT_CODE_POINTS);
        if (entity.getModelName() != null && entity.getModelName().isBlank()) {
            throw error(MvpErrorCode.MODEL_NOT_AVAILABLE);
        }
        List<AgentSkillBindingEntity> bindings = bindingMapper.selectOwnedBindingsByAgent(user.tenantId(), user.userId(), entity.getId());
        Map<String, SkillDefinitionEntity> skills = loadOwnedSkills(user, bindings.stream().map(AgentSkillBindingEntity::getSkillId).toList());
        for (AgentSkillBindingEntity binding : bindings) {
            SkillDefinitionEntity skill = skills.get(binding.getSkillId());
            if (skill == null) {
                throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
            }
            if (!SkillStatus.ENABLED.name().equals(skill.getStatus())) {
                throw error(MvpErrorCode.AGENT_INVALID_STATE);
            }
        }
    }

    private List<String> enabledSkillIds(CurrentUser user, String agentId) {
        return bindingMapper.selectOwnedBindingsByAgent(user.tenantId(), user.userId(), agentId)
            .stream().map(AgentSkillBindingEntity::getSkillId).toList();
    }

    private void replaceSkillBindings(CurrentUser user, String agentId, List<AgentSkillBindingRequest> skills,
                                      Instant now, Map<String, SkillDefinitionEntity> ownedSkills,
                                      boolean requireEnabled) {
        for (AgentSkillBindingRequest skill : skills) {
            SkillDefinitionEntity entity = ownedSkills.get(skill.skillId());
            if (entity == null) {
                throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
            }
            if (requireEnabled && !SkillStatus.ENABLED.name().equals(entity.getStatus())) {
                throw error(MvpErrorCode.AGENT_INVALID_STATE);
            }
        }
        if (skills.isEmpty()) {
            return;
        }
        List<AgentSkillBindingEntity> rows = skills.stream().map(skill -> {
            AgentSkillBindingEntity row = new AgentSkillBindingEntity();
            row.setTenantId(user.tenantId());
            row.setOwnerId(user.userId());
            row.setAgentId(agentId);
            row.setSkillId(skill.skillId());
            row.setSortOrder(skill.sortOrder());
            row.setCreatedAt(now);
            return row;
        }).toList();
        bindingMapper.batchInsert(rows);
    }

    private Map<String, SkillDefinitionEntity> loadOwnedSkills(CurrentUser user, List<String> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        return skillMapper.selectOwnedByIds(user.tenantId(), user.userId(), skillIds).stream()
            .collect(Collectors.toMap(SkillDefinitionEntity::getId, Function.identity()));
    }

    private AgentDefinitionEntity requireOwnedAgent(CurrentUser user, String agentId) {
        requireUser(user);
        validateId(agentId);
        AgentDefinitionEntity entity = agentMapper.selectOwnedById(user.tenantId(), user.userId(), agentId);
        if (entity == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
        }
        return entity;
    }

    private NormalizedAgentInput normalizeCreate(AgentCreateRequest request) {
        if (request == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        return new NormalizedAgentInput(
            normalizeRequiredText(request.name(), MAX_NAME_CODE_POINTS),
            normalizeRequiredText(request.description(), MAX_DESCRIPTION_CODE_POINTS),
            normalizePromptMode(request.promptMode()),
            normalizeOptionalText(request.promptConfig(), MAX_PROMPT_CODE_POINTS),
            normalizeRequiredText(request.systemPrompt(), MAX_PROMPT_CODE_POINTS),
            normalizeOptionalText(request.modelName(), 128),
            request.skills(),
            request.capabilityKeys()
        );
    }

    private NormalizedAgentInput normalizeUpdate(AgentUpdateRequest request) {
        if (request == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        return new NormalizedAgentInput(
            normalizeRequiredText(request.name(), MAX_NAME_CODE_POINTS),
            normalizeRequiredText(request.description(), MAX_DESCRIPTION_CODE_POINTS),
            normalizePromptMode(request.promptMode()),
            normalizeOptionalText(request.promptConfig(), MAX_PROMPT_CODE_POINTS),
            normalizeRequiredText(request.systemPrompt(), MAX_PROMPT_CODE_POINTS),
            normalizeOptionalText(request.modelName(), 128),
            request.skills(),
            request.capabilityKeys()
        );
    }

    private String normalizePromptMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return PromptMode.STRUCTURED.name();
        }
        try {
            return PromptMode.valueOf(raw.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw error(MvpErrorCode.PROMPT_INVALID);
        }
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

    private List<String> normalizeCapabilityKeys(List<String> raw) {
        List<String> keys = raw == null ? List.of() : raw;
        if (keys.size() > MAX_CAPABILITIES) {
            throw error(MvpErrorCode.TOOL_BINDING_INVALID);
        }
        LinkedHashSet<String> validated = new LinkedHashSet<>();
        for (String key : keys) {
            CapabilityKeys.requireValid(key);
            validated.add(key);
        }
        return List.copyOf(validated);
    }

    private void validateActiveNameAvailable(CurrentUser user, String name, String excludeAgentId) {
        if (agentMapper.existsOwnedActiveName(user.tenantId(), user.userId(), name, excludeAgentId)) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private void classifyZeroRow(CurrentUser user, String agentId, int affectedRows) {
        if (affectedRows == 1) {
            return;
        }
        if (agentMapper.selectOwnedById(user.tenantId(), user.userId(), agentId) == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
        }
        throw error(MvpErrorCode.VERSION_CONFLICT);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.userId() == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private Long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        return version;
    }

    private String normalizeRequiredText(String value, int maxCodePoints) {
        String normalized = normalizeOptionalText(value, maxCodePoints);
        if (normalized == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxCodePoints) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        validateText(normalized, maxCodePoints);
        return normalized;
    }

    private void validateText(String value, int maxCodePoints) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > maxCodePoints) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private ToolBindingPort toolBindingPort() {
        ToolBindingPort port = toolBindingPortProvider.getIfAvailable();
        if (port == null) {
            throw error(MvpErrorCode.TOOL_BINDING_INVALID);
        }
        return port;
    }

    private AgentResponse toResponse(AgentDefinitionEntity entity, List<String> skillIds, List<String> capabilityKeys) {
        return new AgentResponse(entity.getId(), entity.getName(), entity.getDescription(), entity.getPromptMode(),
            entity.getPromptConfig(), entity.getSystemPrompt(), entity.getModelName(), entity.getStatus(), entity.getVersion(),
            skillIds, capabilityKeys, entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private AgentConfigurationException error(MvpErrorCode code) {
        return new AgentConfigurationException(code, code.name());
    }

    private record NormalizedAgentInput(
        String name,
        String description,
        String promptMode,
        String promptConfig,
        String systemPrompt,
        String modelName,
        List<AgentSkillBindingRequest> skills,
        List<String> capabilityKeys
    ) {
    }
}
