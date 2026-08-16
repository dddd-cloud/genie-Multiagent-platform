package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.api.Phase2MemoryController;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchItem;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.service.ConversationSummaryAnalysisService;
import com.jd.genie.platform.phase2.configuration.memory.service.MemoryAnalysisService;
import com.jd.genie.platform.phase2.memory.store.MemoryDocumentService;
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

class Phase2AMemoryApiContractTest extends Phase2AApiTestSupport {

    @Test
    void returnsFrozenMemoryPatchAndUsesCurrentUser() throws Exception {
        MemoryAnalysisService memoryService = mock(MemoryAnalysisService.class);
        ConversationSummaryAnalysisService summaryService = mock(ConversationSummaryAnalysisService.class);
        MemoryDocumentService memoryDocuments = mock(MemoryDocumentService.class);
        when(memoryService.analyzeTurn(any())).thenReturn(new MemoryPatchResponse(1, List.of(
            new MemoryPatchItem("UPSERT", "回答偏好", "answerStyle", "concise")
        )));
        var mvc = mvc(new Phase2MemoryController(memoryService, summaryService, memoryDocuments, currentUserProvider));

        mvc.perform(post("/api/v2/memory/analyze-turn").contentType(MediaType.APPLICATION_JSON).content(json(new MemoryAnalysisRequest(
                "conversation-1", "用户说喜欢简洁", "好的", "", "COMPLETED"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.schemaVersion").value(1))
            .andExpect(jsonPath("$.data.patches[0].operation").value("UPSERT"))
            .andExpect(jsonPath("$.data.patches[0].section").value("回答偏好"));
        verify(memoryService).analyzeTurn(any());
    }

    @Test
    void mapsModelAndMalformedFailuresWithoutLeakingMessageText() throws Exception {
        MemoryAnalysisService memoryService = mock(MemoryAnalysisService.class);
        ConversationSummaryAnalysisService summaryService = mock(ConversationSummaryAnalysisService.class);
        MemoryDocumentService memoryDocuments = mock(MemoryDocumentService.class);
        when(memoryService.analyzeTurn(any())).thenThrow(new MemoryAnalysisException(
            MvpErrorCode.MEMORY_ANALYSIS_FAILED, "secret user message body"));
        var mvc = mvc(new Phase2MemoryController(memoryService, summaryService, memoryDocuments, currentUserProvider));

        mvc.perform(post("/api/v2/memory/analyze-turn").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("MEMORY_ANALYSIS_FAILED"))
            .andExpect(jsonPath("$.message").value("MEMORY_ANALYSIS_FAILED"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret user message"))));
        mvc.perform(post("/api/v2/memory/analyze-turn").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
