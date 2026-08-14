package com.jd.genie.agent.agent;

import com.jd.genie.agent.dto.Memory;
import com.jd.genie.agent.dto.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
