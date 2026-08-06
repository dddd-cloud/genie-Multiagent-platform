package com.jd.genie.platform.phase2.configuration.prompt;

import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;

import java.util.List;

public record PromptPreviewRequest(
    String promptMode,
    String promptConfig,
    String systemPrompt,
    String modelName,
    List<AgentSkillBindingRequest> skills
) {
    public PromptPreviewRequest {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
