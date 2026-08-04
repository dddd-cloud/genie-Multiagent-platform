package com.jd.genie.platform.phase2.tooling;
public record CreateMcpServerRequest(String name, String serverUrl, AuthType authType, String authName, String credential) { }
