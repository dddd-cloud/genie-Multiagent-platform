package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;

import java.util.List;

public record SkillEntrypointView(
    String name,
    SkillEntrypointRuntime runtime,
    String script,
    String description,
    String inputSchemaJson,
    List<String> packages
) {
    public SkillEntrypointView {
        packages = packages == null ? List.of() : List.copyOf(packages);
    }

    /** Backward-compatible constructor for existing Fake/tests. */
    public SkillEntrypointView(
        String name,
        SkillEntrypointRuntime runtime,
        String script,
        String description,
        String inputSchemaJson
    ) {
        this(name, runtime, script, description, inputSchemaJson, List.of());
    }
}
