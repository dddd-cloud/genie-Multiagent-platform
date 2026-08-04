package com.jd.genie.platform.phase2.tooling;
import com.fasterxml.jackson.databind.JsonNode;
public record McpToolResponse(String id, String toolName, String runtimeName, String description, JsonNode inputSchema, boolean enabled, boolean available, long version) { }
