package com.jd.genie.platform.phase2.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class McpServerVersionConflictTest {
    @Test void updateCarriesOptimisticVersion() {
        assertEquals("version", UpdateMcpServerRequest.class.getRecordComponents()[6].getName());
    }
}
