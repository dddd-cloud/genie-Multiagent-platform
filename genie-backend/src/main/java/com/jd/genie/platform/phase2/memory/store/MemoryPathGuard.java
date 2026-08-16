package com.jd.genie.platform.phase2.memory.store;

import com.jd.genie.platform.contract.MvpErrorCode;

final class MemoryPathGuard {
    private MemoryPathGuard() {
    }

    static String requireSegment(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, "Invalid " + label);
        }
        if (".".equals(value) || "..".equals(value)) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, "Invalid " + label);
        }
        if (value.indexOf('\\') >= 0 || value.indexOf('/') >= 0 || value.indexOf('\0') >= 0) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, "Invalid " + label);
        }
        if (value.contains("..")) {
            throw new MemoryStoreException(MvpErrorCode.VALIDATION_ERROR, "Invalid " + label);
        }
        return value;
    }
}
