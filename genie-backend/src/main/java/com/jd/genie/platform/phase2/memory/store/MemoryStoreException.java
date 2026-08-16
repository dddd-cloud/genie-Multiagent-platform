package com.jd.genie.platform.phase2.memory.store;

import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.Objects;

public class MemoryStoreException extends RuntimeException {
    private final MvpErrorCode code;

    public MemoryStoreException(MvpErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MemoryStoreException(MvpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public MvpErrorCode code() {
        return code;
    }
}
