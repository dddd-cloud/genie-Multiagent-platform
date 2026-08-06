package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import com.jd.genie.platform.phase2.configuration.memory.service.ConversationSummaryAnalysisService;
import com.jd.genie.platform.phase2.configuration.memory.service.MemoryAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/memory")
@RequiredArgsConstructor
public class Phase2MemoryController {
    private final MemoryAnalysisService memoryAnalysisService;
    private final ConversationSummaryAnalysisService summaryAnalysisService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/analyze-turn")
    public ApiResponse<MemoryPatchResponse> analyzeTurn(@RequestBody(required = false) MemoryAnalysisRequest request) {
        currentUserProvider.requireCurrentUser();
        return new ApiResponse<>("OK", "success", memoryAnalysisService.analyzeTurn(request));
    }

    @PostMapping("/summarize-conversation")
    public ApiResponse<ConversationSummaryResponse> summarizeConversation(
        @RequestBody(required = false) ConversationSummaryAnalysisRequest request
    ) {
        currentUserProvider.requireCurrentUser();
        return new ApiResponse<>("OK", "success", summaryAnalysisService.summarize(request));
    }
}
