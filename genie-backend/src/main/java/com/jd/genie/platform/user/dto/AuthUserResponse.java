package com.jd.genie.platform.user.dto;

import com.jd.genie.platform.contract.UserRole;

public record AuthUserResponse(String id, String username, String displayName, UserRole role) { }
