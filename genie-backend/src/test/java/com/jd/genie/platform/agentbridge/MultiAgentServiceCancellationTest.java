package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.pending;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.respond;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.returning;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.scenario;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiAgentServiceCancellationTest {

    @Test
    void disconnectBeforeCallBindingCancelsWithoutEnqueueing() {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                pending(),
                returning(ObserverTestSupport.event("unused", true))
        );
        assertTrue(scenario.observer().markStreaming());
        assertTrue(scenario.observer().onClientDisconnected());

        scenario.service().searchForAgentRequest(
                MultiAgentServiceTestSupport.request(),
                scenario.observer(),
                scenario.cancellableCall()
        );

        MultiAgentServiceTestSupport.ScriptedCall call = scenario.calls().lastCall();
        assertTrue(scenario.cancellableCall().isCancellationRequested());
        assertTrue(call.isCanceled());
        assertFalse(call.isExecuted());
        assertEquals(1, call.cancellationCount());
        assertInterruptedExactlyOnce(scenario);
    }

    @Test
    void disconnectAfterCallBindingCancelsAndIgnoresLateCallbacks() throws Exception {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                pending(),
                returning(ObserverTestSupport.event("unused", true))
        );
        scenario.start();
        MultiAgentServiceTestSupport.ScriptedCall call = scenario.calls().lastCall();
        assertTrue(call.isExecuted());

        assertTrue(scenario.observer().onClientDisconnected());
        assertFalse(scenario.observer().onClientDisconnected());
        call.signalFailure(new IOException("canceled"));
        MultiAgentServiceTestSupport.TrackingResponseBody lateBody =
                MultiAgentServiceTestSupport.trackedStream("data: [DONE]\n\n");
        call.signalResponse(200, lateBody);

        assertTrue(call.isCanceled());
        assertEquals(1, call.cancellationCount());
        assertTrue(lateBody.isClosed());
        assertInterruptedExactlyOnce(scenario);
    }

    @Test
    void successfulCompletionIgnoresLateErrorAndCompletionSignals() {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(
                        200,
                        stream("data: {\"messageType\":\"result\",\"finish\":true}\n\n")
                ),
                returning(ObserverTestSupport.event("answer", true))
        );

        scenario.start();
        scenario.calls().lastCall().signalFailure(new IOException("late failure"));

        assertFalse(scenario.observer().onCompleted());
        assertFalse(scenario.observer().onError(new IOException("late observer error")));
        assertFalse(scenario.observer().onClientDisconnected());
        assertEquals(ConversationStreamObserver.TerminalState.COMPLETED, scenario.observer().state());
        assertEquals(0, scenario.calls().lastCall().cancellationCount());
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.COMPLETE
        ), callTypes(scenario));
        assertEquals(1, scenario.channel().completionCount());
    }

    @Test
    void clientSendFailureCancelsBoundCallAndInterrupts() {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(
                        200,
                        stream("data: {\"messageType\":\"agent_stream\",\"finish\":false}\n\n")
                ),
                returning(ObserverTestSupport.event("partial", false))
        );
        scenario.channel().failEventSendWith(new IOException("client disconnected"));

        scenario.start();

        assertEquals(1, scenario.calls().lastCall().cancellationCount());
        assertInterruptedExactlyOnce(scenario);
    }

    private void assertInterruptedExactlyOnce(MultiAgentServiceTestSupport.Scenario scenario) {
        assertEquals(ConversationStreamObserver.TerminalState.INTERRUPTED, scenario.observer().state());
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.INTERRUPT
        ), callTypes(scenario));
        assertEquals("CLIENT_DISCONNECTED", scenario.port().getCalls().get(1)
                .failureCommand()
                .errorCode());
        assertEquals(1, scenario.channel().completionCount());
    }

    private List<FakeConversationExecutionPort.CallType> callTypes(
            MultiAgentServiceTestSupport.Scenario scenario
    ) {
        return scenario.port().getCalls().stream()
                .map(FakeConversationExecutionPort.CallRecord::type)
                .toList();
    }
}
