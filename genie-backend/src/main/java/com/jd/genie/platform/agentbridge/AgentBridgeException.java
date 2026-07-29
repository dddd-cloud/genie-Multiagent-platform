package com.jd.genie.platform.agentbridge;

import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.Objects;

public final class AgentBridgeException extends RuntimeException {
    private final MvpErrorCode errorCode;

    public AgentBridgeException(MvpErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public AgentBridgeException(MvpErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public MvpErrorCode getErrorCode() {
        return errorCode;
    }
}
