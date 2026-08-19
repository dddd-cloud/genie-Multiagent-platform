package com.jd.genie.platform.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceRequestIdsTest {

    @Test
    void namespacesAreUserAndConversationScopedAndIgnoreClientWorkspaceId() {
        String alice = WorkspaceRequestIds.forConversation("t1", "user-a", "conv-1");
        String bob = WorkspaceRequestIds.forConversation("t1", "user-b", "conv-1");
        String otherConv = WorkspaceRequestIds.forConversation("t1", "user-a", "conv-2");

        assertTrue(alice.startsWith("workspace-v1-"));
        assertEquals(13 + 64, alice.length());
        assertEquals(alice, WorkspaceRequestIds.forConversation("t1", "user-a", "conv-1"));
        assertNotEquals(alice, bob);
        assertNotEquals(alice, otherConv);
    }
}
