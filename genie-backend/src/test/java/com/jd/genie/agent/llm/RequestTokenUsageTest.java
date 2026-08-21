package com.jd.genie.agent.llm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestTokenUsageTest {

    @AfterEach
    void tearDown() {
        RequestTokenUsage.clearBillingRequestId();
        RequestTokenUsage.consume("req-1");
        RequestTokenUsage.consume("req-2");
        RequestTokenUsage.consume("conv-1");
    }

    @Test
    void billingKeyPrefersTheThreadLocalOverTheFallback() {
        RequestTokenUsage.setBillingRequestId("conv-1");
        assertEquals("conv-1", RequestTokenUsage.billingKeyOr("execution-id"));
        RequestTokenUsage.clearBillingRequestId();
        assertEquals("execution-id", RequestTokenUsage.billingKeyOr("execution-id"));
    }

    @Test
    void fromAgentRequestIdUsesTheSuffixAfterTheLastColon() {
        assertEquals("turn-9", RequestTokenUsage.fromAgentRequestId("alice-session-uuid:turn-9"));
        assertEquals("plain", RequestTokenUsage.fromAgentRequestId("plain"));
        assertNull(RequestTokenUsage.fromAgentRequestId(" "));
    }

    @Test
    void addsAccumulateAndConsumeClearsTheTurn() {
        RequestTokenUsage.add("req-1", "gpt-4o-mini", 10, 20, 30);
        RequestTokenUsage.add("req-1", "gpt-4o-mini", 5, 7, 12);

        RequestTokenUsage.Snapshot snapshot = RequestTokenUsage.consume("req-1");
        assertEquals("gpt-4o-mini", snapshot.modelName());
        assertEquals(15, snapshot.promptTokens());
        assertEquals(27, snapshot.completionTokens());
        assertEquals(42, snapshot.totalTokens());
        assertNull(RequestTokenUsage.consume("req-1"));
    }

    @Test
    void zeroTotalsAreIgnoredSoAFailedCallDoesNotCreateARow() {
        RequestTokenUsage.add("req-2", "gpt-4o-mini", 0, 0, 0);
        assertNull(RequestTokenUsage.consume("req-2"));
    }
}
