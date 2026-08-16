package com.jd.genie.platform.phase2.memory.store;

public record MemoryFileSnapshot(
    Status status,
    String markdown,
    String reason
) {
    public enum Status {
        READY,
        EMPTY,
        CORRUPTED,
        UNAVAILABLE
    }

    public static MemoryFileSnapshot empty() {
        return new MemoryFileSnapshot(Status.EMPTY, null, null);
    }

    public static MemoryFileSnapshot ready(String markdown) {
        return new MemoryFileSnapshot(Status.READY, markdown, null);
    }

    public static MemoryFileSnapshot corrupted(String markdown, String reason) {
        return new MemoryFileSnapshot(Status.CORRUPTED, markdown, reason);
    }

    public static MemoryFileSnapshot unavailable() {
        return new MemoryFileSnapshot(Status.UNAVAILABLE, null, "memory store unavailable");
    }
}
