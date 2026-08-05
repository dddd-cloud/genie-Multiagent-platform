package com.jd.genie.platform.phase2.configuration.memory.service;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.MemoryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.prompt.MemoryAnalysisPromptFactory;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryInputValidator;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryMarkdownGuard;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryPatchValidator;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemorySecretFilter;
import com.jd.genie.platform.phase2.configuration.support.FakeMemoryAnalysisModelClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryAnalysisServiceTest {

    @Test
    void analyzesCompletedTurnAndReturnsValidatedPatch() {
        FakeMemoryAnalysisModelClient fake = new FakeMemoryAnalysisModelClient();
        fake.memoryContent("""
            {"schemaVersion":1,"patches":[{"operation":"UPSERT","section":"长期目标","key":"learnDocker","value":"用户希望系统学习 Docker"}]}
            """);
        MemoryAnalysisService service = service(fake);

        var response = service.analyzeTurn(completedRequest());

        assertEquals(1, fake.memoryCalls());
        assertEquals("UPSERT", response.patches().get(0).operation());
        assertTrue(fake.lastMemoryRequest().systemPrompt().contains("Return only compact JSON"));
        assertTrue(fake.lastMemoryRequest().userPrompt().contains("userMessage:"));
    }

    @Test
    void returnsEmptyPatchAndSkipsModelForNonCompletedTurn() {
        FakeMemoryAnalysisModelClient fake = new FakeMemoryAnalysisModelClient();
        MemoryAnalysisService service = service(fake);

        var response = service.analyzeTurn(new MemoryAnalysisRequest("c1", "用户说先暂停", "稍后处理", "", "INTERRUPTED"));

        assertEquals(0, response.patches().size());
        assertEquals(0, fake.memoryCalls());
    }

    @Test
    void mapsInvalidJsonAndModelTimeoutToMemoryAnalysisFailed() {
        FakeMemoryAnalysisModelClient invalid = new FakeMemoryAnalysisModelClient();
        invalid.memoryContent("not-json");
        MemoryAnalysisException invalidJson = assertThrows(MemoryAnalysisException.class,
            () -> service(invalid).analyzeTurn(completedRequest()));

        FakeMemoryAnalysisModelClient timeout = new FakeMemoryAnalysisModelClient();
        timeout.failMemory(new IllegalStateException("timeout"));
        MemoryAnalysisException timeoutError = assertThrows(MemoryAnalysisException.class,
            () -> service(timeout).analyzeTurn(completedRequest()));

        assertEquals(MvpErrorCode.MEMORY_ANALYSIS_FAILED, invalidJson.code());
        assertEquals(MvpErrorCode.MEMORY_ANALYSIS_FAILED, timeoutError.code());
    }

    @Test
    void rejectsAssistantOnlyMemoryExtractionThroughValidatedModelOutput() {
        FakeMemoryAnalysisModelClient fake = new FakeMemoryAnalysisModelClient();
        fake.memoryContent("{\"schemaVersion\":1,\"patches\":[]}");
        MemoryAnalysisService service = service(fake);

        var response = service.analyzeTurn(new MemoryAnalysisRequest("c1", "请给我建议", "用户喜欢极简 Markdown", "", "COMPLETED"));

        assertEquals(0, response.patches().size());
        assertTrue(fake.lastMemoryRequest().systemPrompt().contains("Do not infer memory from assistant suggestions"));
    }

    @Test
    void enforcesConcurrencyLimitWithoutQueueing() throws Exception {
        FakeMemoryAnalysisModelClient fake = new FakeMemoryAnalysisModelClient();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fake.blockOnCall(entered, release);
        MemoryAnalysisService service = service(fake, 1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> service.analyzeTurn(completedRequest()), executor);
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));

            MemoryAnalysisException second = assertThrows(MemoryAnalysisException.class,
                () -> service.analyzeTurn(completedRequest()));

            assertEquals(MvpErrorCode.MEMORY_ANALYSIS_FAILED, second.code());
            release.countDown();
            first.join();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private MemoryAnalysisService service(FakeMemoryAnalysisModelClient fake) {
        return service(fake, 4);
    }

    private MemoryAnalysisService service(FakeMemoryAnalysisModelClient fake, int maxConcurrent) {
        MemorySecretFilter secretFilter = new MemorySecretFilter();
        return new MemoryAnalysisService(
            new MemoryInputValidator(),
            new MemoryAnalysisPromptFactory(),
            fake,
            new MemoryPatchValidator(secretFilter, new MemoryMarkdownGuard()),
            maxConcurrent,
            1_000
        );
    }

    private MemoryAnalysisRequest completedRequest() {
        return new MemoryAnalysisRequest("conversation-1", "我长期目标是学习 Docker", "OK", "", "COMPLETED");
    }
}
