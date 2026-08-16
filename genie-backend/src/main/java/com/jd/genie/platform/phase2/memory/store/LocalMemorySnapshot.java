package com.jd.genie.platform.phase2.memory.store;

public record LocalMemorySnapshot(
    String longTermMemory,
    String conversationSummary
) {
    public static LocalMemorySnapshot empty() {
        return new LocalMemorySnapshot("", "");
    }
}
