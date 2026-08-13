package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.respond;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.returning;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.scenario;
import static com.jd.genie.platform.agentbridge.MultiAgentServiceTestSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V1RegressionTest {

    @Test
    void v1AutoAgentStreamKeepsItsOwnLifecycleWithoutOrchestrationPackages() {
        GptProcessResult running = ObserverTestSupport.event("partial", false);
        GptProcessResult completed = ObserverTestSupport.event("v1 answer", true);
        java.util.concurrent.atomic.AtomicInteger handled = new java.util.concurrent.atomic.AtomicInteger();
        MultiAgentServiceTestSupport.Scenario scenario = scenario(
                respond(
                        200,
                        stream("data: {\"messageType\":\"agent_stream\",\"finish\":false}\n\n"
                                + "data: {\"messageType\":\"result\",\"finish\":true}\n\n")
                ),
                (request, response, responses, eventResult) ->
                        handled.getAndIncrement() == 0 ? running : completed
        );

        scenario.start();

        assertEquals(ConversationStreamObserver.TerminalState.COMPLETED, scenario.observer().state());
        assertEquals(List.of(running, completed), scenario.channel().events());
        assertTrue(scenario.channel().events().stream()
                .noneMatch(event -> "orchestration".equals(event.getPackageType())));
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.MARK_STREAMING,
                FakeConversationExecutionPort.CallType.COMPLETE
        ), scenario.port().getCalls().stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
        MessageCompletionCommand completion = scenario.port().getCalls().get(1).completionCommand();
        assertEquals("v1 answer", completion.finalContent());
        assertEquals(1, scenario.channel().completionCount());
    }
}
