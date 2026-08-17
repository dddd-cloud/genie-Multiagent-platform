package com.jd.genie.platform.phase2.configuration.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("agent_team")
public class AgentTeamEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String tenantId;
    private String ownerId;
    private String name;
    private String description;
    private String masterAgentId;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
