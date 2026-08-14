package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.agent.dto.DeepSearchrResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredAgentPrinterTest {
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
    }

    @Test
    void formatsDeepSearchProgressWithoutDumpingReport() {
        assertEquals(
                "正在联网搜索：贵州茅台2024营收",
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
}
