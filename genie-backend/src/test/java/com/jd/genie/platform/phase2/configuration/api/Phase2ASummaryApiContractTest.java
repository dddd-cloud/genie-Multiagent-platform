package com.jd.genie.platform.phase2.configuration.api;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.api.Phase2MemoryController;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryTurn;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.service.ConversationSummaryAnalysisService;
import com.jd.genie.platform.phase2.configuration.memory.service.MemoryAnalysisService;
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

class Phase2ASummaryApiContractTest extends Phase2AApiTestSupport {

    @Test
    void returnsFrozenFourSectionSummary() throws Exception {
        MemoryAnalysisService memoryService = mock(MemoryAnalysisService.class);
        ConversationSummaryAnalysisService summaryService = mock(ConversationSummaryAnalysisService.class);
        when(summaryService.summarize(any())).thenReturn(new ConversationSummaryResponse(1,
            "## 当前目标\n- demo\n\n## 已确认事实\n- fact\n\n## 已完成内容\n- done\n\n## 未解决事项\n- none"));
        var mvc = mvc(new Phase2MemoryController(memoryService, summaryService, currentUserProvider));

        mvc.perform(post("/api/v2/memory/summarize-conversation").contentType(MediaType.APPLICATION_JSON).content(json(
                new ConversationSummaryAnalysisRequest("conversation-1", "", List.of(
                    new ConversationSummaryTurn(1L, "user", "assistant", "COMPLETED"))))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.schemaVersion").value(1))
            .andExpect(jsonPath("$.data.markdown").value(org.hamcrest.Matchers.containsString("## 当前目标")));
        verify(summaryService).summarize(any());
    }

    @Test
    void mapsSummaryFailureWithoutLeakingSummaryText() throws Exception {
        MemoryAnalysisService memoryService = mock(MemoryAnalysisService.class);
        ConversationSummaryAnalysisService summaryService = mock(ConversationSummaryAnalysisService.class);
        when(summaryService.summarize(any())).thenThrow(new MemoryAnalysisException(MvpErrorCode.SUMMARY_FAILED,
            "raw summary with SECRET"));
        var mvc = mvc(new Phase2MemoryController(memoryService, summaryService, currentUserProvider));

        mvc.perform(post("/api/v2/memory/summarize-conversation").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("SUMMARY_FAILED"))
            .andExpect(jsonPath("$.message").value("SUMMARY_FAILED"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
    }
}
