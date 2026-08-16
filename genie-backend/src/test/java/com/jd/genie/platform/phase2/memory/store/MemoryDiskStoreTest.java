package com.jd.genie.platform.phase2.memory.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryDiskStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsUserScopedMarkdown() {
        MemoryDiskStore store = new MemoryDiskStore(tempDir);
        store.writeLongTerm("user-a", "hello");
        assertEquals("hello", store.readLongTerm("user-a"));
        assertNull(store.readLongTerm("user-b"));
        assertTrue(Files.exists(tempDir.resolve("v1/users/user-a/长期记忆.md")));
    }

    @Test
    void rejectsPathTraversalUserId() {
        MemoryDiskStore store = new MemoryDiskStore(tempDir);
        assertThrows(MemoryStoreException.class, () -> store.writeLongTerm("../evil", "nope"));
        assertThrows(MemoryStoreException.class, () -> store.writeSummary("user-a", "..", "nope"));
    }

    @Test
    void listsOnlyExistingSummaries() {
        MemoryDiskStore store = new MemoryDiskStore(tempDir);
        store.writeSummary("user-a", "conv-1", "one");
        store.writeSummary("user-a", "conv-2", "two");
        store.writeSummary("user-b", "conv-9", "other");
        assertEquals(2, store.listSummaryConversationIds("user-a").size());
        store.deleteSummary("user-a", "conv-1");
        assertEquals(1, store.listSummaryConversationIds("user-a").size());
        assertFalse(Files.exists(store.summaryPath("user-a", "conv-1")));
    }
}
