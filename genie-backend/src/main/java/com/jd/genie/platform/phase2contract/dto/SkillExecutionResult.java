package com.jd.genie.platform.phase2contract.dto;

import com.jd.genie.platform.contract.MvpErrorCode;

public record SkillExecutionResult(
    boolean success,
    String stdout,
    String stderr,
    Integer exitCode,
    MvpErrorCode errorCode,
    String message
) {
}
