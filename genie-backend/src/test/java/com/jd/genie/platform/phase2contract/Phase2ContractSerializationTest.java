package com.jd.genie.platform.phase2contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeSkill;
import com.jd.genie.platform.phase2contract.dto.OrchestrationEvent;
import com.jd.genie.platform.phase2contract.dto.Phase2GptQueryRequest;
import com.jd.genie.platform.phase2contract.dto.Phase2LocalContext;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.enums.ExecutionMode;
import com.jd.genie.platform.phase2contract.enums.OrchestrationCompletionStatus;
import com.jd.genie.platform.phase2contract.enums.OrchestrationEventType;
import com.jd.genie.platform.phase2contract.enums.OrchestrationRoute;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase2ContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void agentRuntimeProfileUsesCamelCase() throws Exception {
        AgentRuntimeProfile profile = new AgentRuntimeProfile(
            "agent-1",
            2L,
            "Name",
            "Desc",
            "prompt {{query}}",
            "gpt-4o-mini",
            List.of(new AgentRuntimeSkill("skill-1", 1L, 0, "do", "out")),
            List.of("builtin:file")
        );

        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(profile));
        assertTrue(node.has("compiledSystemPromptTemplate"));
        assertTrue(node.has("resolvedModelName"));
        assertTrue(node.has("capabilityKeys"));
        assertEquals("agent-1", node.get("agentId").asText());
    }

    @Test
    void toolBindingViewEmptyCollectionsSerialize() throws Exception {
        ToolBindingView view = new ToolBindingView(null, null, null);
        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(view));
        assertTrue(node.get("directCapabilities").isArray());
        assertEquals(0, node.get("directCapabilities").size());
        assertTrue(node.get("skillCapabilities").isObject());
        assertEquals(0, node.get("skillCapabilities").size());
        assertTrue(node.get("invalidCapabilities").isArray());
    }

    @Test
    void orchestrationEventFieldNamesMatchTypeScript() throws Exception {
        OrchestrationEvent event = new OrchestrationEvent(
            1,
            "request-fixture:1",
            1L,
            OrchestrationEventType.ROUTE_SELECTED,
            "request-fixture",
            "run-fixture",
            null,
            null,
            null,
            null,
            OrchestrationRoute.DIRECT,
            "USER_DIRECT",
            null,
            List.of(),
            null
        );
        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(event));
        for (String field : List.of(
            "schemaVersion", "eventId", "sequence", "eventType", "requestId", "runId",
            "attemptNo", "stepId", "agentId", "agentName", "route", "reasonCode",
            "errorCode", "steps", "completionStatus"
        )) {
            assertTrue(node.has(field), "missing " + field);
        }
        assertEquals("ROUTE_SELECTED", node.get("eventType").asText());
    }

    @Test
    void phase2GptQueryRequestFieldNamesAreStable() throws Exception {
        Phase2GptQueryRequest request = new Phase2GptQueryRequest(
            "11111111-1111-1111-1111-111111111111",
            "req-1",
            "hello",
            ExecutionMode.AUTO,
            0,
            "markdown",
            List.of("agent-1"),
            new Phase2LocalContext(1, "", "")
        );
        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(request));
        for (String field : List.of(
            "sessionId", "requestId", "query", "executionMode",
            "deepThink", "outputStyle", "allowedAgentIds", "localContext"
        )) {
            assertTrue(node.has(field), "missing " + field);
        }
        assertEquals("AUTO", node.get("executionMode").asText());
    }

    @Test
    void collectionCopiesAreImmutable() {
        List<String> mutableCaps = new ArrayList<>(List.of("builtin:file"));
        Map<String, List<String>> mutableSkills = new HashMap<>();
        mutableSkills.put("skill-1", new ArrayList<>(List.of("builtin:file")));
        ToolBindingView view = new ToolBindingView(mutableCaps, mutableSkills, new ArrayList<>());
        mutableCaps.add("builtin:report");
        mutableSkills.put("skill-2", List.of("builtin:report"));
        assertEquals(1, view.directCapabilities().size());
        assertEquals(1, view.skillCapabilities().size());
        assertThrows(UnsupportedOperationException.class, () -> view.directCapabilities().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> view.skillCapabilities().put("y", List.of()));
    }

    @Test
    void finalResponseCompletionStatusSerializes() throws Exception {
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
        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(event));
        assertEquals("SUCCESS", node.get("completionStatus").asText());
    }
}
