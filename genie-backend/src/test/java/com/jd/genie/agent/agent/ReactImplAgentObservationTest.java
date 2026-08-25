package com.jd.genie.agent.agent;

import com.jd.genie.agent.dto.Memory;
import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.dto.tool.ToolCall;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
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
        assertEquals(1, ReactImplAgent.toolObservationCount(memory));

        memory.addMessage(Message.toolMessage("rendered report", "call-2", null));
        assertEquals(2, ReactImplAgent.toolObservationCount(memory));
    }

    @Test
    void countsParallelToolCallsAsOneExecutionRound() {
        Memory memory = new Memory();
        memory.addMessage(Message.userMessage("plan a trip", null));
        ToolCall geo = ToolCall.builder().id("geo").build();
        ToolCall search = ToolCall.builder().id("search").build();
        memory.addMessage(Message.fromToolCalls("", List.of(geo, search)));
        memory.addMessage(Message.toolMessage("coordinates", "geo", null));
        memory.addMessage(Message.toolMessage("attractions", "search", null));

        assertEquals(2, ReactImplAgent.toolObservationCount(memory));
        assertEquals(1, ReactImplAgent.toolExecutionRoundCount(memory));

        memory.addMessage(Message.fromToolCalls("", List.of(ToolCall.builder().id("route").build())));
        memory.addMessage(Message.toolMessage("driving route", "route", null));
        assertEquals(2, ReactImplAgent.toolExecutionRoundCount(memory));
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

    @Test
    void replacesAnEmptyFinishOnlyResponseWithAParseableFailure() {
        assertEquals(ReactImplAgent.FINISH_TURN_FAILURE, ReactImplAgent.contentForTurn(true, null));
        assertEquals(ReactImplAgent.FINISH_TURN_FAILURE, ReactImplAgent.contentForTurn(true, "  "));
        assertEquals("final", ReactImplAgent.contentForTurn(true, "final"));
        assertEquals("", ReactImplAgent.contentForTurn(false, ""));
    }

    @Test
    void preservesSuccessfulToolEvidenceWhenFinalWordingIsEmpty() {
        Memory memory = new Memory();
        memory.addMessage(Message.toolMessage("{\"distance\":\"18.6km\"}", "route", null));

        String fallback = ReactImplAgent.contentForTurn(true, "", memory);

        assertTrue(fallback.contains("\"status\":\"SUCCESS\""));
        assertTrue(fallback.contains("18.6km"));
    }

    @Test
    void acceptsOnlyWhitelistedEmbeddedToolCall() {
        ToolCollection tools = new ToolCollection();
        tools.addTool(new BaseTool() {
            @Override
            public String getName() {
                return "render_report";
            }

            @Override
            public String getDescription() {
                return "render a report";
            }

            @Override
            public java.util.Map<String, Object> toParams() {
                return java.util.Map.of();
            }

            @Override
            public Object execute(Object input) {
                return input;
            }
        });

        List<ToolCall> calls = ReactImplAgent.embeddedToolCalls("""
                I will call the tool now.
                ```json
                {"name":"render_report","arguments":{"title":"招聘报告","content":"正文"}}
                ```
                """, tools);

        assertEquals(1, calls.size());
        assertEquals("render_report", calls.get(0).getFunction().getName());
        assertEquals("招聘报告", com.alibaba.fastjson.JSON.parseObject(calls.get(0).getFunction().getArguments())
                .getString("title"));
        assertTrue(ReactImplAgent.embeddedToolCalls("""
                ```json
                {"name":"not_bound","arguments":{}}
                ```
                """, tools).isEmpty());
    }
}
