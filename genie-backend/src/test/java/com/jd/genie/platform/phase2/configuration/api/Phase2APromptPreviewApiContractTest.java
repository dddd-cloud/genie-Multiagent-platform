package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewRequest;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewResponse;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewService;
import com.jd.genie.platform.phase2.configuration.prompt.PromptSkillFragmentView;
import com.jd.genie.platform.phase2.configuration.prompt.PromptValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase2APromptPreviewApiContractTest extends Phase2AApiTestSupport {

    @Test
    void previewsStructuredAndRawWithoutPersistingOrReturningSecrets() throws Exception {
        PromptPreviewService service = mock(PromptPreviewService.class);
        when(service.preview(any(), any())).thenReturn(new PromptPreviewResponse(
            "compiled prompt",
            List.of(new PromptSkillFragmentView("skill-1", 2L, 1)),
            "qwen-plus",
            15
        ));
        var mvc = mvc(new Phase2PromptPreviewController(service, currentUserProvider));

        mvc.perform(post("/api/v2/agents/prompt-preview").contentType(MediaType.APPLICATION_JSON).content(json(new PromptPreviewRequest(
                "STRUCTURED", "{\"role\":\"researcher\"}", "forged compiled", "system-default", List.of()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.compiledSystemPromptTemplate").value("compiled prompt"))
            .andExpect(jsonPath("$.data.skillFragments[0].skillId").value("skill-1"))
            .andExpect(jsonPath("$.data.resolvedModelName").value("qwen-plus"))
            .andExpect(jsonPath("$.data.codePointLength").value(15))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("apiKey"))));
        verify(service).preview(any(), any());
    }

    @Test
    void mapsPromptValidationToFrozenError() throws Exception {
        PromptPreviewService service = mock(PromptPreviewService.class);
        when(service.preview(any(), any())).thenThrow(new PromptValidationException(MvpErrorCode.PROMPT_INVALID, "raw prompt text"));
        var mvc = mvc(new Phase2PromptPreviewController(service, currentUserProvider));

        mvc.perform(post("/api/v2/agents/prompt-preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PROMPT_INVALID"))
            .andExpect(jsonPath("$.message").value("PROMPT_INVALID"));
    }
}
