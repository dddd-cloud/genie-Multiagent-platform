package com.jd.genie.platform.phase2.configuration.prompt;

import java.util.List;

public record PromptCompilationRequest(
    String promptMode,
    String promptConfig,
    String systemPrompt,
    List<PromptSkillFragment> skills
) {
    public PromptCompilationRequest {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
