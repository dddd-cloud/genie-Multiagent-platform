package com.jd.genie.platform.user.dto;

import com.jd.genie.platform.contract.UserRole;

public record CreateAdminUserRequest(String username, String displayName, String password, UserRole role) { }
