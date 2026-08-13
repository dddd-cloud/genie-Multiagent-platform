package com.jd.genie.platform.phase2.runtime.agent;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;
import com.jd.genie.platform.phase2.runtime.trace.OrchestrationTraceChannel;
import com.jd.genie.platform.phase2contract.BrowserSkillExecutionContract;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionSignal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BrowserSkillControlPacketTest {

    @Test
    void forwardsOnlyFrozenSignalsAsFixedSkillExecutionPackets() {
        ConversationStreamObserver observer = mock(ConversationStreamObserver.class);
        OrchestrationTraceChannel traceChannel = mock(OrchestrationTraceChannel.class);
        ConfiguredAgentPrinter printer = new ConfiguredAgentPrinter(
                traceChannel, observer, 1, "step-1", "agent-a", "Agent A"
        );
        BrowserSkillExecutionSignal signal = new BrowserSkillExecutionSignal(
                BrowserSkillExecutionContract.SCHEMA_VERSION,
                "exec-1",
                "skill-1",
                "main",
                "hash-123",
                30_000L
        );

        printer.send(BrowserSkillExecutionContract.PRINTER_MESSAGE_TYPE, signal);

        ArgumentCaptor<GptProcessResult> captor = ArgumentCaptor.forClass(GptProcessResult.class);
        verify(observer).onEvent(captor.capture());
        GptProcessResult packet = captor.getValue();
        assertEquals(BrowserSkillExecutionContract.SSE_PACKAGE_TYPE, packet.getPackageType());
        assertFalse(packet.isFinished());
        assertEquals("running", packet.getStatus());
        Map<?, ?> resultMap = packet.getResultMap();
        assertSame(signal, resultMap.get(BrowserSkillExecutionContract.RESULT_MAP_KEY));
        // The control packet is never recorded as a trace or thought.
        verify(traceChannel, never()).emitStep(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void refusesNonSignalMessagesAndWrongMessageTypes() {
        ConversationStreamObserver observer = mock(ConversationStreamObserver.class);
        ConfiguredAgentPrinter printer = new ConfiguredAgentPrinter(
                null, observer, 1, "step-1", "agent-a", "Agent A"
        );
        BrowserSkillExecutionSignal signal = new BrowserSkillExecutionSignal(1, "exec-1", "skill-1", "main", "hash", 100L);

        // Wrong message type with a signal instance must not leak.
        printer.send("tool_thought", signal);
        // Frozen message type with a non-signal instance must not leak.
        printer.send(BrowserSkillExecutionContract.PRINTER_MESSAGE_TYPE, "just a string");
        // Plain text never becomes a control packet.
        printer.send("tool_thought", "normal thinking text");

        verify(observer, never()).onEvent(any(GptProcessResult.class));
    }
}
