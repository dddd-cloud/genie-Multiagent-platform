package com.jd.genie.platform.phase2.configuration.skill.binding;

import lombok.Data;

@Data
public class AgentRuntimeSkillSnapshot {
    private String skillId;
    private Long skillVersion;
    private Integer sortOrder;
    private String skillName;
    private String instruction;
    private String outputRequirement;
    private String status;
}
