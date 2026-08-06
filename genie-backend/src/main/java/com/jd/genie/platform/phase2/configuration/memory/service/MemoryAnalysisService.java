package com.jd.genie.platform.phase2.configuration.memory.service;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryPatchResponse;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.llm.MemoryAnalysisModelClient;
import com.jd.genie.platform.phase2.configuration.memory.llm.MemoryAnalysisModelRequest;
import com.jd.genie.platform.phase2.configuration.memory.prompt.MemoryAnalysisPromptFactory;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryInputValidator;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryPatchValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Semaphore;

@Service
public class MemoryAnalysisService {
    public static final int DEFAULT_TIMEOUT_MS = 30_000;
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 4;

    private final MemoryInputValidator inputValidator;
    private final MemoryAnalysisPromptFactory promptFactory;
    private final MemoryAnalysisModelClient modelClient;
    private final MemoryPatchValidator patchValidator;
    private final Semaphore semaphore;
    private final int timeoutMs;

    @Autowired
    public MemoryAnalysisService(
        MemoryInputValidator inputValidator,
        MemoryAnalysisPromptFactory promptFactory,
        MemoryAnalysisModelClient modelClient,
        MemoryPatchValidator patchValidator
    ) {
        this(inputValidator, promptFactory, modelClient, patchValidator, DEFAULT_MAX_CONCURRENT_REQUESTS, DEFAULT_TIMEOUT_MS);
    }

    MemoryAnalysisService(
        MemoryInputValidator inputValidator,
        MemoryAnalysisPromptFactory promptFactory,
        MemoryAnalysisModelClient modelClient,
        MemoryPatchValidator patchValidator,
        int maxConcurrentRequests,
        int timeoutMs
    ) {
        this.inputValidator = inputValidator;
        this.promptFactory = promptFactory;
        this.modelClient = modelClient;
        this.patchValidator = patchValidator;
        this.semaphore = new Semaphore(maxConcurrentRequests);
        this.timeoutMs = timeoutMs;
    }

    public MemoryPatchResponse analyzeTurn(MemoryAnalysisRequest request) {
        inputValidator.validateMemoryRequest(request);
        if (!"COMPLETED".equals(request.turnStatus())) {
            return new MemoryPatchResponse(MemoryPatchValidator.SCHEMA_VERSION, List.of());
        }
        acquire(MvpErrorCode.MEMORY_ANALYSIS_FAILED);
        try {
            var modelRequest = new MemoryAnalysisModelRequest(
                request.conversationId(),
                promptFactory.systemPrompt(),
                promptFactory.userPrompt(request),
                timeoutMs
            );
            return patchValidator.parseAndValidate(modelClient.analyzeMemory(modelRequest).content());
        } catch (MemoryAnalysisException ex) {
            throw ex;
        } catch (Exception ex) {
            throw failed(MvpErrorCode.MEMORY_ANALYSIS_FAILED);
        } finally {
            semaphore.release();
        }
    }

    private void acquire(MvpErrorCode code) {
        if (!semaphore.tryAcquire()) {
            throw failed(code);
        }
    }

    private MemoryAnalysisException failed(MvpErrorCode code) {
        return new MemoryAnalysisException(code, code.name());
    }
}
