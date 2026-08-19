package com.jd.genie.platform.phase2.configuration.team.service;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.agent.entity.AgentDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.agent.model.AgentStatus;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamCreateRequest;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamUpdateRequest;
import com.jd.genie.platform.phase2.configuration.team.entity.AgentTeamEntity;
import com.jd.genie.platform.phase2.configuration.team.entity.AgentTeamMemberEntity;
import com.jd.genie.platform.phase2.configuration.team.exception.TeamConfigurationException;
import com.jd.genie.platform.phase2.configuration.team.mapper.AgentTeamMapper;
import com.jd.genie.platform.phase2.configuration.team.mapper.AgentTeamMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentTeamService {
    private static final int MAX_NAME_CODE_POINTS = 128;
    private static final int MAX_DESCRIPTION_CODE_POINTS = 1000;
    private static final int MAX_MEMBERS = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AgentTeamMapper teamMapper;
    private final AgentTeamMemberMapper memberMapper;
    private final AgentDefinitionMapper agentMapper;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public TeamResponse createTeam(CurrentUser user, TeamCreateRequest request) {
        requireUser(user);
        if (request == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        NormalizedTeamInput input = normalize(
            request.name(), request.description(), request.masterAgentId(), request.memberAgentIds());
        validateActiveNameAvailable(user, input.name(), null);
        AgentDefinitionEntity master = requireOnlineMaster(user, input.masterAgentId());
        requireOwnedMembers(user, input.memberAgentIds());

        Instant now = Instant.now(clock);
        AgentTeamEntity entity = new AgentTeamEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(user.tenantId());
        entity.setOwnerId(user.userId());
        entity.setName(input.name());
        entity.setDescription(input.description());
        entity.setMasterAgentId(input.masterAgentId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setVersion(0L);
        teamMapper.insert(entity);
        replaceMembers(user, entity.getId(), input.memberAgentIds(), now);
        return toResponse(entity, master.getName(), input.memberAgentIds());
    }

    @Transactional(readOnly = true)
    public String nextAvailableName(CurrentUser user, String desiredName) {
        requireUser(user);
        String base = desiredName == null ? "" : desiredName.trim();
        if (base.isEmpty() || base.codePointCount(0, base.length()) > MAX_NAME_CODE_POINTS) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        if (!teamMapper.existsOwnedActiveName(user.tenantId(), user.userId(), base, null)) {
            return base;
        }
        for (int i = 2; i <= 99; i++) {
            String candidate = base + " (" + i + ")";
            if (candidate.codePointCount(0, candidate.length()) > MAX_NAME_CODE_POINTS) {
                break;
            }
            if (!teamMapper.existsOwnedActiveName(user.tenantId(), user.userId(), candidate, null)) {
                return candidate;
            }
        }
        throw error(MvpErrorCode.VALIDATION_ERROR);
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(CurrentUser user, String teamId) {
        AgentTeamEntity entity = requireOwnedTeam(user, teamId);
        return toResponse(entity, masterName(user, entity.getMasterAgentId()), loadMemberIds(user, teamId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamResponse> listTeams(CurrentUser user, Integer page, Integer pageSize) {
        requireUser(user);
        int validPage = page == null ? 1 : page;
        int validPageSize = pageSize == null ? 20 : pageSize;
        if (validPage < 1 || validPageSize < 1 || validPageSize > MAX_PAGE_SIZE) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        List<AgentTeamEntity> rows = teamMapper.selectOwnedPage(
            user.tenantId(), user.userId(), validPageSize + 1, (validPage - 1) * validPageSize);
        boolean hasMore = rows.size() > validPageSize;
        List<AgentTeamEntity> items = hasMore ? rows.subList(0, validPageSize) : rows;
        Map<String, String> namesByAgentId = agentNamesByIds(
            user, items.stream().map(AgentTeamEntity::getMasterAgentId).toList());
        return new PageResponse<>(
            items.stream()
                .map(entity -> toResponse(
                    entity,
                    namesByAgentId.get(entity.getMasterAgentId()),
                    loadMemberIds(user, entity.getId())))
                .toList(),
            validPage,
            validPageSize,
            hasMore
        );
    }

    @Transactional
    public TeamResponse updateTeam(CurrentUser user, String teamId, TeamUpdateRequest request) {
        requireOwnedTeam(user, teamId);
        if (request == null) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        Long version = requireVersion(request.version());
        NormalizedTeamInput input = normalize(
            request.name(), request.description(), request.masterAgentId(), request.memberAgentIds());
        validateActiveNameAvailable(user, input.name(), teamId);
        AgentDefinitionEntity master = requireOnlineMaster(user, input.masterAgentId());
        requireOwnedMembers(user, input.memberAgentIds());

        AgentTeamEntity update = new AgentTeamEntity();
        update.setId(teamId);
        update.setName(input.name());
        update.setDescription(input.description());
        update.setMasterAgentId(input.masterAgentId());

        Instant now = Instant.now(clock);
        int updated = teamMapper.updateOwnedWithVersion(user.tenantId(), user.userId(), update, version, now);
        classifyZeroRow(user, teamId, updated);
        replaceMembers(user, teamId, input.memberAgentIds(), now);
        AgentTeamEntity current = requireOwnedTeam(user, teamId);
        return toResponse(current, master.getName(), input.memberAgentIds());
    }

    @Transactional
    public void deleteTeam(CurrentUser user, String teamId, Long version) {
        requireOwnedTeam(user, teamId);
        int deleted = teamMapper.softDeleteOwnedWithVersion(
            user.tenantId(), user.userId(), teamId, requireVersion(version), Instant.now(clock));
        classifyZeroRow(user, teamId, deleted);
        memberMapper.deleteOwnedMembersByTeam(user.tenantId(), user.userId(), teamId);
    }

    private void replaceMembers(CurrentUser user, String teamId, List<String> memberAgentIds, Instant now) {
        memberMapper.deleteOwnedMembersByTeam(user.tenantId(), user.userId(), teamId);
        List<AgentTeamMemberEntity> rows = new ArrayList<>();
        int sortOrder = 1;
        for (String agentId : memberAgentIds) {
            AgentTeamMemberEntity member = new AgentTeamMemberEntity();
            member.setTenantId(user.tenantId());
            member.setOwnerId(user.userId());
            member.setTeamId(teamId);
            member.setAgentId(agentId);
            member.setSortOrder(sortOrder++);
            member.setCreatedAt(now);
            rows.add(member);
        }
        if (!rows.isEmpty()) {
            memberMapper.batchInsert(rows);
        }
    }

    private List<String> loadMemberIds(CurrentUser user, String teamId) {
        return memberMapper.selectOwnedMembersByTeam(user.tenantId(), user.userId(), teamId).stream()
            .map(AgentTeamMemberEntity::getAgentId)
            .toList();
    }

    private String masterName(CurrentUser user, String masterAgentId) {
        AgentDefinitionEntity master = agentMapper.selectOwnedById(user.tenantId(), user.userId(), masterAgentId);
        return master == null ? null : master.getName();
    }

    private Map<String, String> agentNamesByIds(CurrentUser user, List<String> agentIds) {
        List<String> distinct = agentIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return agentMapper.selectOwnedByIds(user.tenantId(), user.userId(), distinct).stream()
            .collect(Collectors.toMap(AgentDefinitionEntity::getId, AgentDefinitionEntity::getName, (a, b) -> a));
    }

    private NormalizedTeamInput normalize(String name, String description, String masterAgentId, List<String> members) {
        String normalizedName = normalizeRequiredText(name, MAX_NAME_CODE_POINTS, MvpErrorCode.VALIDATION_ERROR);
        String normalizedDescription =
            normalizeRequiredText(description, MAX_DESCRIPTION_CODE_POINTS, MvpErrorCode.VALIDATION_ERROR);
        String normalizedMaster =
            normalizeRequiredText(masterAgentId, 36, MvpErrorCode.TEAM_MASTER_INVALID);
        List<String> normalizedMembers = normalizeMembers(members, normalizedMaster);
        return new NormalizedTeamInput(normalizedName, normalizedDescription, normalizedMaster, normalizedMembers);
    }

    private List<String> normalizeMembers(List<String> raw, String masterAgentId) {
        if (raw == null || raw.isEmpty()) {
            throw error(MvpErrorCode.TEAM_MEMBERS_INVALID);
        }
        LinkedHashSet<String> members = new LinkedHashSet<>();
        for (String agentId : raw) {
            if (agentId == null || agentId.isBlank()) {
                throw error(MvpErrorCode.TEAM_MEMBERS_INVALID);
            }
            String trimmed = agentId.trim();
            if (trimmed.equals(masterAgentId)) {
                throw error(MvpErrorCode.TEAM_MEMBERS_INVALID);
            }
            members.add(trimmed);
        }
        if (members.isEmpty() || members.size() > MAX_MEMBERS) {
            throw error(MvpErrorCode.TEAM_MEMBERS_INVALID);
        }
        return List.copyOf(members);
    }

    private AgentDefinitionEntity requireOnlineMaster(CurrentUser user, String masterAgentId) {
        AgentDefinitionEntity master = agentMapper.selectOwnedById(user.tenantId(), user.userId(), masterAgentId);
        if (master == null || !AgentStatus.ONLINE.name().equals(master.getStatus())) {
            throw error(MvpErrorCode.TEAM_MASTER_INVALID);
        }
        return master;
    }

    private void requireOwnedMembers(CurrentUser user, List<String> memberAgentIds) {
        List<AgentDefinitionEntity> found =
            agentMapper.selectOwnedByIds(user.tenantId(), user.userId(), memberAgentIds);
        if (found.size() != memberAgentIds.size()) {
            throw error(MvpErrorCode.TEAM_MEMBERS_INVALID);
        }
    }

    private AgentTeamEntity requireOwnedTeam(CurrentUser user, String teamId) {
        requireUser(user);
        if (teamId == null || teamId.isBlank()) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
        AgentTeamEntity entity = teamMapper.selectOwnedById(user.tenantId(), user.userId(), teamId);
        if (entity == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
        }
        return entity;
    }

    private void validateActiveNameAvailable(CurrentUser user, String name, String excludeTeamId) {
        if (teamMapper.existsOwnedActiveName(user.tenantId(), user.userId(), name, excludeTeamId)) {
            throw error(MvpErrorCode.VALIDATION_ERROR);
        }
    }

    private void classifyZeroRow(CurrentUser user, String teamId, int affectedRows) {
        if (affectedRows == 1) {
            return;
        }
        if (teamMapper.selectOwnedById(user.tenantId(), user.userId(), teamId) == null) {
            throw error(MvpErrorCode.RESOURCE_NOT_FOUND);
        }
        throw error(MvpErrorCode.VERSION_CONFLICT);
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

    private String normalizeRequiredText(String value, int maxCodePoints, MvpErrorCode onInvalid) {
        if (value == null) {
            throw error(onInvalid);
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw error(onInvalid);
        }
        return normalized;
    }

    private TeamResponse toResponse(AgentTeamEntity entity, String masterAgentName, List<String> memberAgentIds) {
        return new TeamResponse(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getMasterAgentId(),
            masterAgentName,
            memberAgentIds,
            entity.getVersion(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private TeamConfigurationException error(MvpErrorCode code) {
        return new TeamConfigurationException(code, code.name());
    }

    private record NormalizedTeamInput(
        String name,
        String description,
        String masterAgentId,
        List<String> memberAgentIds
    ) {
    }
}
