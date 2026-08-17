package com.jd.genie.platform.phase2.configuration.team.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
public class AgentTeamMemberEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String ownerId;
    private String teamId;
    private String agentId;
    private Integer sortOrder;
    private Instant createdAt;
}
