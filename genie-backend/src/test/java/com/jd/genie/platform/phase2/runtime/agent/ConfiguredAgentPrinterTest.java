package com.jd.genie.platform.phase2.runtime.agent;

import org.junit.jupiter.api.Test;

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
}
