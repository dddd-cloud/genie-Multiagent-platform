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

    default RouteDecision selectRoute(
            String query,
            String conversationSummary,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates
    ) {
        return selectRoute(query, conversationSummary, candidates);
    }

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

    default OrchestrationPlan createPlan(
            String query,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates,
            int attemptNo,
            Map<String, String> successfulResultSummaries,
            Map<String, String> failureMetadata
    ) {
        return createPlan(query, candidates, attemptNo, successfulResultSummaries, failureMetadata);
    }

    String summarize(
            String query,
            Map<String, String> successfulResultSummaries,
            Map<String, String> failureMetadata
    );

    /**
     * Preferred summarization entry: named specialist evidence plus the original user question.
     * Default converts evidence back to the map form for existing test fakes.
     */
    default String summarize(String query, List<SummaryEvidence> evidence) {
        Map<String, String> successes = new java.util.LinkedHashMap<>();
        Map<String, String> failures = new java.util.LinkedHashMap<>();
        if (evidence != null) {
            for (SummaryEvidence item : evidence) {
                if (item == null || item.stepId() == null || item.stepId().isBlank()) {
                    continue;
                }
                if (item.failed()) {
                    failures.put(item.stepId(), item.errorCode());
                } else if (item.output() != null) {
                    successes.put(item.stepId(), item.output());
                }
            }
        }
        return summarize(query, successes, failures);
    }

    default String summarize(String query, String conversationHistory, List<SummaryEvidence> evidence) {
        return summarize(query, evidence);
    }
}
