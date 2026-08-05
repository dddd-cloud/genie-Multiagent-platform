package com.jd.genie.platform.phase2.configuration.memory.exception;

import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.Objects;

public class MemoryAnalysisException extends RuntimeException {
    private final MvpErrorCode code;

    public MemoryAnalysisException(MvpErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MemoryAnalysisException(MvpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MvpErrorCode code() {
        return code;
    }
}
