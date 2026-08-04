package com.jd.genie.platform.phase2.tooling;
public record UpdateMcpServerRequest(String name, String serverUrl, AuthType authType, String authName, String credential, Boolean clearCredential, long version) { }
