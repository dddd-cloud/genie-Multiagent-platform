package com.jd.genie.platform.phase2.runtime.plan;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.enums.StepMode;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class OrchestrationPlanValidator {
    private static final int MAX_STEPS = 6;
    private static final int MAX_STEP_ID_LENGTH = 64;
    private static final int MAX_OBJECTIVE_LENGTH = 4_000;
    private static final int MIN_PARALLEL_SUB_TASKS = 2;
    private static final int MAX_PARALLEL_SUB_TASKS = 4;

    public OrchestrationPlan validate(
            OrchestrationPlan plan,
            List<AgentCapabilitySummary> candidates
    ) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty() || plan.steps().size() > MAX_STEPS) {
            throw invalidPlan("Plan must contain one to six steps");
        }

        Set<String> candidateIds = candidateIds(candidates);
        Set<String> completedStepIds = new HashSet<>();
        Set<String> stepIds = new HashSet<>();
        Set<String> subTaskIds = new HashSet<>();
        for (OrchestrationStep step : plan.steps()) {
            validateStep(step, candidateIds, completedStepIds, stepIds, subTaskIds);
            completedStepIds.add(step.stepId());
        }
        return new OrchestrationPlan(plan.steps());
    }

    private Set<String> candidateIds(List<AgentCapabilitySummary> candidates) {
        if (candidates == null) {
            return Set.of();
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .map(AgentCapabilitySummary::agentId)
                .filter(agentId -> !blank(agentId))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void validateStep(
            OrchestrationStep step,
            Set<String> candidateIds,
            Set<String> completedStepIds,
            Set<String> stepIds,
            Set<String> subTaskIds
    ) {
        if (step == null || blank(step.stepId()) || step.stepId().length() > MAX_STEP_ID_LENGTH
                || blank(step.objective()) || step.objective().length() > MAX_OBJECTIVE_LENGTH
                || step.mode() == null || step.inputRefs() == null || step.subTasks() == null
                || !stepIds.add(step.stepId())) {
            throw invalidPlan("Each step must have unique valid identifiers, mode, and objective");
        }

        validateInputReferences(step.inputRefs(), completedStepIds);
        validateMode(step, candidateIds, subTaskIds);
    }

    private void validateInputReferences(List<String> inputRefs, Set<String> completedStepIds) {
        Set<String> references = new HashSet<>();
        for (String inputRef : inputRefs) {
            if (blank(inputRef) || !references.add(inputRef) || !completedStepIds.contains(inputRef)) {
                throw invalidPlan("inputRefs must uniquely reference preceding steps");
            }
        }
    }

    private void validateMode(
            OrchestrationStep step,
            Set<String> candidateIds,
            Set<String> subTaskIds
    ) {
        switch (step.mode()) {
            case MAIN_ONLY -> validateMainOnly(step);
            case SINGLE_AGENT -> validateSingleAgent(step, candidateIds);
            case PARALLEL_AGENTS -> validateParallelAgents(step, candidateIds, subTaskIds);
        }
    }

    private void validateMainOnly(OrchestrationStep step) {
        if (step.agentId() != null || !step.subTasks().isEmpty()) {
            throw invalidPlan("MAIN_ONLY must not include agentId or subTasks");
        }
    }

    private void validateSingleAgent(OrchestrationStep step, Set<String> candidateIds) {
        if (blank(step.agentId()) || !candidateIds.contains(step.agentId()) || !step.subTasks().isEmpty()) {
            throw invalidPlan("SINGLE_AGENT requires a candidate agentId and no subTasks");
        }
    }

    private void validateParallelAgents(
            OrchestrationStep step,
            Set<String> candidateIds,
            Set<String> subTaskIds
    ) {
        int size = step.subTasks().size();
        if (step.agentId() != null || size < MIN_PARALLEL_SUB_TASKS || size > MAX_PARALLEL_SUB_TASKS) {
            throw invalidPlan("PARALLEL_AGENTS requires two to four subTasks and no agentId");
        }
        for (OrchestrationSubTask subTask : step.subTasks()) {
            if (subTask == null || blank(subTask.subTaskId()) || subTask.subTaskId().length() > MAX_STEP_ID_LENGTH
                    || blank(subTask.objective()) || subTask.objective().length() > MAX_OBJECTIVE_LENGTH
                    || blank(subTask.agentId()) || !candidateIds.contains(subTask.agentId())
                    || !subTaskIds.add(subTask.subTaskId())) {
                throw invalidPlan("Each subTask must have a unique valid candidate agentId and objective");
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
