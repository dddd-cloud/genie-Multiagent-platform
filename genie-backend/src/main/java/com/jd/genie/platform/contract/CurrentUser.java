package com.jd.genie.platform.contract;

public record CurrentUser(
    String tenantId,
    String userId,
    String username,
    String displayName,
    UserRole role
) {
}
