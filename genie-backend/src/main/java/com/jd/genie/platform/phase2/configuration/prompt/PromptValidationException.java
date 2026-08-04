package com.jd.genie.platform.phase2.configuration.prompt;

import com.jd.genie.platform.contract.MvpErrorCode;

public class PromptValidationException extends RuntimeException {
    private final MvpErrorCode code;

    public PromptValidationException(MvpErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public MvpErrorCode code() {
        return code;
    }
}
