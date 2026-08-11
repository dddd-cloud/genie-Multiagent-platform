package com.jd.genie.platform.phase2.configuration.prompt.api;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewRequest;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewResponse;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewService;
import com.jd.genie.platform.phase2.configuration.prompt.PromptSkillFragmentView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/agents/prompt-preview")
@RequiredArgsConstructor
public class Phase2PromptPreviewController {
    private final PromptPreviewService promptPreviewService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ApiResponse<PromptPreviewView> preview(@RequestBody(required = false) PromptPreviewRequest request) {
        PromptPreviewResponse response = promptPreviewService.preview(currentUserProvider.requireCurrentUser(), request);
        return new ApiResponse<>("OK", "success", new PromptPreviewView(
            response.compiledSystemPromptTemplate(),
            response.skillFragments(),
            response.resolvedModelName(),
            response.codePointLength()
        ));
    }

    public record PromptPreviewView(
        String compiledSystemPromptTemplate,
        List<PromptSkillFragmentView> skillFragments,
        String resolvedModelName,
        int codePointLength
    ) {
        public PromptPreviewView {
            skillFragments = skillFragments == null ? List.of() : List.copyOf(skillFragments);
        }
    }
}
