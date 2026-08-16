package com.jd.genie.platform.phase2.configuration.memory.api;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryTurn;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryFileResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryMarkdownWriteRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryStatusResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemorySummaryIndexItemResponse;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemorySummaryIndexResponse;
import com.jd.genie.platform.phase2.configuration.memory.service.ConversationSummaryAnalysisService;
import com.jd.genie.platform.phase2.configuration.memory.service.MemoryAnalysisService;
import com.jd.genie.platform.phase2.memory.store.MemoryDocumentService;
import com.jd.genie.platform.phase2.memory.store.MemoryFileSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/memory")
@RequiredArgsConstructor
public class Phase2MemoryController {
    private final MemoryAnalysisService memoryAnalysisService;
    private final ConversationSummaryAnalysisService summaryAnalysisService;
    private final MemoryDocumentService memoryDocumentService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/status")
    public ApiResponse<MemoryStatusResponse> status() {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ok(new MemoryStatusResponse(
            memoryDocumentService.isAvailable(),
            memoryDocumentService.rootPath(),
            user.userId()
        ));
    }

    @GetMapping("/long-term")
    public ApiResponse<MemoryFileResponse> readLongTerm() {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ok(toFileResponse(memoryDocumentService.readLongTerm(user.userId())));
    }

    @PutMapping("/long-term")
    public ApiResponse<MemoryFileResponse> writeLongTerm(@RequestBody(required = false) MemoryMarkdownWriteRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        memoryDocumentService.writeLongTerm(user.userId(), markdownOf(request));
        return ok(toFileResponse(memoryDocumentService.readLongTerm(user.userId())));
    }

    @DeleteMapping("/long-term")
    public ApiResponse<Void> deleteLongTerm() {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        memoryDocumentService.deleteLongTerm(user.userId());
        return ok(null);
    }

    @GetMapping("/summaries")
    public ApiResponse<MemorySummaryIndexResponse> listSummaries() {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ok(new MemorySummaryIndexResponse(
            memoryDocumentService.listSummaries(user.userId()).stream()
                .map(item -> new MemorySummaryIndexItemResponse(
                    item.conversationId(),
                    item.path(),
                    item.updatedAt(),
                    item.lastSummarizedTurnNo()
                ))
                .toList()
        ));
    }

    @GetMapping("/conversations/{conversationId}/summary")
    public ApiResponse<MemoryFileResponse> readSummary(@PathVariable String conversationId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ok(toFileResponse(memoryDocumentService.readSummary(user.userId(), conversationId)));
    }

    @PutMapping("/conversations/{conversationId}/summary")
    public ApiResponse<MemoryFileResponse> writeSummary(
        @PathVariable String conversationId,
        @RequestBody(required = false) MemoryMarkdownWriteRequest request
    ) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        memoryDocumentService.writeSummary(user.userId(), conversationId, markdownOf(request));
        return ok(toFileResponse(memoryDocumentService.readSummary(user.userId(), conversationId)));
    }

    @DeleteMapping("/conversations/{conversationId}/summary")
    public ApiResponse<Void> deleteSummary(@PathVariable String conversationId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        memoryDocumentService.deleteSummary(user.userId(), conversationId);
        return ok(null);
    }

    @PostMapping("/analyze-turn")
    public ApiResponse<MemoryPatchResponse> analyzeTurn(@RequestBody(required = false) MemoryAnalysisRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        MemoryPatchResponse patches = memoryAnalysisService.analyzeTurn(request);
        memoryDocumentService.persistAnalyzeResult(user.userId(), patches);
        return ok(patches);
    }

    @PostMapping("/summarize-conversation")
    public ApiResponse<ConversationSummaryResponse> summarizeConversation(
        @RequestBody(required = false) ConversationSummaryAnalysisRequest request
    ) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        ConversationSummaryResponse response = summaryAnalysisService.summarize(request);
        if (request != null && request.conversationId() != null && response != null && response.markdown() != null) {
            long lastTurn = request.newTurns().stream()
                .map(ConversationSummaryTurn::turnNo)
                .filter(value -> value != null && value >= 0)
                .mapToLong(Long::longValue)
                .max()
                .orElseGet(() -> memoryDocumentService.lastSummarizedTurnNo(user.userId(), request.conversationId()));
            memoryDocumentService.persistSummaryMarkdown(
                user.userId(),
                request.conversationId(),
                response.markdown(),
                lastTurn
            );
        }
        return ok(response);
    }

    private MemoryFileResponse toFileResponse(MemoryFileSnapshot snapshot) {
        return new MemoryFileResponse(
            snapshot.status().name(),
            snapshot.markdown(),
            snapshot.reason()
        );
    }

    private String markdownOf(MemoryMarkdownWriteRequest request) {
        return request == null ? "" : request.markdown();
    }

    private <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }
}
