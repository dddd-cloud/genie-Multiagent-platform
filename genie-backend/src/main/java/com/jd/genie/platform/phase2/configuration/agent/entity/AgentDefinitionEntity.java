package com.jd.genie.platform.phase2.configuration.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("agent_definition")
public class AgentDefinitionEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String tenantId;
    private String ownerId;
    private String name;
    private String description;
    private String promptMode;
    private String promptConfig;
    private String systemPrompt;
    private String modelName;
    private String status;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}