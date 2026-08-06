package com.jd.genie.platform.phase2.configuration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewRequest;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewResponse;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/agents/prompt-preview")
@RequiredArgsConstructor
public class Phase2PromptPreviewController {
    private final PromptPreviewService promptPreviewService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ApiResponse<Phase2ApiAssembler.PromptPreviewView> preview(@RequestBody(required = false) PromptPreviewRequest request) {
        PromptPreviewResponse response = promptPreviewService.preview(currentUserProvider.requireCurrentUser(), request);
        return new ApiResponse<>("OK", "success", new Phase2ApiAssembler.PromptPreviewView(
            response.compiledSystemPromptTemplate(),
            response.skillFragments(),
            response.resolvedModelName(),
            response.codePointLength()
        ));
    }
}
