package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;

import java.util.List;
import java.util.Map;

/**
 * The implementation owns transport and redaction so orchestration input is never emitted by the runtime.
 */
public interface OrchestrationModelPort {
    enum ReviewDecision {
        COMPLETE,
        RETRY,
        FALLBACK
    }

    RouteDecision selectRoute(String query, String conversationSummary, List<AgentCapabilitySummary> candidates);

    /**
     * Reviews only a single step's safe execution boundary. Implementations must not require raw prompts or tools.
     */
    default ReviewDecision review(
            String objective,
            String safeResult,
            String errorCode,
            boolean retryable,
            int retryNo
    ) {
        if (errorCode == null || errorCode.isBlank()) {
            return ReviewDecision.COMPLETE;
        }
        return retryable && retryNo == 0 ? ReviewDecision.RETRY : ReviewDecision.FALLBACK;
    }

    OrchestrationPlan createPlan(
            String query,
            List<AgentCapabilitySummary> candidates,
            int attemptNo,
            Map<String, String> successfulResultSummaries,
            Map<String, String> failureMetadata
    );

    String summarize(
            String query,
            Map<String, String> successfulResultSummaries,
            Map<String, String> failureMetadata
    );
}
