package com.jd.genie.platform.agentbridge;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.contract.MessageCompletionCommand;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalAnswerPersistenceTest {
    private final OrchestrationEventMapper mapper = new OrchestrationEventMapper();

    @Test
    void persistsTheOnlyFinalResponseThroughTheExistingObserverCompatibilityChain() {
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel = new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver observer = new ConversationStreamObserver(
                new StreamPersistenceObserver(port, ObserverTestSupport.USER, ObserverTestSupport.ASSISTANT_MESSAGE_ID),
                channel
        );
        GptProcessResult progress = mapper.progress(
                "request-1", "run-1", 1, "STEP_COMPLETED", Map.of("stepId", "step-1"), List.of()
        );
        GptProcessResult finalResponse = mapper.finalResponse(
                "request-1", "run-1", 2, "final answer", "SUCCESS"
        );

        assertTrue(observer.markStreaming());
        assertTrue(observer.onEvent(progress));
        assertTrue(observer.onEvent(finalResponse));
        assertTrue(observer.onCompleted());

        List<GptProcessResult> delivered = channel.events();
        assertEquals(1, delivered.stream().filter(GptProcessResult::isFinished).count());
        assertEquals("final answer", new FinalAnswerExtractor().extract(delivered));
        MessageCompletionCommand completion = port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE)
                .map(FakeConversationExecutionPort.CallRecord::completionCommand)
                .findFirst()
                .orElseThrow();
        assertEquals("final answer", completion.finalContent());
        assertEquals(1, completion.payloadVersion());
        assertTrue(completion.snapshotJson().contains("\"payloadVersion\":1"));
        assertTrue(completion.snapshotJson().contains("FINAL_RESPONSE"));
    }
}
