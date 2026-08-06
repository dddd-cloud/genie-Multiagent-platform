package com.jd.genie.platform.phase2.runtime.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.Iterator;
import java.util.Set;

public final class RouteDecisionParser {
    private static final Set<String> FIELDS = Set.of("route", "reasonCode");

    private final ObjectMapper objectMapper;

    public RouteDecisionParser() {
        this(new ObjectMapper());
    }

    RouteDecisionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RouteDecision parse(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root == null || !root.isObject() || root.size() != FIELDS.size() || hasUnexpectedFields(root)) {
                throw invalidDecision();
            }
            String route = root.path("route").asText();
            String reasonCode = root.path("reasonCode").asText();
            if (route.isBlank() || reasonCode.isBlank()) {
                throw invalidDecision();
            }
            return new RouteDecision(RouteDecision.Route.valueOf(route), reasonCode);
        } catch (AgentBridgeException error) {
            throw error;
        } catch (Exception error) {
            throw invalidDecision();
        }
    }

    private boolean hasUnexpectedFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            if (!FIELDS.contains(names.next())) {
                return true;
            }
        }
        return false;
    }

    private AgentBridgeException invalidDecision() {
        return new AgentBridgeException(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, "Route decision must be a fixed JSON object");
    }
}
