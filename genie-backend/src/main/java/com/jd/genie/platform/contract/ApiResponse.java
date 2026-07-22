package com.jd.genie.platform.contract;

public record ApiResponse<T>(
    String code,
    String message,
    T data
) {
}
