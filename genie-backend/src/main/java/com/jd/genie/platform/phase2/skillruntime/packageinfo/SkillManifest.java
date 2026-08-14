package com.jd.genie.platform.phase2.skillruntime.packageinfo;

import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import java.util.List;

public record SkillManifest(String name, String description, String version,
                            String instructionMarkdown, List<SkillEntrypointView> entrypoints) {
    public SkillManifest {
        entrypoints = entrypoints == null ? List.of() : List.copyOf(entrypoints);
    }
}
