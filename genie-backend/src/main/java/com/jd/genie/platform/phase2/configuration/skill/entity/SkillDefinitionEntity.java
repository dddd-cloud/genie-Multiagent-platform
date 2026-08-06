package com.jd.genie.platform.phase2.configuration.skill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@TableName("skill_definition")
public class SkillDefinitionEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String tenantId;
    private String ownerId;
    private String name;
    private String description;
    private String instruction;
    private String outputRequirement;
    private String status;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}