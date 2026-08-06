package com.jd.genie.platform.phase2.runtime.event;

import com.jd.genie.model.response.GptProcessResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationEventMapperTest {
    private final OrchestrationEventMapper mapper = new OrchestrationEventMapper();

    @Test
    void projectsProgressWithStablePerRequestEventIdentity() {
        GptProcessResult result = mapper.progress("request-1", 2L, "STEP_STARTED", Map.of("stepId", "step-1"));

        assertFalse(result.isFinished());
        assertEquals("orchestration", result.getPackageType());
        Map<?, ?> event = (Map<?, ?>) result.getResultMap().get("orchestrationEvent");
        assertEquals("request-1:2", event.get("eventId"));
        assertEquals(2L, event.get("sequence"));
    }

    @Test
    void projectsFrozenV1FieldsWithoutDetailsEnvelope() {
        GptProcessResult result = mapper.progress(
                "request-1",
                "run-1",
                2L,
                "STEP_STARTED",
                Map.of("stepId", "step-1", "agentId", "agent-a", "agentName", "A", "attemptNo", 1),
                java.util.List.of()
        );

        Map<?, ?> event = (Map<?, ?>) result.getResultMap().get("orchestrationEvent");
        assertEquals(1, event.get("schemaVersion"));
        assertEquals("request-1", event.get("requestId"));
        assertEquals("run-1", event.get("runId"));
        assertEquals("step-1", event.get("stepId"));
        assertEquals("agent-a", event.get("agentId"));
        assertFalse(event.containsKey("details"));
        assertTrue(event.containsKey("steps"));
    }

    @Test
    void projectsFinalResponseAsTheOnlyTerminalShape() {
        GptProcessResult result = mapper.finalResponse(
                "request-1",
                "run-1",
                3L,
                "completed answer",
                "SUCCESS"
        );
        Map<?, ?> event = (Map<?, ?>) result.getResultMap().get("orchestrationEvent");

        assertTrue(result.isFinished());
        assertEquals("result", result.getPackageType());
        assertEquals("completed answer", result.getResponseAll());
        assertEquals("FINAL_RESPONSE", event.get("eventType"));
        assertEquals("SUCCESS", event.get("completionStatus"));
        assertEquals("request-1:3", event.get("eventId"));
    }
}
