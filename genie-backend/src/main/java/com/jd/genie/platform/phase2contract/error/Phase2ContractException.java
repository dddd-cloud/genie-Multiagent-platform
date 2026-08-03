package com.jd.genie.platform.phase2contract.error;

import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.Objects;

public final class Phase2ContractException extends RuntimeException {

    private final MvpErrorCode errorCode;

    public Phase2ContractException(
        MvpErrorCode errorCode,
        String message
    ) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public Phase2ContractException(
        MvpErrorCode errorCode,
        String message,
        Throwable cause
    ) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public MvpErrorCode errorCode() {
        return errorCode;
    }
}
