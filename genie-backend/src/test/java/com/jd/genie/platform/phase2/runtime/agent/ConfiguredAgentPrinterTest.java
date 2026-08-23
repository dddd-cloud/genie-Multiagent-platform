package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.dto.DeepSearchrResponse;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfiguredAgentPrinterTest {
    @Test
    void unescapesJsonStringEscapesInStreamedThoughtFragments() {
        // Deltas landing inside the frozen contract's "output" value still carry
        // JSON string escaping since the JSON document isn't complete/parseable yet.
        assertEquals(
                "第一段\n\n第二段",
                ConfiguredAgentPrinter.unescapeJsonStringFragment("第一段\\n\\n第二段")
        );
        assertEquals(
                "she said \"hi\"\tthen left",
                ConfiguredAgentPrinter.unescapeJsonStringFragment("she said \\\"hi\\\"\\tthen left")
        );
        assertEquals("C:\\path", ConfiguredAgentPrinter.unescapeJsonStringFragment("C:\\\\path"));
        // No backslash: returned unchanged (fast path).
        assertEquals("plain text", ConfiguredAgentPrinter.unescapeJsonStringFragment("plain text"));
        // Unknown escape (e.g. LaTeX/markdown backslash usage) is left untouched.
        assertEquals("\\sqrt{2}", ConfiguredAgentPrinter.unescapeJsonStringFragment("\\sqrt{2}"));
        // Trailing lone backslash (escape split across a chunk boundary) is kept as-is.
        assertEquals("tail\\", ConfiguredAgentPrinter.unescapeJsonStringFragment("tail\\"));
        assertEquals(null, ConfiguredAgentPrinter.unescapeJsonStringFragment(null));
    }

    @Test
    void stripsJsonKeyBoundaryLandingExactlyOnAChunk() {
        // A delta can land exactly on the status->output transition without
        // containing enough of either half for the fragment detectors to fire.
        assertEquals(
                "光合作用是绿色植物…",
                ConfiguredAgentPrinter.stripLeadingJsonKeyBoundary("\",\"output\":\"光合作用是绿色植物…")
        );
        assertEquals(
                "SUCCESS",
                ConfiguredAgentPrinter.stripLeadingJsonKeyBoundary("\"status\":\"SUCCESS")
        );
        // No boundary present: untouched.
        assertEquals("光合作用是…", ConfiguredAgentPrinter.stripLeadingJsonKeyBoundary("光合作用是…"));
        // Boundary only, no content yet: strips down to empty.
        assertEquals("", ConfiguredAgentPrinter.stripLeadingJsonKeyBoundary("\",\"output\":\""));
    }

    @Test
    void detectsFrozenResultContractJson() {
        assertTrue(ConfiguredAgentPrinter.looksLikeResultContract(
                "{\"status\":\"SUCCESS\",\"output\":\"**平台组周报**\",\"errorCode\":null,\"retryable\":false}"
        ));
        assertTrue(ConfiguredAgentPrinter.looksLikeResultContract(
                "```json\n{\"status\":\"FAILURE\",\"output\":null,\"errorCode\":\"EXECUTION_ERROR\",\"retryable\":true}\n```"
        ));
        assertFalse(ConfiguredAgentPrinter.looksLikeResultContract("正在分析周报差异…"));
        assertFalse(ConfiguredAgentPrinter.looksLikeResultContract("{\"foo\":1}"));
        assertTrue(ConfiguredAgentPrinter.looksLikeJsonObject("{\"status\":\"SUCCESS\"}"));
        assertFalse(ConfiguredAgentPrinter.looksLikeJsonObject("正在分析周报差异…"));
    }

    @Test
    void hidesStreamedResultContractFragments() {
        assertTrue(ConfiguredAgentPrinter.looksLikeResultJsonFragment(
                "可正常接收并处理任务。\",\"errorCode"));
        assertTrue(ConfiguredAgentPrinter.looksLikeResultJsonFragment(
                "\":null,\"retryable\":false}"));
        assertEquals(
                "正在判断需要哪些资料",
                ConfiguredAgentPrinter.humanThoughtPrefix(
                        "正在判断需要哪些资料{\"status\":\"SUCCESS\",\"output\":\"已上线\"}"
                )
        );
        assertEquals(
                null,
                ConfiguredAgentPrinter.humanThoughtPrefix("{\"status\":\"SUCCESS\",\"output\":\"已上线\"}")
        );
        assertEquals(
                "正在判断需要哪些资料",
                ConfiguredAgentPrinter.humanThoughtPrefix("正在判断需要哪些资料")
        );
    }

    @Test
    void formatsDeepSearchProgressWithoutDumpingReport() {
        // "start" duplicates the "准备联网搜索：X" intent ReactImplAgent already sent;
        // suppressed so the same sentence doesn't appear twice in a row.
        assertEquals(
                null,
                ConfiguredAgentPrinter.formatDeepSearchProgress(Map.of(
                        "messageType", "start",
                        "query", "贵州茅台2024营收"
                ))
        );

        DeepSearchrResponse extend = DeepSearchrResponse.builder()
                .messageType("extend")
                .isFinal(false)
                .searchResult(DeepSearchrResponse.SearchResult.builder()
                        .query(List.of("茅台2024年报营收", "贵州茅台主营构成"))
                        .build())
                .build();
        assertEquals("搜索方向：茅台2024年报营收；贵州茅台主营构成",
                ConfiguredAgentPrinter.formatDeepSearchProgress(extend));

        DeepSearchrResponse search = DeepSearchrResponse.builder()
                .messageType("search")
                .isFinal(false)
                .searchResult(DeepSearchrResponse.SearchResult.builder()
                        .docs(List.of(List.of(
                                DeepSearchrResponse.SearchDoc.builder().title("贵州茅台年报").link("https://a").build(),
                                DeepSearchrResponse.SearchDoc.builder().title("主营业务构成").link("https://b").build()
                        )))
                        .build())
                .build();
        assertEquals("已找到网页（2）：贵州茅台年报、主营业务构成",
                ConfiguredAgentPrinter.formatDeepSearchProgress(search));

        DeepSearchrResponse report = DeepSearchrResponse.builder()
                .messageType("report")
                .isFinal(false)
                .answer("很长的报告正文不应展示")
                .build();
        assertEquals("正在根据网页整理要点", ConfiguredAgentPrinter.formatDeepSearchProgress(report));

        DeepSearchrResponse done = DeepSearchrResponse.builder()
                .messageType("report")
                .isFinal(true)
                .answer("最终答案")
                .build();
        assertEquals("搜索整理完成", ConfiguredAgentPrinter.formatDeepSearchProgress(done));
    }

    @Test
    void recoversStreamedMarkdownWhenEnvelopeIsMissing() {
        ConversationStreamObserver observer = mock(ConversationStreamObserver.class);
        OrchestrationTraceChannel channel = new OrchestrationTraceChannel(
                observer, "req", "run", new java.util.concurrent.atomic.AtomicLong());
        ConfiguredAgentPrinter printer = new ConfiguredAgentPrinter(
                channel, observer, 1, "s1", "a1", "专家");
        printer.send("idle", "tool_thought", "正在判断需要哪些资料", true);
        printer.send("idle2", "tool_thought", "准备读写文件", true);
        assertEquals(null, printer.recoveredOutput());
        printer.send("m1", "tool_thought", "**假设**：基于 Java 后端。", false);
        printer.send("m1", "tool_thought", " Agent 编排层推荐 Spring AI。", false);
        String recovered = printer.recoveredOutput();
        assertTrue(recovered.contains("Spring AI"));
        assertTrue(recovered.contains("假设"));
    }

    @Test
    void streamsParallelWorkerThoughtsOntoSubTaskChannel() {
        ConversationStreamObserver observer = mock(ConversationStreamObserver.class);
        OrchestrationTraceChannel channel = new OrchestrationTraceChannel(
                observer, "req", "run", new java.util.concurrent.atomic.AtomicLong());
        ConfiguredAgentPrinter printer = new ConfiguredAgentPrinter(
                channel, observer, 1, "s1", "a1", "前端", "st-front");
        printer.send("m1", "tool_thought", "先搭画布。", false);

        ArgumentCaptor<GptProcessResult> captor = ArgumentCaptor.forClass(GptProcessResult.class);
        verify(observer).onEventBestEffort(captor.capture());
        Object payload = captor.getValue().getResultMap().get("orchestrationTrace");
        assertTrue(payload instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> trace = (Map<String, Object>) payload;
        assertEquals("SUBTASK", trace.get("scope"));
        assertEquals("st-front", trace.get("subTaskId"));
        assertEquals("THOUGHT", trace.get("kind"));
        assertEquals("先搭画布。", trace.get("text"));
    }
}
