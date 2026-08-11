package com.jd.genie.platform.phase2.configuration.skill.binding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("agent_skill_binding")
public class AgentSkillBindingEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String ownerId;
    private String agentId;
    private String skillId;
    private Integer sortOrder;
    private Instant createdAt;
}
