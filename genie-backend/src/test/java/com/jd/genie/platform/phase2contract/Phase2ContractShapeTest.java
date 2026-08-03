package com.jd.genie.platform.phase2contract;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.dto.OrchestrationEvent;
import com.jd.genie.platform.phase2contract.dto.OrchestrationPlanStepView;
import com.jd.genie.platform.phase2contract.dto.Phase2GptQueryRequest;
import com.jd.genie.platform.phase2contract.dto.Phase2LocalContext;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.enums.AgentTaskErrorCode;
import com.jd.genie.platform.phase2contract.enums.ExecutionMode;
import com.jd.genie.platform.phase2contract.enums.OrchestrationCompletionStatus;
import com.jd.genie.platform.phase2contract.enums.OrchestrationEventType;
import com.jd.genie.platform.phase2contract.enums.OrchestrationRoute;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase2ContractShapeTest {

    @Test
    void agentRuntimeCatalogPortSignature() throws NoSuchMethodException {
        Method list = AgentRuntimeCatalogPort.class.getMethod(
            "listOnlineCandidates", CurrentUser.class, List.class);
        assertEquals(List.class, list.getReturnType());
        Method load = AgentRuntimeCatalogPort.class.getMethod(
            "loadOnlineProfile", CurrentUser.class, String.class);
        assertEquals(AgentRuntimeProfile.class, load.getReturnType());
    }

    @Test
    void toolBindingPortSignatureAndTransactions() throws NoSuchMethodException {
        Method resolve = ToolBindingPort.class.getMethod(
            "resolveBindings", CurrentUser.class, String.class, List.class);
        assertEquals(ToolBindingView.class, resolve.getReturnType());

        for (String methodName : List.of(
            "replaceAgentBindings",
            "replaceSkillBindings",
            "removeAgentBindings",
            "removeSkillBindings"
        )) {
            Method method = Arrays.stream(ToolBindingPort.class.getMethods())
                .filter(item -> item.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertTrue(transactional != null, methodName + " must be @Transactional");
            assertEquals(Propagation.REQUIRED, transactional.propagation());
            assertEquals(void.class, method.getReturnType());
        }
    }

    @Test
    void runtimeToolCollectionPortSignature() throws NoSuchMethodException {
        Method build = RuntimeToolCollectionPort.class.getMethod(
            "build", CurrentUser.class, AgentRuntimeProfile.class, AgentContext.class);
        assertEquals(ToolCollection.class, build.getReturnType());
    }

    @Test
    void dtoRecordComponents() {
        assertRecordComponents(AgentCapabilitySummary.class,
            "agentId", "agentVersion", "name", "description");
        assertRecordComponents(AgentRuntimeSkill.class,
            "skillId", "skillVersion", "sortOrder", "instruction", "outputRequirement");
        assertRecordComponents(AgentRuntimeProfile.class,
            "agentId", "agentVersion", "name", "description",
            "compiledSystemPromptTemplate", "resolvedModelName", "skills", "capabilityKeys");
        assertRecordComponents(ToolBindingView.class,
            "directCapabilities", "skillCapabilities", "invalidCapabilities");
        assertRecordComponents(Phase2LocalContext.class,
            "schemaVersion", "longTermMemory", "conversationSummary");
        assertRecordComponents(Phase2GptQueryRequest.class,
            "sessionId", "requestId", "query", "executionMode",
            "deepThink", "outputStyle", "allowedAgentIds", "localContext");
        assertRecordComponents(OrchestrationPlanStepView.class,
            "stepId", "agentId", "agentName", "objective", "inputRefs");
        assertRecordComponents(OrchestrationEvent.class,
            "schemaVersion", "eventId", "sequence", "eventType", "requestId", "runId",
            "attemptNo", "stepId", "agentId", "agentName", "route", "reasonCode",
            "errorCode", "steps", "completionStatus");
    }

    @Test
    void enumValuesAndOrder() {
        assertArrayEquals(new String[]{"AUTO", "DIRECT", "ORCHESTRATED"}, enumNames(ExecutionMode.class));
        assertArrayEquals(new String[]{"DIRECT", "ORCHESTRATED"}, enumNames(OrchestrationRoute.class));
        assertArrayEquals(new String[]{
            "ROUTE_SELECTED", "PLAN_CREATED", "STEP_STARTED", "STEP_COMPLETED", "STEP_FAILED",
            "STEP_SKIPPED", "REPLAN_STARTED", "SUMMARY_STARTED", "SUMMARY_COMPLETED",
            "SUMMARY_FALLBACK", "FINAL_RESPONSE"
        }, enumNames(OrchestrationEventType.class));
        assertArrayEquals(new String[]{
            "INVALID_INPUT", "AGENT_OFFLINE", "TOOL_PERMISSION_DENIED", "TOOL_TIMEOUT",
            "TOOL_UNAVAILABLE", "TOOL_INVALID_RESPONSE", "AGENT_INVALID_RESULT",
            "CONTEXT_BUDGET_EXCEEDED", "EXECUTION_ERROR", "CANCELLED"
        }, enumNames(AgentTaskErrorCode.class));
        assertArrayEquals(new String[]{"SUCCESS", "PARTIAL"}, enumNames(OrchestrationCompletionStatus.class));
    }

    @Test
    void publicContractTypesHaveNoSpringStereotypes() {
        List<Class<?>> types = List.of(
            AgentRuntimeCatalogPort.class,
            ToolBindingPort.class,
            RuntimeToolCollectionPort.class,
            AgentCapabilitySummary.class,
            AgentRuntimeSkill.class,
            AgentRuntimeProfile.class,
            ToolBindingView.class,
            Phase2LocalContext.class,
            Phase2GptQueryRequest.class,
            OrchestrationPlanStepView.class,
            OrchestrationEvent.class,
            ExecutionMode.class,
            OrchestrationRoute.class,
            OrchestrationEventType.class,
            AgentTaskErrorCode.class,
            OrchestrationCompletionStatus.class
        );
        for (Class<?> type : types) {
            assertFalse(type.isAnnotationPresent(Component.class), type.getSimpleName());
            assertFalse(type.isAnnotationPresent(Service.class), type.getSimpleName());
            assertFalse(type.isAnnotationPresent(Repository.class), type.getSimpleName());
            assertFalse(type.isAnnotationPresent(Controller.class), type.getSimpleName());
            assertFalse(type.isAnnotationPresent(RestController.class), type.getSimpleName());
        }
    }

    @Test
    void uniqueTypeDefinitionsAcrossRepository() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("genie-backend"))) {
            root = root.getParent();
        }
        assertTrue(root != null, "repository root not found");
        assertUniqueSimpleName(root, "AgentRuntimeCatalogPort.java");
        assertUniqueSimpleName(root, "ToolBindingPort.java");
        assertUniqueSimpleName(root, "RuntimeToolCollectionPort.java");
        assertUniqueSimpleName(root, "AgentRuntimeProfile.java");
        assertUniqueSimpleName(root, "ToolBindingView.java");
        assertUniqueSimpleName(root, "OrchestrationEvent.java");
    }

    private static void assertUniqueSimpleName(Path root, String fileName) throws IOException {
        List<Path> matches = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root.resolve("genie-backend/src/main/java"))) {
            walk.filter(path -> path.getFileName().toString().equals(fileName))
                .forEach(matches::add);
        }
        assertEquals(1, matches.size(), "Expected unique " + fileName + " but found " + matches);
    }

    private static void assertRecordComponents(Class<?> recordClass, String... expected) {
        RecordComponent[] components = recordClass.getRecordComponents();
        String[] actual = Arrays.stream(components)
            .map(RecordComponent::getName)
            .toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    private static String[] enumNames(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
            .map(Enum::name)
            .toArray(String[]::new);
    }
}
