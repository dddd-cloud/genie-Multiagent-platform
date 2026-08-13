package com.jd.genie.platform.phase2.runtime.plan;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.enums.StepMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class OrchestrationPlanParser {
    private static final Set<String> ROOT_FIELDS = Set.of("steps");
    private static final Set<String> STEP_FIELDS = Set.of(
            "stepId", "mode", "objective", "inputRefs", "agentId", "subTasks"
    );
    private static final Set<String> SUB_TASK_FIELDS = Set.of("subTaskId", "agentId", "objective");

    private final ObjectMapper objectMapper;

    public OrchestrationPlanParser() {
        this(new ObjectMapper());
    }

    OrchestrationPlanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public OrchestrationPlan parse(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root == null || !root.isObject() || !hasExactlyFields(root, ROOT_FIELDS) || !root.path("steps").isArray()) {
                throw invalidPlan();
            }
            return new OrchestrationPlan(parseSteps(root.path("steps")));
        } catch (AgentBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw invalidPlan();
        }
    }

    private List<OrchestrationStep> parseSteps(JsonNode stepsNode) {
        List<OrchestrationStep> steps = new ArrayList<>();
        for (JsonNode stepNode : stepsNode) {
            steps.add(parseStep(stepNode));
        }
        return List.copyOf(steps);
    }

    private OrchestrationStep parseStep(JsonNode stepNode) {
        if (!stepNode.isObject() || !hasExactlyFields(stepNode, STEP_FIELDS)
                || !stepNode.path("stepId").isTextual()
                || !stepNode.path("mode").isTextual()
                || !stepNode.path("objective").isTextual()
                || !stepNode.path("inputRefs").isArray()
                || !isNullableText(stepNode.path("agentId"))
                || !stepNode.path("subTasks").isArray()) {
            throw invalidPlan();
        }
        return new OrchestrationStep(
                stepNode.path("stepId").asText(),
                parseMode(stepNode.path("mode")),
                stepNode.path("objective").asText(),
                parseInputRefs(stepNode.path("inputRefs")),
                nullableText(stepNode.path("agentId")),
                parseSubTasks(stepNode.path("subTasks"))
        );
    }

    private StepMode parseMode(JsonNode modeNode) {
        try {
            return StepMode.valueOf(modeNode.asText());
        } catch (IllegalArgumentException error) {
            throw invalidPlan();
        }
    }

    private List<String> parseInputRefs(JsonNode inputRefsNode) {
        List<String> inputRefs = new ArrayList<>();
        for (JsonNode inputRef : inputRefsNode) {
            if (!inputRef.isTextual()) {
                throw invalidPlan();
            }
            inputRefs.add(inputRef.asText());
        }
        return List.copyOf(inputRefs);
    }

    private List<OrchestrationSubTask> parseSubTasks(JsonNode subTasksNode) {
        List<OrchestrationSubTask> subTasks = new ArrayList<>();
        for (JsonNode subTaskNode : subTasksNode) {
            if (!subTaskNode.isObject() || !hasExactlyFields(subTaskNode, SUB_TASK_FIELDS)
                    || !subTaskNode.path("subTaskId").isTextual()
                    || !subTaskNode.path("agentId").isTextual()
                    || !subTaskNode.path("objective").isTextual()) {
                throw invalidPlan();
            }
            subTasks.add(new OrchestrationSubTask(
                    subTaskNode.path("subTaskId").asText(),
                    subTaskNode.path("agentId").asText(),
                    subTaskNode.path("objective").asText()
            ));
        }
        return List.copyOf(subTasks);
    }

    private boolean isNullableText(JsonNode node) {
        return node.isNull() || node.isTextual();
    }

    private String nullableText(JsonNode node) {
        return node.isNull() ? null : node.asText();
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
