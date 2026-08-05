package com.jd.genie.platform.phase2.configuration.memory.service;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryAnalysisRequest;
import com.jd.genie.platform.phase2.configuration.memory.dto.ConversationSummaryTurn;
import com.jd.genie.platform.phase2.configuration.memory.exception.MemoryAnalysisException;
import com.jd.genie.platform.phase2.configuration.memory.prompt.ConversationSummaryPromptFactory;
import com.jd.genie.platform.phase2.configuration.memory.validation.ConversationSummaryValidator;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemoryInputValidator;
import com.jd.genie.platform.phase2.configuration.memory.validation.MemorySecretFilter;
import com.jd.genie.platform.phase2.configuration.support.FakeMemoryAnalysisModelClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationSummaryAnalysisServiceTest {

    @Test
    void summarizesCompletedTurnsWithFixedFourSections() {
        FakeMemoryAnalysisModelClient fake = new FakeMemoryAnalysisModelClient();
        ConversationSummaryAnalysisService service = service(fake);

        var response = service.summarize(requestWithTurn());

        assertEquals(1, fake.summaryCalls());
        assertTrue(response.markdown().contains("## 当前目标"));
        assertTrue(response.markdown().contains("## 未解决事项"));
        assertTrue(fake.lastSummaryRequest().systemPrompt().contains("exactly these four H2 sections"));
    }

    @Test
    void returnsValidatedExistingOrEmptySummaryWithoutModelWhenNoNewTurns() {
        FakeMemoryAnalysisModelClient fake = new FakeMemoryAnalysisModelClient();
        ConversationSummaryAnalysisService service = service(fake);

        var empty = service.summarize(new ConversationSummaryAnalysisRequest("c1", "", List.of()));
        var existing = service.summarize(new ConversationSummaryAnalysisRequest("c1", empty.markdown(), List.of()));

        assertEquals(0, fake.summaryCalls());
        assertTrue(existing.markdown().contains("## 已确认事实"));
    }

    @Test
    void rejectsNonCompletedTurnsBeforeModelCall() {
        FakeMemoryAnalysisModelClient fake = new FakeMemoryAnalysisModelClient();
        MemoryAnalysisException ex = assertThrows(MemoryAnalysisException.class,
            () -> service(fake).summarize(new ConversationSummaryAnalysisRequest("c1", "", List.of(
                new ConversationSummaryTurn(1L, "Q", "A", "FAILED")
            ))));

        assertEquals(MvpErrorCode.VALIDATION_ERROR, ex.code());
        assertEquals(0, fake.summaryCalls());
    }

    @Test
    void mapsInvalidSummaryAndTimeoutToSummaryFailed() {
        FakeMemoryAnalysisModelClient invalid = new FakeMemoryAnalysisModelClient();
        invalid.summaryContent("{\"schemaVersion\":1,\"markdown\":\"## 当前目标\\n- A\"}");
        MemoryAnalysisException invalidSummary = assertThrows(MemoryAnalysisException.class,
            () -> service(invalid).summarize(requestWithTurn()));

        FakeMemoryAnalysisModelClient timeout = new FakeMemoryAnalysisModelClient();
        timeout.failSummary(new IllegalStateException("timeout"));
        MemoryAnalysisException timeoutError = assertThrows(MemoryAnalysisException.class,
            () -> service(timeout).summarize(requestWithTurn()));

        assertEquals(MvpErrorCode.SUMMARY_FAILED, invalidSummary.code());
        assertEquals(MvpErrorCode.SUMMARY_FAILED, timeoutError.code());
    }

    @Test
    void enforcesConcurrencyLimitWithoutQueueing() throws Exception {
        FakeMemoryAnalysisModelClient fake = new FakeMemoryAnalysisModelClient();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fake.blockOnCall(entered, release);
        ConversationSummaryAnalysisService service = service(fake, 1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> first = CompletableFuture.runAsync(() -> service.summarize(requestWithTurn()), executor);
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));

            MemoryAnalysisException second = assertThrows(MemoryAnalysisException.class,
                () -> service.summarize(requestWithTurn()));

            assertEquals(MvpErrorCode.SUMMARY_FAILED, second.code());
            release.countDown();
            first.join();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private ConversationSummaryAnalysisService service(FakeMemoryAnalysisModelClient fake) {
        return service(fake, 4);
    }

    private ConversationSummaryAnalysisService service(FakeMemoryAnalysisModelClient fake, int maxConcurrent) {
        return new ConversationSummaryAnalysisService(
            new MemoryInputValidator(),
            new ConversationSummaryPromptFactory(),
            fake,
            new ConversationSummaryValidator(new MemorySecretFilter()),
            maxConcurrent,
            1_000
        );
    }

    private ConversationSummaryAnalysisRequest requestWithTurn() {
        return new ConversationSummaryAnalysisRequest("conversation-1", "", List.of(
            new ConversationSummaryTurn(1L, "请制定 Docker 学习计划", "三天计划如下", "COMPLETED")
        ));
    }
}
