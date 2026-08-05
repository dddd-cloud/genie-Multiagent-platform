package com.jd.genie.platform.phase2.configuration.memory.llm;

public interface MemoryAnalysisModelClient {
    MemoryAnalysisModelResponse analyzeMemory(MemoryAnalysisModelRequest request);

    MemoryAnalysisModelResponse summarizeConversation(MemoryAnalysisModelRequest request);
}
