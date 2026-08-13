package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredAgentResultStrictnessTest {

    private final AgentTaskResultParser parser = new AgentTaskResultParser();

    @Test
    void plainNaturalLanguageIsRejectedAsInvalidResult() {
        assertInvalid("我觉得任务完成了，结果很好。");
        assertInvalid("Here is the answer in plain prose.");
    }

    @Test
    void multipleJsonObjectsAreRejectedAsInvalidResult() {
        assertInvalid("{\"status\":\"SUCCESS\",\"output\":\"one\",\"errorCode\":null,\"retryable\":false} "
                + "{\"status\":\"SUCCESS\",\"output\":\"two\",\"errorCode\":null,\"retryable\":false}");
    }

    @Test
    void unknownErrorCodesAreRejectedAsInvalidResult() {
        assertInvalid("{\"status\":\"FAILURE\",\"output\":null,\"errorCode\":\"WEIRD_CODE\",\"retryable\":false}");
    }

    @Test
    void successfulResultWithEmptyOutputIsRejectedAsInvalidResult() {
        assertInvalid("{\"status\":\"SUCCESS\",\"output\":\"\",\"errorCode\":null,\"retryable\":false}");
        assertInvalid("{\"status\":\"SUCCESS\",\"output\":\"   \",\"errorCode\":null,\"retryable\":false}");
    }

    @Test
    void frozenSuccessAndFailureEnvelopesAreAccepted() {
        AgentTaskResult success = parser.parse(
                "{\"status\":\"SUCCESS\",\"output\":\"发现 3 条证据\",\"errorCode\":null,\"retryable\":false}"
        );
        assertEquals(AgentTaskResult.Status.SUCCESS, success.status());
        assertEquals("发现 3 条证据", success.output());
        assertFalse(success.retryable());

        AgentTaskResult failure = parser.parse(
                "{\"status\":\"FAILURE\",\"output\":null,\"errorCode\":\"TOOL_TIMEOUT\",\"retryable\":true}"
        );
        assertEquals(AgentTaskResult.Status.FAILURE, failure.status());
        assertEquals("TOOL_TIMEOUT", failure.errorCode());
        assertTrue(failure.retryable());
    }

    @Test
    void extraFieldsAreRejectedAsInvalidResult() {
        assertInvalid("{\"status\":\"SUCCESS\",\"output\":\"ok\",\"errorCode\":null,\"retryable\":false,\"thought\":\"secret\"}");
    }

    private void assertInvalid(String raw) {
        AgentBridgeException error = assertThrows(AgentBridgeException.class, () -> parser.parse(raw));
        assertEquals(MvpErrorCode.AGENT_INVALID_RESULT, error.getErrorCode());
    }
}
