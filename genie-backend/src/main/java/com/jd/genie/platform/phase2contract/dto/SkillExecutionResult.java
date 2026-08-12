package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.contract.MvpErrorCode;

public record SkillExecutionResult(
    boolean success,
    String stdout,
    String stderr,
    Integer exitCode,
    MvpErrorCode errorCode,
    String message,
    String outputJson
) {
    /** Backward-compatible constructor for existing Fake/Legacy callers. */
    public SkillExecutionResult(
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        MvpErrorCode errorCode,
        String message
    ) {
        this(success, stdout, stderr, exitCode, errorCode, message, null);
    }
}
