package com.jd.genie.platform.conversation;

import com.jd.genie.platform.conversation.history.CompletedHistoryTurnRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletedHistoryTurnRowTest {

    @Test
    void coversGeneratedValueSemanticsForHistoryProjection() {
        CompletedHistoryTurnRow row = row(1L, "req-1", "user", "assistant");

        assertTrue(row.equals(row));
        assertFalse(row.equals(null));
        assertFalse(row.equals("not-a-row"));
        assertEquals(row, row(1L, "req-1", "user", "assistant"));
        assertEquals(row.hashCode(), row(1L, "req-1", "user", "assistant").hashCode());
        assertTrue(row.toString().contains("req-1"));

        assertNotEquals(row, row(2L, "req-1", "user", "assistant"));
        assertNotEquals(row, row(1L, "req-2", "user", "assistant"));
        assertNotEquals(row, row(1L, "req-1", "other", "assistant"));
        assertNotEquals(row, row(1L, "req-1", "user", "other"));
        assertEquals(row(null, null, null, null), row(null, null, null, null));
        assertNotEquals(row(null, "req-1", "user", "assistant"), row(1L, "req-1", "user", "assistant"));
        assertNotEquals(row(1L, null, "user", "assistant"), row(1L, "req-1", "user", "assistant"));
        assertNotEquals(row(1L, "req-1", null, "assistant"), row(1L, "req-1", "user", "assistant"));
        assertNotEquals(row(1L, "req-1", "user", null), row(1L, "req-1", "user", "assistant"));
    }

    private CompletedHistoryTurnRow row(Long turnNo, String requestId, String userContent, String assistantContent) {
        CompletedHistoryTurnRow row = new CompletedHistoryTurnRow();
        row.setTurnNo(turnNo);
        row.setRequestId(requestId);
        row.setUserContent(userContent);
        row.setAssistantContent(assistantContent);
        return row;
    }
}
