package com.jd.genie.platform.user.dto;

import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.entity.UserStatus;

public record AdminUserResponse(String id, String username, String displayName, UserRole role,
                                UserStatus status, String createdAt, String updatedAt) { }
