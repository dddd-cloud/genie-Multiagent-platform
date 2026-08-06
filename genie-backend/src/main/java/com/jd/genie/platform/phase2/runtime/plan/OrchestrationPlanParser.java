package com.jd.genie.platform.phase2.runtime.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.List;
import java.util.Set;

public final class OrchestrationPlanParser {
    private static final Set<String> ROOT_FIELDS = Set.of("steps");
    private static final Set<String> STEP_FIELDS = Set.of("stepId", "agentId", "objective", "inputRefs");

    private final ObjectMapper objectMapper;

    public OrchestrationPlanParser() {
        this(new ObjectMapper());
    }

    OrchestrationPlanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrchestrationPlan parse(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root == null || !root.isObject() || !hasExactlyFields(root, ROOT_FIELDS) || !root.path("steps").isArray()) {
                throw invalidPlan();
            }
            for (JsonNode step : root.path("steps")) {
                if (!step.isObject() || !hasExactlyFields(step, STEP_FIELDS)
                        || !step.path("stepId").isTextual()
                        || !step.path("agentId").isTextual()
                        || !step.path("objective").isTextual()
                        || !step.path("inputRefs").isArray()
                        || hasNonTextInputReference(step.path("inputRefs"))) {
                    throw invalidPlan();
                }
            }
            List<OrchestrationStep> steps = objectMapper.readValue(
                    root.path("steps").traverse(objectMapper),
                    new TypeReference<>() { }
            );
            return new OrchestrationPlan(steps);
        } catch (AgentBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw invalidPlan();
        }
    }

    private boolean hasNonTextInputReference(JsonNode inputRefs) {
        for (JsonNode inputRef : inputRefs) {
            if (!inputRef.isTextual()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExactlyFields(JsonNode node, Set<String> expected) {
        if (node.size() != expected.size()) {
            return false;
        }
        java.util.Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!expected.contains(fields.next())) {
                return false;
            }
        }
        return true;
    }

    private AgentBridgeException invalidPlan() {
        return new AgentBridgeException(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, "Plan must be a fixed JSON object");
    }
}
