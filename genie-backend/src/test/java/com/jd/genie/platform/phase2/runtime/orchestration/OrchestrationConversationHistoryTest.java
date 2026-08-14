package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.model.req.AgentRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrchestrationConversationHistoryTest {

    @Test
    void formatsPriorUserAndAssistantTurnsAndSkipsBlankOrSystemMessages() {
        String formatted = OrchestrationConversationHistory.format(java.util.Arrays.asList(
                AgentRequest.Message.builder().role("user").content("茅台市场规模多大").build(),
                AgentRequest.Message.builder().role("assistant").content("约三千亿。").build(),
                AgentRequest.Message.builder().role("system").content("ignore me").build(),
                AgentRequest.Message.builder().role("user").content("  ").build(),
                null
        ));

        assertEquals("user: 茅台市场规模多大\nassistant: 约三千亿。", formatted);
    }

    @Test
    void emptyHistoryFormatsToEmptyString() {
        assertEquals("", OrchestrationConversationHistory.format(null));
        assertEquals("", OrchestrationConversationHistory.format(List.of()));
    }
}
