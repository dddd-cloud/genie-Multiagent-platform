package com.jd.genie.platform.phase2.configuration.skill.service;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.entity.SkillDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.skill.exception.SkillConfigurationException;
import com.jd.genie.platform.phase2.configuration.skill.mapper.SkillDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.skill.model.SkillStatus;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkillDefinitionService {
    private static final int MAX_NAME_CODE_POINTS = 128;
    private static final int MAX_DESCRIPTION_CODE_POINTS = 1000;
    private static final int MAX_INSTRUCTION_CODE_POINTS = 20_000;
    private static final int MAX_OUTPUT_REQUIREMENT_CODE_POINTS = 5_000;
    private static final int MAX_CAPABILITIES = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final SkillDefinitionMapper skillMapper;
    private final AgentSkillBindingMapper bindingMapper;
    private final ObjectProvider<ToolBindingPort> toolBindingPortProvider;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public SkillResponse createSkill(CurrentUser user, SkillCreateRequest request) {
        requireUser(user);
        NormalizedSkillInput input = normalizeCreate(request);
        validateActiveNameAvailable(user, input.name(), null);
        List<String> capabilityKeys = normalizeCapabilityKeys(input.capabilityKeys());

        Instant now = Instant.now(clock);
        SkillDefinitionEntity entity = new SkillDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(user.tenantId());
        entity.setOwnerId(user.userId());
        entity.setName(input.name());
        entity.setDescription(input.description());
        entity.setInstruction(input.instruction());
        entity.setOutputRequirement(input.outputRequirement());
        entity.setStatus(SkillStatus.ENABLED.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setVersion(0L);
        skillMapper.insert(entity);
        toolBindingPort().replaceSkillBindings(user, entity.getId(), capabilityKeys);
        return toResponse(entity, capabilityKeys);
    }

    @Transactional(readOnly = true)
    public SkillResponse getSkill(CurrentUser user, String skillId) {
        SkillDefinitionEntity entity = requireOwnedSkill(user, skillId);
        return toResponse(entity, skillCapabilityKeys(user, skillId));
    }

    @Transactional(readOnly = true)
    public PageResponse<SkillResponse> listSkills(CurrentUser user, Integer page, Integer pageSize) {
        requireUser(user);
        int validPage = page == null ? 1 : page;
        int validPageSize = pageSize == null ? 20 : pageSize;
        validatePage(validPage, validPageSize);
        List<SkillDefinitionEntity> rows = skillMapper.selectOwnedPage(
            user.tenantId(), user.userId(), validPageSize + 1, (validPage - 1) * validPageSize);
        boolean hasMore = rows.size() > validPageSize;
        List<SkillDefinitionEntity> items = hasMore ? rows.subList(0, validPageSize) : rows;
        return new PageResponse<>(
            items.stream().map(entity -> toResponse(entity, skillCapabilityKeys(user, entity.getId()))).toList(),
            validPage,
            validPageSize,
            hasMore
        );
    }

    @Transactional
    public SkillResponse updateSkill(CurrentUser user, String skillId, SkillUpdateRequest request) {
        requireOwnedSkill(user, skillId);
        Long version = requireVersion(request == null ? null : request.version());
        NormalizedSkillInput input = normalizeUpdate(request);
        validateActiveNameAvailable(user, input.name(), skillId);
        List<String> capabilityKeys = normalizeCapabilityKeys(input.capabilityKeys());

        SkillDefinitionEntity update = new SkillDefinitionEntity();
        update.setId(skillId);
        update.setName(input.name());
        update.setDescription(input.description());
        update.setInstruction(input.instruction());
        update.setOutputRequirement(input.outputRequirement());

        int updated = skillMapper.updateOwnedWithVersion(user.tenantId(), user.userId(), update, version, Instant.now(clock));
        classifyZeroRow(user, skillId, updated);
        toolBindingPort().replaceSkillBindings(user, skillId, capabilityKeys);
        return toResponse(requireOwnedSkill(user, skillId), capabilityKeys);
    }

    @Transactional
    public SkillResponse enableSkill(CurrentUser user, String skillId, Long version) {
        SkillDefinitionEntity entity = requireOwnedSkill(user, skillId);
        if (SkillStatus.ENABLED.name().equals(entity.getStatus())) {
            return toResponse(entity, skillCapabilityKeys(user, skillId));
        }
        int updated = skillMapper.updateStatusOwnedWithVersion(user.tenantId(), user.userId(), skillId,
            requireVersion(version), SkillStatus.ENABLED.name(), Instant.now(clock));
        classifyZeroRow(user, skillId, updated);
        return getSkill(user, skillId);
    }

    @Transactional
    public SkillResponse disableSkill(CurrentUser user, String skillId, Long version) {
        SkillDefinitionEntity entity = requireOwnedSkill(user, skillId);
        if (SkillStatus.DISABLED.name().equals(entity.getStatus())) {
            return toResponse(entity, skillCapabilityKeys(user, skillId));
        }
        int updated = skillMapper.updateStatusOwnedWithVersion(user.tenantId(), user.userId(), skillId,
            requireVersion(version), SkillStatus.DISABLED.name(), Instant.now(clock));
        classifyZeroRow(user, skillId, updated);
        return getSkill(user, skillId);
    }

    @Transactional
    public void deleteSkill(CurrentUser user, String skillId, Long version) {
        requireOwnedSkill(user, skillId);
        if (bindingMapper.countOwnedReferencesBySkill(user.tenantId(), user.userId(), skillId) > 0) {
            throw error(MvpErrorCode.SKILL_IN_USE);
        }
        int deleted = skillMapper.softDeleteOwnedWithVersion(user.tenantId(), user.userId(), skillId,
            requireVersion(version), Instant.now(clock));
        classifyZeroRow(user, skillId, deleted);
        toolBindingPort().removeSkillBindings(user, skillId);
    }

    private SkillDefinitionEntity requireOwnedSkill(CurrentUser user, String skillId) {
        requireUser(user);
        validateId(skillId);
        SkillDefinitionEntity entity = skillMapper.selectOwnedById(user.tenantId(), user.userId(), skillId);
        if (entity == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
        }
        return entity;
    }

    private NormalizedSkillInput normalizeCreate(SkillCreateRequest request) {
        if (request == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        return new NormalizedSkillInput(
            normalizeRequiredText(request.name(), MAX_NAME_CODE_POINTS),
            normalizeRequiredText(request.description(), MAX_DESCRIPTION_CODE_POINTS),
            normalizeRequiredText(request.instruction(), MAX_INSTRUCTION_CODE_POINTS),
            normalizeOptionalText(request.outputRequirement(), MAX_OUTPUT_REQUIREMENT_CODE_POINTS),
            request.capabilityKeys()
        );
    }

    private NormalizedSkillInput normalizeUpdate(SkillUpdateRequest request) {
        if (request == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        return new NormalizedSkillInput(
            normalizeRequiredText(request.name(), MAX_NAME_CODE_POINTS),
            normalizeRequiredText(request.description(), MAX_DESCRIPTION_CODE_POINTS),
            normalizeRequiredText(request.instruction(), MAX_INSTRUCTION_CODE_POINTS),
            normalizeOptionalText(request.outputRequirement(), MAX_OUTPUT_REQUIREMENT_CODE_POINTS),
            request.capabilityKeys()
        );
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

    private void validateActiveNameAvailable(CurrentUser user, String name, String excludeSkillId) {
        if (skillMapper.existsOwnedActiveName(user.tenantId(), user.userId(), name, excludeSkillId)) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private void classifyZeroRow(CurrentUser user, String skillId, int affectedRows) {
        if (affectedRows == 1) {
            return;
        }
        if (skillMapper.selectOwnedById(user.tenantId(), user.userId(), skillId) == null) {
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
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    private List<String> skillCapabilityKeys(CurrentUser user, String skillId) {
        // resolveBindings requires an agentId; skillId is only used to load skill_tool_binding.
        return toolBindingPort()
            .resolveBindings(user, skillId, List.of(skillId))
            .skillCapabilities()
            .getOrDefault(skillId, List.of());
    }

    private ToolBindingPort toolBindingPort() {
        ToolBindingPort port = toolBindingPortProvider.getIfAvailable();
        if (port == null) {
            throw error(MvpErrorCode.TOOL_BINDING_INVALID);
        }
        return port;
    }

    private SkillResponse toResponse(SkillDefinitionEntity entity, List<String> capabilityKeys) {
        return new SkillResponse(entity.getId(), entity.getName(), entity.getDescription(), entity.getInstruction(),
            entity.getOutputRequirement(), entity.getStatus(), entity.getVersion(), capabilityKeys,
            entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private SkillConfigurationException error(MvpErrorCode code) {
        return new SkillConfigurationException(code, code.name());
    }

    private record NormalizedSkillInput(
        String name,
        String description,
        String instruction,
        String outputRequirement,
        List<String> capabilityKeys
    ) {
    }
}
