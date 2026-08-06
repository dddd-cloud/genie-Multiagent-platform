package com.jd.genie.platform.phase2.configuration.prompt;

public record PromptSkillFragment(
    String skillId,
    Long skillVersion,
    String skillName,
    int sortOrder,
    String instruction,
    String outputRequirement
) {
}
