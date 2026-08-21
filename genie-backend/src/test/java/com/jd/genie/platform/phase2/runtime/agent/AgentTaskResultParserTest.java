package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTaskResultParserTest {
    private final AgentTaskResultParser parser = new AgentTaskResultParser();

    @Test
    void acceptsOnlyTheFrozenSuccessShape() {
        AgentTaskResult result = parser.parse("{\"status\":\"SUCCESS\",\"output\":\"done\",\"errorCode\":null,\"retryable\":false}");

        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
        assertEquals("done", result.output());
    }

    @Test
    void extractsJsonObjectFromMarkdownFence() {
        AgentTaskResult result = parser.parse("""
                ```json
                {"status":"SUCCESS","output":"done","errorCode":null,"retryable":false}
                ```
                """);
        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
        assertEquals("done", result.output());
    }

    @Test
    void rejectsNaturalLanguageAndExtraFields() {
        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> parser.parse("done")
        );
        assertEquals(MvpErrorCode.AGENT_INVALID_RESULT, error.getErrorCode());

        error = assertThrows(
                AgentBridgeException.class,
                () -> parser.parse("{\"status\":\"SUCCESS\",\"output\":\"done\",\"errorCode\":null,\"retryable\":false,\"reasoning\":\"hidden\"}")
        );
        assertEquals(MvpErrorCode.AGENT_INVALID_RESULT, error.getErrorCode());
    }

    @Test
    void acceptsOnlyFrozenFailureCodes() {
        AgentTaskResult result = parser.parse(
                "{\"status\":\"FAILURE\",\"output\":null,\"errorCode\":\"TOOL_TIMEOUT\",\"retryable\":true}"
        );
        assertEquals(AgentTaskResult.Status.FAILURE, result.status());
        assertEquals("TOOL_TIMEOUT", result.errorCode());

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> parser.parse("{\"status\":\"FAILURE\",\"output\":null,\"errorCode\":\"UNKNOWN\",\"retryable\":false}")
        );
        assertEquals(MvpErrorCode.AGENT_INVALID_RESULT, error.getErrorCode());
    }

    @Test
    void recoversSuccessWhenOutputContainsUnescapedQuotes() {
        // Mirrors the production failure: comparison text embeds ASCII quotes inside output.
        String dirty = """
                {"status":"SUCCESS","output":"## 对比评价\\n\\nAgent a 如"顺利完成并上线了全新登录页"，偏概括。\\nAgent b 更优。","errorCode":null,"retryable":false}
                """;

        AgentTaskResult result = parser.parse(dirty);

        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
        assertTrue(result.output().contains("顺利完成并上线了全新登录页"));
        assertTrue(result.output().contains("Agent b 更优"));
        assertTrue(result.output().contains("对比评价"));
    }

    @Test
    void recoversSuccessWhenOutputContainsMarkdownAndNewlines() {
        String dirty = """
                {"status":"SUCCESS","output":"## Agent a 与 Agent b 周报对比评价

                **结论：Agent b 的周报质量明显更优。**

                ### 具体评价理由：
                1. 信息颗粒度
                ","errorCode":null,"retryable":false}
                """;

        AgentTaskResult result = parser.parse(dirty);
        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
        assertTrue(result.output().contains("Agent b 的周报质量明显更优"));
    }

    @Test
    void stillAcceptsProperlyEscapedQuotesViaStrictPath() {
        String clean = "{\"status\":\"SUCCESS\",\"output\":\"引用 \\\"原文\\\" 也可\",\"errorCode\":null,\"retryable\":false}";
        AgentTaskResult result = parser.parse(clean);
        assertEquals("引用 \"原文\" 也可", result.output());
    }

    @Test
    void acceptsSuccessOutputAtTwentyThousandCharacters() {
        String output = "a".repeat(20_000);
        AgentTaskResult result = parser.parse(
                "{\"status\":\"SUCCESS\",\"output\":\"" + output + "\",\"errorCode\":null,\"retryable\":false}"
        );
        assertEquals(20_000, result.output().length());
    }

    @Test
    void extractsEnvelopeAfterLeadingMarkdown() {
        AgentTaskResult result = parser.parse("""
                **假设**：基于 Java 后端。
                {"status":"SUCCESS","output":"接口契约基本一致","errorCode":null,"retryable":false}
                """);
        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
        assertEquals("接口契约基本一致", result.output());
    }

    @Test
    void rejectsSuccessOutputOverTwentyThousandCharacters() {
        String output = "a".repeat(20_001);
        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> parser.parse(
                        "{\"status\":\"SUCCESS\",\"output\":\"" + output + "\",\"errorCode\":null,\"retryable\":false}"
                )
        );
        assertEquals(MvpErrorCode.AGENT_INVALID_RESULT, error.getErrorCode());
    }
}
