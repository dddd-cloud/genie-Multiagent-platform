package com.jd.genie.platform.phase2.configuration.memory.service;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryResponse;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.llm.MemoryAnalysisModelClient;
import com.jd.genie.platform.phase2.configuration.memory.llm.MemoryAnalysisModelRequest;
import com.jd.genie.platform.phase2.configuration.memory.prompt.ConversationSummaryPromptFactory;
import com.jd.genie.platform.phase2.configuration.memory.validation.ConversationSummaryValidator;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryInputValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Service
public class ConversationSummaryAnalysisService {
    private static final String EMPTY_SUMMARY = """
        ## 当前目标
        - 暂无

        ## 已确认事实
        - 暂无

        ## 已完成内容
        - 暂无

        ## 未解决事项
        - 暂无
        """;

    private final MemoryInputValidator inputValidator;
    private final ConversationSummaryPromptFactory promptFactory;
    private final MemoryAnalysisModelClient modelClient;
    private final ConversationSummaryValidator summaryValidator;
    private final Semaphore semaphore;
    private final int timeoutMs;

    @Autowired
    public ConversationSummaryAnalysisService(
        MemoryInputValidator inputValidator,
        ConversationSummaryPromptFactory promptFactory,
        MemoryAnalysisModelClient modelClient,
        ConversationSummaryValidator summaryValidator
    ) {
        this(inputValidator, promptFactory, modelClient, summaryValidator,
            MemoryAnalysisService.DEFAULT_MAX_CONCURRENT_REQUESTS, MemoryAnalysisService.DEFAULT_TIMEOUT_MS);
    }

    ConversationSummaryAnalysisService(
        MemoryInputValidator inputValidator,
        ConversationSummaryPromptFactory promptFactory,
        MemoryAnalysisModelClient modelClient,
        ConversationSummaryValidator summaryValidator,
        int maxConcurrentRequests,
        int timeoutMs
    ) {
        this.inputValidator = inputValidator;
        this.promptFactory = promptFactory;
        this.modelClient = modelClient;
        this.summaryValidator = summaryValidator;
        this.semaphore = new Semaphore(maxConcurrentRequests);
        this.timeoutMs = timeoutMs;
    }

    public ConversationSummaryResponse summarize(ConversationSummaryAnalysisRequest request) {
        inputValidator.validateSummaryRequest(request);
        if (request.newTurns().isEmpty()) {
            String markdown = request.currentSummary() == null || request.currentSummary().isBlank()
                ? EMPTY_SUMMARY : request.currentSummary();
            return summaryValidator.validateMarkdown(markdown);
        }
        acquire();
        try {
            var modelRequest = new MemoryAnalysisModelRequest(
                request.conversationId(),
                promptFactory.systemPrompt(),
                promptFactory.userPrompt(request),
                timeoutMs
            );
            return summaryValidator.parseAndValidate(modelClient.summarizeConversation(modelRequest).content());
        } catch (MemoryAnalysisException ex) {
            throw ex;
        } catch (Exception ex) {
            throw failed();
        } finally {
            semaphore.release();
        }
    }

    private void acquire() {
        if (!semaphore.tryAcquire()) {
            throw failed();
        }
    }

    private MemoryAnalysisException failed() {
        return new MemoryAnalysisException(MvpErrorCode.SUMMARY_FAILED, MvpErrorCode.SUMMARY_FAILED.name());
    }
}
