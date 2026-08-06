package com.jd.genie.platform.phase2.configuration.agent.exception;

import com.jd.genie.platform.contract.MvpErrorCode;

public class AgentConfigurationException extends RuntimeException {
    private final MvpErrorCode code;

    public AgentConfigurationException(MvpErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AgentConfigurationException(MvpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public MvpErrorCode code() {
        return code;
    }
}