package com.jd.genie.platform.phase2.configuration.team.exception;

import com.jd.genie.platform.contract.MvpErrorCode;

public class TeamConfigurationException extends RuntimeException {
    private final MvpErrorCode code;

    public TeamConfigurationException(MvpErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public TeamConfigurationException(MvpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public MvpErrorCode code() {
        return code;
    }
}
