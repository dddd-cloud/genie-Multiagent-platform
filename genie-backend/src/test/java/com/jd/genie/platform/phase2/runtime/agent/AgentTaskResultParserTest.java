package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
