package com.jd.genie.platform.phase2.tooling;

import com.jd.genie.platform.contract.MvpErrorCode;

/** B-owned exception for runtime capability authorization failures. */
public final class ToolCapabilityException extends RuntimeException {
    public ToolCapabilityException(String message) {
        super(message);
    }

    public MvpErrorCode errorCode() { return MvpErrorCode.TOOL_NOT_BOUND; }
}
