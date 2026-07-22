package com.jd.genie.platform.conversation.exception;

import com.jd.genie.platform.contract.MvpErrorCode;

public class ConversationException extends RuntimeException {
    private final MvpErrorCode code;

    public ConversationException(MvpErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ConversationException(MvpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public MvpErrorCode code() {
        return code;
    }
}
