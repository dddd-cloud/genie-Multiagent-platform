package com.jd.genie.platform.user.dto;

public record CsrfTokenResponse(String headerName, String parameterName, String token) { }
