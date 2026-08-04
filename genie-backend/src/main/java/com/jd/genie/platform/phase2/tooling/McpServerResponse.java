package com.jd.genie.platform.phase2.tooling;
import java.time.LocalDateTime;
public record McpServerResponse(String id, String name, String serverUrl, AuthType authType, String authName, McpServerStatus status, boolean credentialConfigured, String lastCheckStatus, String lastCheckCode, LocalDateTime lastCheckedAt, long version, LocalDateTime createdAt, LocalDateTime updatedAt) { }
