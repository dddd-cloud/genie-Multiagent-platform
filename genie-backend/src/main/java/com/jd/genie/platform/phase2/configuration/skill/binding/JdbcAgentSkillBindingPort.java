package com.jd.genie.platform.phase2.configuration.skill.binding;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.skill.binding.entity.AgentSkillBindingEntity;
import com.jd.genie.platform.phase2.configuration.skill.binding.mapper.AgentSkillBindingMapper;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingSpec;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.AgentSkillBindingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JdbcAgentSkillBindingPort implements AgentSkillBindingPort {
    private final AgentSkillBindingMapper bindingMapper;
    private final Clock clock = Clock.systemUTC();

    @Override
    @Transactional(readOnly = true)
    public List<AgentSkillBindingView> loadForAgent(CurrentUser user, String agentId) {
        requireUser(user);
        requireId(agentId);
        return bindingMapper.selectOwnedBindingsByAgent(user.tenantId(), user.userId(), agentId.trim())
            .stream()
            .map(row -> new AgentSkillBindingView(row.getSkillId(), row.getSortOrder() == null ? 0 : row.getSortOrder()))
            .toList();
    }

    @Override
    @Transactional
    public void replaceForAgent(CurrentUser user, String agentId, List<AgentSkillBindingSpec> bindings) {
        requireUser(user);
        requireId(agentId);
        List<AgentSkillBindingSpec> normalized = bindings == null ? List.of() : List.copyOf(bindings);
        bindingMapper.deleteOwnedBindingsByAgent(user.tenantId(), user.userId(), agentId.trim());
        if (normalized.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        List<AgentSkillBindingEntity> rows = normalized.stream().map(spec -> {
            AgentSkillBindingEntity row = new AgentSkillBindingEntity();
            row.setTenantId(user.tenantId());
            row.setOwnerId(user.userId());
            row.setAgentId(agentId.trim());
            row.setSkillId(spec.skillId());
            row.setSortOrder(spec.sortOrder());
            row.setCreatedAt(now);
            return row;
        }).toList();
        bindingMapper.batchInsert(rows);
    }

    @Override
    @Transactional
    public void removeForAgent(CurrentUser user, String agentId) {
        requireUser(user);
        requireId(agentId);
        bindingMapper.deleteOwnedBindingsByAgent(user.tenantId(), user.userId(), agentId.trim());
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.tenantId() == null || user.userId() == null) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "user required");
        }
    }

    private void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "agentId required");
        }
    }
}
