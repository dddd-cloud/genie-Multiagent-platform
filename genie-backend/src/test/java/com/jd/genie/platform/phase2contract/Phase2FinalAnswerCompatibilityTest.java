package com.jd.genie.platform.phase2contract;

import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.agentbridge.FinalAnswerExtractor;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.OrchestrationEvent;
import com.jd.genie.platform.phase2contract.enums.OrchestrationCompletionStatus;
import com.jd.genie.platform.phase2contract.enums.OrchestrationEventType;
import com.jd.genie.platform.phase2contract.enums.OrchestrationRoute;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Phase2FinalAnswerCompatibilityTest {

    private final FinalAnswerExtractor extractor = new FinalAnswerExtractor();

    @Test
    void extractsFinalResponseAllAndIgnoresProgressEvents() {
        GptProcessResult route = progress(1, OrchestrationEventType.ROUTE_SELECTED);
        GptProcessResult plan = progress(2, OrchestrationEventType.PLAN_CREATED);
        GptProcessResult finalEvent = finalResult("The definitive answer");

        String answer = extractor.extract(List.of(route, plan, finalEvent));
        assertEquals("The definitive answer", answer);
    }

    @Test
    void emptyFinalAnswerDoesNotPretendSuccess() {
        GptProcessResult route = progress(1, OrchestrationEventType.ROUTE_SELECTED);
        GptProcessResult emptyFinal = finalResult("   ");
        AgentBridgeException ex = assertThrows(
            AgentBridgeException.class,
            () -> extractor.extract(List.of(route, emptyFinal))
        );
        assertEquals(MvpErrorCode.AGENT_NO_FINAL_EVENT, ex.getErrorCode());
    }

    private static GptProcessResult progress(long sequence, OrchestrationEventType type) {
        OrchestrationEvent event = new OrchestrationEvent(
            1,
            "request-fixture:" + sequence,
            sequence,
            type,
            "request-fixture",
            "run-fixture",
            type == OrchestrationEventType.PLAN_CREATED ? 1 : null,
            null,
            null,
            null,
            type == OrchestrationEventType.ROUTE_SELECTED ? OrchestrationRoute.ORCHESTRATED : null,
            type == OrchestrationEventType.ROUTE_SELECTED ? "MULTI_STEP" : null,
            null,
            type == OrchestrationEventType.PLAN_CREATED
                ? List.of(new com.jd.genie.platform.phase2contract.dto.OrchestrationPlanStepView(
                "step-1", "agent-1", "Agent", "do", List.of()))
                : List.of(),
            null
        );
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("orchestrationEvent", event);
        return GptProcessResult.builder()
            .status("success")
            .response("")
            .responseAll("")
            .finished(false)
            .resultMap(resultMap)
            .responseType("json")
            .packageType("orchestration")
            .traceId("trace-fixture")
            .reqId("request-fixture")
            .build();
    }

    private static GptProcessResult finalResult(String text) {
        OrchestrationEvent event = new OrchestrationEvent(
            1,
            "request-fixture:9",
            9L,
            OrchestrationEventType.FINAL_RESPONSE,
            "request-fixture",
            "run-fixture",
            1,
            null,
            null,
            null,
            OrchestrationRoute.ORCHESTRATED,
            null,
            null,
            List.of(),
            OrchestrationCompletionStatus.SUCCESS
        );
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("orchestrationEvent", event);
        return GptProcessResult.builder()
            .status("success")
            .response(text)
            .responseAll(text)
            .finished(true)
            .resultMap(resultMap)
            .responseType("markdown")
            .packageType("result")
            .traceId("trace-fixture")
            .reqId("request-fixture")
            .build();
    }
}
