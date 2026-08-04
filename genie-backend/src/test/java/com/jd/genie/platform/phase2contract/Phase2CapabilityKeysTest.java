package com.jd.genie.platform.phase2contract;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase2CapabilityKeysTest {

    @Test
    void allFiveBuiltinKeysAreValid() {
        assertEquals(5, CapabilityKeys.builtInKeys().size());
        for (String key : CapabilityKeys.builtInKeys()) {
            CapabilityKeys.requireValid(key);
            assertTrue(CapabilityKeys.isBuiltIn(key));
            assertFalse(CapabilityKeys.isMcp(key));
        }
    }

    @Test
    void planningToolIsIllegal() {
        Phase2ContractException ex = assertThrows(
            Phase2ContractException.class,
            () -> CapabilityKeys.requireValid("planning_tool")
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, ex.errorCode());
    }

    @Test
    void unknownBuiltinIsIllegal() {
        Phase2ContractException ex = assertThrows(
            Phase2ContractException.class,
            () -> CapabilityKeys.requireValid("builtin:unknown")
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, ex.errorCode());
    }

    @Test
    void emptyMcpSuffixIsIllegal() {
        assertThrows(Phase2ContractException.class, () -> CapabilityKeys.requireValid("mcp:"));
        assertThrows(Phase2ContractException.class, () -> CapabilityKeys.requireValid("mcp: "));
    }

    @Test
    void mcpKeyParsesToolId() {
        String key = CapabilityKeys.forMcpTool("tool-123");
        assertEquals("mcp:tool-123", key);
        assertTrue(CapabilityKeys.isMcp(key));
        assertEquals("tool-123", CapabilityKeys.mcpToolId(key));
    }

    @Test
    void leadingOrTrailingWhitespaceIsIllegal() {
        assertThrows(Phase2ContractException.class,
            () -> CapabilityKeys.requireValid(" builtin:file"));
        assertThrows(Phase2ContractException.class,
            () -> CapabilityKeys.requireValid("builtin:file "));
        assertThrows(Phase2ContractException.class,
            () -> CapabilityKeys.requireValid(""));
        assertThrows(Phase2ContractException.class,
            () -> CapabilityKeys.requireValid(null));
    }

    @Test
    void duplicateKeysAreRejected() {
        Phase2ContractException ex = assertThrows(
            Phase2ContractException.class,
            () -> CapabilityKeys.requireAllValid(List.of(
                CapabilityKeys.BUILTIN_FILE,
                CapabilityKeys.BUILTIN_REPORT,
                CapabilityKeys.BUILTIN_FILE
            ))
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, ex.errorCode());
    }

    @Test
    void tabAndNewlineMcpKeysAreRejected() {
        Phase2ContractException tabKey = assertThrows(
            Phase2ContractException.class,
            () -> CapabilityKeys.requireValid("mcp:\ttool")
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, tabKey.errorCode());
        assertFalse(CapabilityKeys.isMcp("mcp:\ttool"));

        Phase2ContractException newlineKey = assertThrows(
            Phase2ContractException.class,
            () -> CapabilityKeys.requireValid("mcp:tool\n")
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, newlineKey.errorCode());
        assertFalse(CapabilityKeys.isMcp("mcp:tool\n"));
    }

    @Test
    void forMcpToolRejectsTabPrefixedId() {
        Phase2ContractException ex = assertThrows(
            Phase2ContractException.class,
            () -> CapabilityKeys.forMcpTool("\ttool")
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, ex.errorCode());
    }

    @Test
    void requireAllValidPreservesInputOrder() {
        Set<String> validated = CapabilityKeys.requireAllValid(List.of(
            CapabilityKeys.BUILTIN_REPORT,
            "mcp:mcp-tool-001",
            CapabilityKeys.BUILTIN_FILE
        ));
        assertEquals(
            List.of(
                CapabilityKeys.BUILTIN_REPORT,
                "mcp:mcp-tool-001",
                CapabilityKeys.BUILTIN_FILE
            ),
            new ArrayList<>(validated)
        );
    }

    @Test
    void nullCapabilityKeysIterableIsRejected() {
        Phase2ContractException ex = assertThrows(
            Phase2ContractException.class,
            () -> CapabilityKeys.requireAllValid(null)
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, ex.errorCode());
    }
}
