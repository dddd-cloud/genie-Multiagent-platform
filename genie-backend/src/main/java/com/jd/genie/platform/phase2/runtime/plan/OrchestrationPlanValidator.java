package com.jd.genie.platform.phase2.runtime.plan;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OrchestrationPlanValidator {
    private static final int MAX_STEPS = 6;
    private static final int MAX_STEP_ID_LENGTH = 64;
    private static final int MAX_OBJECTIVE_LENGTH = 4_000;
    private static final int MAX_AGENT_APPEARANCES = 2;

    public OrchestrationPlan validate(
            OrchestrationPlan plan,
            List<AgentCapabilitySummary> candidates
    ) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty() || plan.steps().size() > MAX_STEPS) {
            throw invalidPlan("Plan must contain one to six steps");
        }
        Set<String> candidateIds = candidates.stream().map(AgentCapabilitySummary::agentId).collect(java.util.stream.Collectors.toSet());
        Set<String> completedStepIds = new HashSet<>();
        Set<String> stepIds = new HashSet<>();
        java.util.Map<String, Integer> agentCounts = new java.util.HashMap<>();
        for (OrchestrationStep step : plan.steps()) {
            validateStep(step, candidateIds, completedStepIds, stepIds, agentCounts);
            completedStepIds.add(step.stepId());
        }
        return new OrchestrationPlan(List.copyOf(plan.steps()));
    }

    private void validateStep(
            OrchestrationStep step,
            Set<String> candidateIds,
            Set<String> completedStepIds,
            Set<String> stepIds,
            java.util.Map<String, Integer> agentCounts
    ) {
        if (step == null || blank(step.stepId()) || step.stepId().length() > MAX_STEP_ID_LENGTH
                || blank(step.agentId()) || blank(step.objective()) || step.objective().length() > MAX_OBJECTIVE_LENGTH
                || step.inputRefs() == null || !stepIds.add(step.stepId())) {
            throw invalidPlan("Each step must have unique valid identifiers and objective");
        }
        if (!candidateIds.contains(step.agentId())) {
            throw invalidPlan("Step agentId must be in the candidate snapshot");
        }
        if (agentCounts.merge(step.agentId(), 1, Integer::sum) > MAX_AGENT_APPEARANCES) {
            throw invalidPlan("An agent may appear at most twice per attempt");
        }
        Set<String> inputRefs = new HashSet<>();
        for (String inputRef : step.inputRefs()) {
            if (blank(inputRef) || !inputRefs.add(inputRef) || !completedStepIds.contains(inputRef)) {
                throw invalidPlan("inputRefs must uniquely reference preceding steps");
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private AgentBridgeException invalidPlan(String message) {
        return new AgentBridgeException(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, message);
    }
}
