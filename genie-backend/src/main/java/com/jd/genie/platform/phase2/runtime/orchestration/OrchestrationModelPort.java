package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.route.DispatchDecision;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.MasterPersona;
import com.jd.genie.platform.phase2contract.dto.TeamCapabilitySummary;

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
     * Deepest routing entry. {@code masterPersona} carries the team master overlay, or
     * {@link MasterPersona#none()} when the platform default master is in effect.
     */
    default RouteDecision selectRoute(
            String query,
            String conversationSummary,
            String conversationHistory,
            List<AgentCapabilitySummary> candidates,
            MasterPersona masterPersona
    ) {
        return selectRoute(query, conversationSummary, conversationHistory, candidates);
    }

    /**
     * AUTO dispatch: pick one specialist or one team. Default maps the legacy router
     * onto a single agent when DIRECT, otherwise the first available team.
     */
    default DispatchDecision selectDispatch(
            String query,
            String conversationSummary,
            String conversationHistory,
            List<AgentCapabilitySummary> agents,
            List<TeamCapabilitySummary> teams
    ) {
        List<AgentCapabilitySummary> safeAgents = agents == null ? List.of() : agents;
        List<TeamCapabilitySummary> safeTeams = teams == null ? List.of() : teams;
        RouteDecision route = selectRoute(
                query, conversationSummary, conversationHistory, safeAgents, MasterPersona.none());
        if (route != null && route.route() == RouteDecision.Route.DIRECT && !safeAgents.isEmpty()) {
            AgentCapabilitySummary agent = safeAgents.get(0);
            return DispatchDecision.agent(agent.agentId(), agent.name(), route.reasonCode());
        }
        if (!safeTeams.isEmpty()) {
            TeamCapabilitySummary team = safeTeams.get(0);
            return DispatchDecision.team(team.teamId(), team.name(),
                    route == null ? "MULTI_AGENT" : route.reasonCode());
        }
        if (!safeAgents.isEmpty()) {
            AgentCapabilitySummary agent = safeAgents.get(0);
            return DispatchDecision.agent(agent.agentId(), agent.name(),
                    route == null ? "SINGLE_CAPABILITY" : route.reasonCode());
        }
        throw new IllegalStateException("No dispatch target");
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

    default OrchestrationPlan createPlan(
            String query,
            String conversationHistory,
            String longTermMemory,
            String conversationSummary,
            List<AgentCapabilitySummary> candidates,
            int attemptNo,
            Map<String, String> successfulResultSummaries,
            Map<String, String> failureMetadata
    ) {
        return createPlan(
            query,
            conversationHistory,
            candidates,
            attemptNo,
            successfulResultSummaries,
            failureMetadata
        );
    }

    /**
     * Deepest planning entry. {@code masterPersona} carries the team master overlay, or
     * {@link MasterPersona#none()} when the platform default master is in effect.
     */
    default OrchestrationPlan createPlan(
            String query,
            String conversationHistory,
            String longTermMemory,
            String conversationSummary,
            List<AgentCapabilitySummary> candidates,
            int attemptNo,
            Map<String, String> successfulResultSummaries,
            Map<String, String> failureMetadata,
            MasterPersona masterPersona
    ) {
        return createPlan(
            query,
            conversationHistory,
            longTermMemory,
            conversationSummary,
            candidates,
            attemptNo,
            successfulResultSummaries,
            failureMetadata
        );
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

    default String summarize(
            String query,
            String conversationHistory,
            String longTermMemory,
            String conversationSummary,
            List<SummaryEvidence> evidence
    ) {
        return summarize(query, conversationHistory, evidence);
    }

    /**
     * Deepest summarization entry. {@code masterPersona} carries the team master overlay, or
     * {@link MasterPersona#none()} when the platform default master is in effect.
     */
    default String summarize(
            String query,
            String conversationHistory,
            String longTermMemory,
            String conversationSummary,
            List<SummaryEvidence> evidence,
            MasterPersona masterPersona
    ) {
        return summarize(query, conversationHistory, longTermMemory, conversationSummary, evidence);
    }

    /**
     * Same as {@link #summarize(String, String, String, String, List, MasterPersona)} but may
     * invoke {@code onDelta} with incremental tokens as they arrive. Default implementation
     * emits the full answer once for test fakes.
     */
    default String summarize(
            String query,
            String conversationHistory,
            String longTermMemory,
            String conversationSummary,
            List<SummaryEvidence> evidence,
            MasterPersona masterPersona,
            java.util.function.Consumer<String> onDelta
    ) {
        String answer = summarize(
                query, conversationHistory, longTermMemory, conversationSummary, evidence, masterPersona);
        if (onDelta != null && answer != null && !answer.isBlank()) {
            onDelta.accept(answer);
        }
        return answer;
    }
}
