package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;

public record SkillEntrypointView(
    String name,
    SkillEntrypointRuntime runtime,
    String script,
    String description,
    String inputSchemaJson
) {
}
