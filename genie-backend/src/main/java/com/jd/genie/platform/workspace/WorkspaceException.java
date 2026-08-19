package com.jd.genie.platform.workspace;

import com.jd.genie.platform.contract.MvpErrorCode;

public class WorkspaceException extends RuntimeException {
    private final MvpErrorCode code;

    public WorkspaceException(MvpErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public WorkspaceException(MvpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public MvpErrorCode code() {
        return code;
    }
}
