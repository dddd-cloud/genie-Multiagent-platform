package com.jd.genie.platform.agentbridge;

import com.jd.genie.handler.AgentResponseHandler;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MessageFailureCommand;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.interruptedStream;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.respond;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.returning;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.scenario;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiAgentServiceFailureTest {

    @Test
    void connectionFailureUsesDownstreamErrorAndTerminatesStream() {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                MultiAgentServiceTestSupport.connectionFailure(),
                returning(ObserverTestSupport.event("unused", true))
        );

        scenario.start();

        assertFailed(scenario, MvpErrorCode.AGENT_DOWNSTREAM_ERROR);
    }

    @Test
    void nonSuccessHttpStatusUsesDownstreamError() {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(500, stream("ignored")),
                returning(ObserverTestSupport.event("unused", true))
        );

        scenario.start();

        assertFailed(scenario, MvpErrorCode.AGENT_DOWNSTREAM_ERROR);
    }

    @Test
    void nullBodyUsesDownstreamError() {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(200, null),
                returning(ObserverTestSupport.event("unused", true))
        );

        scenario.start();

        assertFailed(scenario, MvpErrorCode.AGENT_DOWNSTREAM_ERROR);
    }

    @Test
    void nonSseAndEmptySseResponsesUseDownstreamError() {
        List<ResponseBody> bodies = List.of(
                ResponseBody.create(MediaType.parse("application/json"), "{}"),
                stream(""),
                stream(": comment-only\n\n")
        );

        for (ResponseBody body : bodies) {
            MultiAgentServiceTestSupport.Scenario scenario = scenario(
                    respond(200, body),
                    returning(ObserverTestSupport.event("unused", true))
            );

            scenario.start();

            assertFailed(scenario, MvpErrorCode.AGENT_DOWNSTREAM_ERROR);
        }
    }

    @Test
    void malformedJsonUsesStreamInterrupted() {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(200, stream("data: {malformed}\n\n")),
                returning(ObserverTestSupport.event("unused", true))
        );

        scenario.start();

        assertFailed(scenario, MvpErrorCode.AGENT_STREAM_INTERRUPTED);
    }

    @Test
    void absentOrFailingHandlerUsesStreamInterrupted() {
        List<AgentResponseHandler> handlers = java.util.Arrays.asList(
                null,
                (request, response, responses, eventResult) -> null,
                (request, response, responses, eventResult) -> {
                    throw new IllegalStateException("handler failure");
                }
        );

        for (AgentResponseHandler handler : handlers) {
            MultiAgentServiceTestSupport.Scenario scenario = scenario(
                    respond(
                            200,
                            stream("data: {\"messageType\":\"agent_stream\",\"finish\":false}\n\n")
                    ),
                    handler
            );

            scenario.start();

            assertFailed(scenario, MvpErrorCode.AGENT_STREAM_INTERRUPTED);
        }
    }

    @Test
    void readFailureAfterStreamEstablishmentUsesStreamInterrupted() {
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(
                        200,
                        interruptedStream(
                                "data: {\"messageType\":\"agent_stream\",\"finish\":false}\n\n"
                        )
                ),
                returning(ObserverTestSupport.event("partial", false))
        );

        scenario.start();

        assertFailed(scenario, MvpErrorCode.AGENT_STREAM_INTERRUPTED);
        assertEquals(1, scenario.observer().bufferedEventCount());
    }

    @Test
    void eofAndDoneWithoutSuccessfulFinalEventUseNoFinalEvent() {
        List<String> streams = List.of(
                "data: {\"messageType\":\"agent_stream\",\"finish\":false}\n\n",
                "data: {\"messageType\":\"agent_stream\",\"finish\":false}\n\n"
                        + "data: [DONE]\n\n",
                "data: [DONE]\n\n"
        );

        for (String events : streams) {
            MultiAgentServiceTestSupport.Scenario scenario = scenario(
                    respond(200, stream(events)),
                    returning(ObserverTestSupport.event("partial", false))
            );

            scenario.start();

            assertFailed(scenario, MvpErrorCode.AGENT_NO_FINAL_EVENT);
        }
    }

    @Test
    void failedFinalEventDoesNotCompleteMessage() {
        GptProcessResult failedFinal = ObserverTestSupport.event("failed", true);
        failedFinal.setStatus("failed");
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(
                        200,
                        stream("data: {\"messageType\":\"result\",\"finish\":true}\n\n")
                ),
                returning(failedFinal)
        );

        scenario.start();

        assertFailed(scenario, MvpErrorCode.AGENT_DOWNSTREAM_ERROR);
    }

    @Test
    void snapshotFailureUsesExistingFailurePathAndNeverCompletes() {
        GptProcessResult oversizedFinal = ObserverTestSupport.event("x".repeat(5_000), true);
        MultiAgentServiceTestSupport.Scenario scenario = MultiAgentServiceTestSupport.scenario(
                respond(
                        200,
                        stream("data: {\"messageType\":\"result\",\"finish\":true}\n\n")
                ),
                returning(oversizedFinal),
                MultiAgentServiceTestSupport.INTERNAL_TOKEN,
                512
        );

        scenario.start();

        assertFailed(scenario, MvpErrorCode.SNAPSHOT_TOO_LARGE);
    }

    @Test
    void missingInternalTokenFailsBeforeCreatingHttpCall() {
        MultiAgentServiceTestSupport.Scenario scenario = MultiAgentServiceTestSupport.scenario(
                MultiAgentServiceTestSupport.pending(),
                returning(ObserverTestSupport.event("unused", true)),
                " ",
                SnapshotPruner.DEFAULT_MAX_BYTES
        );
        assertTrue(scenario.observer().markStreaming());

        AgentBridgeException error = assertThrows(
                AgentBridgeException.class,
                () -> scenario.service().searchForAgentRequest(
                        MultiAgentServiceTestSupport.request(),
                        scenario.observer(),
                        scenario.cancellableCall()
                )
        );

        assertEquals(MvpErrorCode.INTERNAL_ERROR, error.getErrorCode());
        assertNull(scenario.calls().lastCall());
    }

    private void assertFailed(
            MultiAgentServiceTestSupport.Scenario scenario,
            MvpErrorCode expectedCode
    ) {
        assertEquals(ConversationStreamObserver.TerminalState.FAILED, scenario.observer().state());
        assertEquals(1, scenario.channel().completionCount());
        assertEquals(1, scenario.channel().failures().size());
        assertEquals(expectedCode, scenario.channel().failures().get(0).errorCode());
        assertEquals(1, scenario.calls().lastCall().cancellationCount());
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.FAIL
        ), scenario.port().getCalls().stream()
                .map(FakeConversationExecutionPort.CallRecord::type)
                .toList());
        MessageFailureCommand command = scenario.port().getCalls().get(1).failureCommand();
        assertEquals(expectedCode.name(), command.errorCode());
        assertFalse(scenario.port().getCalls().stream()
                .anyMatch(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE));
    }
}
