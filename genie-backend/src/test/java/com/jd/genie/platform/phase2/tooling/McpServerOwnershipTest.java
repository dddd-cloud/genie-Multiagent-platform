package com.jd.genie.platform.phase2.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class McpServerOwnershipTest {
    @Test void requestsDoNotCarryTenantOrOwnerFields() {
        assertEquals(5, CreateMcpServerRequest.class.getRecordComponents().length);
        assertEquals(7, UpdateMcpServerRequest.class.getRecordComponents().length);
    }
}
