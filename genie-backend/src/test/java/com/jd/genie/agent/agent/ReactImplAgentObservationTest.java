package com.jd.genie.agent.agent;

import com.jd.genie.agent.dto.Memory;
import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.dto.tool.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactImplAgentObservationTest {

    @Test
    void detectsToolObservationsForFinishTurn() {
        assertFalse(ReactImplAgent.hasToolObservation(null));
        assertFalse(ReactImplAgent.hasToolObservation(new Memory()));

        Memory memory = new Memory();
        memory.addMessage(Message.userMessage("search the market", null));
        assertFalse(ReactImplAgent.hasToolObservation(memory));

        memory.addMessage(Message.toolMessage("search notes", "call-1", null));
        assertTrue(ReactImplAgent.hasToolObservation(memory));
    }

    @Test
    void waitsForNormalResponsesWithinTheConfiguredDeadline() throws Exception {
        CompletableFuture<String> response = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(75);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "done";
        });

        assertEquals("done", ReactImplAgent.awaitResponse(response, 500, TimeUnit.MILLISECONDS));
        assertFalse(response.isCancelled());
    }

    @Test
    void cancelsAResponseThatExceedsTheAbsoluteDeadline() {
        CompletableFuture<String> response = new CompletableFuture<>();

        assertThrows(
                TimeoutException.class,
                () -> ReactImplAgent.awaitResponse(response, 50, TimeUnit.MILLISECONDS)
        );
        assertTrue(response.isCancelled());
    }

    @Test
    void ignoresProviderToolCallsDuringTheFinishOnlyTurn() {
        ToolCall unexpected = ToolCall.builder()
                .id("unexpected")
                .type("function")
                .function(ToolCall.Function.builder().name("should_not_run").arguments("{}").build())
                .build();

        assertTrue(ReactImplAgent.toolCallsForTurn(true, List.of(unexpected)).isEmpty());
        assertEquals(List.of(unexpected), ReactImplAgent.toolCallsForTurn(false, List.of(unexpected)));
    }
}
