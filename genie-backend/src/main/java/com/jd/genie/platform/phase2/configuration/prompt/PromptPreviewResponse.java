package com.jd.genie.platform.phase2.configuration.prompt;

import java.util.List;

public record PromptPreviewResponse(
    String compiledSystemPromptTemplate,
    List<PromptSkillFragmentView> skillFragments,
    String resolvedModelName,
    int codePointLength
) {
    public PromptPreviewResponse {
        skillFragments = skillFragments == null ? List.of() : List.copyOf(skillFragments);
    }
}
