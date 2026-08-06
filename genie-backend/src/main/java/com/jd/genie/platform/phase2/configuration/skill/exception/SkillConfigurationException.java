package com.jd.genie.platform.phase2.configuration.skill.exception;

import com.jd.genie.platform.contract.MvpErrorCode;

public class SkillConfigurationException extends RuntimeException {
    private final MvpErrorCode code;

    public SkillConfigurationException(MvpErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public SkillConfigurationException(MvpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public MvpErrorCode code() {
        return code;
    }
}