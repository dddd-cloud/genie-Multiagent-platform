package com.jd.genie.platform.phase2.configuration.support;

import com.jd.genie.platform.phase2.configuration.memory.llm.MemoryAnalysisModelClient;
import com.jd.genie.platform.phase2.configuration.memory.llm.MemoryAnalysisModelRequest;
import com.jd.genie.platform.phase2.configuration.memory.llm.MemoryAnalysisModelResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeMemoryAnalysisModelClient implements MemoryAnalysisModelClient {
    private String memoryContent = "{\"schemaVersion\":1,\"patches\":[]}";
    private String summaryContent = "{\"schemaVersion\":1,\"markdown\":\"## 当前目标\\n- 学习 Docker\\n\\n## 已确认事实\\n- 使用真实 MySQL\\n\\n## 已完成内容\\n- 完成创建\\n\\n## 未解决事项\\n- 无\"}";
    private RuntimeException memoryFailure;
    private RuntimeException summaryFailure;
    private CountDownLatch enterLatch;
    private CountDownLatch releaseLatch;
    private MemoryAnalysisModelRequest lastMemoryRequest;
    private MemoryAnalysisModelRequest lastSummaryRequest;
    private final AtomicInteger memoryCalls = new AtomicInteger();
    private final AtomicInteger summaryCalls = new AtomicInteger();

    @Override
    public MemoryAnalysisModelResponse analyzeMemory(MemoryAnalysisModelRequest request) {
        memoryCalls.incrementAndGet();
        lastMemoryRequest = request;
        awaitIfConfigured();
        if (memoryFailure != null) {
            throw memoryFailure;
        }
        return new MemoryAnalysisModelResponse(memoryContent);
    }

    @Override
    public MemoryAnalysisModelResponse summarizeConversation(MemoryAnalysisModelRequest request) {
        summaryCalls.incrementAndGet();
        lastSummaryRequest = request;
        awaitIfConfigured();
        if (summaryFailure != null) {
            throw summaryFailure;
        }
        return new MemoryAnalysisModelResponse(summaryContent);
    }

    public void memoryContent(String memoryContent) {
        this.memoryContent = memoryContent;
    }

    public void summaryContent(String summaryContent) {
        this.summaryContent = summaryContent;
    }

    public void failMemory(RuntimeException failure) {
        this.memoryFailure = failure;
    }

    public void failSummary(RuntimeException failure) {
        this.summaryFailure = failure;
    }

    public void blockOnCall(CountDownLatch enterLatch, CountDownLatch releaseLatch) {
        this.enterLatch = enterLatch;
        this.releaseLatch = releaseLatch;
    }

    public int memoryCalls() {
        return memoryCalls.get();
    }

    public int summaryCalls() {
        return summaryCalls.get();
    }

    public MemoryAnalysisModelRequest lastMemoryRequest() {
        return lastMemoryRequest;
    }

    public MemoryAnalysisModelRequest lastSummaryRequest() {
        return lastSummaryRequest;
    }

    private void awaitIfConfigured() {
        if (enterLatch == null || releaseLatch == null) {
            return;
        }
        enterLatch.countDown();
        try {
            releaseLatch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        }
    }
}
