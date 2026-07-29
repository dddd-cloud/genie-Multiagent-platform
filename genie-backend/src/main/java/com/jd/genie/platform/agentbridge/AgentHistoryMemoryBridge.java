package com.jd.genie.platform.agentbridge;

import com.jd.genie.agent.dto.Memory;
import com.jd.genie.model.req.AgentRequest;

import java.util.List;
import java.util.Objects;

public final class AgentHistoryMemoryBridge {
    private final AgentHistoryMessageMapper historyMapper = new AgentHistoryMessageMapper();

    public void appendTo(Memory memory, List<AgentRequest.Message> historyMessages) {
        Objects.requireNonNull(memory, "memory")
                .addMessages(historyMapper.toMemoryMessages(historyMessages));
    }
}
