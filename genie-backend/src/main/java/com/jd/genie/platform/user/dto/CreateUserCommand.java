package com.jd.genie.platform.user.dto;

import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.entity.UserStatus;

public record CreateUserCommand(
    String tenantId,
    String username,
    String displayName,
    String password,
    UserRole role,
    UserStatus status
) {
}
