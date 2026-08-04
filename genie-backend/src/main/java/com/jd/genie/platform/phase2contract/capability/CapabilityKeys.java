package com.jd.genie.platform.phase2contract.capability;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CapabilityKeys {

    public static final String BUILTIN_CODE_INTERPRETER = "builtin:code_interpreter";
    public static final String BUILTIN_DATA_ANALYSIS = "builtin:data_analysis";
    public static final String BUILTIN_DEEP_SEARCH = "builtin:deep_search";
    public static final String BUILTIN_FILE = "builtin:file";
    public static final String BUILTIN_REPORT = "builtin:report";

    private static final String MCP_PREFIX = "mcp:";
    private static final String BUILTIN_PREFIX = "builtin:";

    private static final Set<String> BUILT_IN_KEYS = Set.of(
        BUILTIN_CODE_INTERPRETER,
        BUILTIN_DATA_ANALYSIS,
        BUILTIN_DEEP_SEARCH,
        BUILTIN_FILE,
        BUILTIN_REPORT
    );

    private CapabilityKeys() {
    }

    public static Set<String> builtInKeys() {
        return BUILT_IN_KEYS;
    }

    public static boolean isBuiltIn(String capabilityKey) {
        return capabilityKey != null && BUILT_IN_KEYS.contains(capabilityKey);
    }

    public static boolean isMcp(String capabilityKey) {
        if (capabilityKey == null || !capabilityKey.startsWith(MCP_PREFIX)) {
            return false;
        }
        return isValidMcpToolId(capabilityKey.substring(MCP_PREFIX.length()));
    }

    public static String mcpToolId(String capabilityKey) {
        requireValid(capabilityKey);
        if (!isMcp(capabilityKey)) {
            throw new Phase2ContractException(
                MvpErrorCode.TOOL_BINDING_INVALID,
                "capabilityKey is not an MCP key"
            );
        }
        return capabilityKey.substring(MCP_PREFIX.length());
    }

    public static String forMcpTool(String mcpToolId) {
        if (!isValidMcpToolId(mcpToolId)) {
            throw new Phase2ContractException(
                MvpErrorCode.TOOL_BINDING_INVALID,
                "mcpToolId is invalid"
            );
        }
        return MCP_PREFIX + mcpToolId;
    }

    public static void requireValid(String capabilityKey) {
        if (capabilityKey == null || capabilityKey.isEmpty()) {
            throw new Phase2ContractException(
                MvpErrorCode.TOOL_BINDING_INVALID,
                "capabilityKey must not be blank"
            );
        }
        if (!capabilityKey.equals(capabilityKey.trim())) {
            throw new Phase2ContractException(
                MvpErrorCode.TOOL_BINDING_INVALID,
                "capabilityKey must not have leading or trailing whitespace"
            );
        }
        for (int i = 0; i < capabilityKey.length(); i++) {
            if (Character.isWhitespace(capabilityKey.charAt(i))) {
                throw new Phase2ContractException(
                    MvpErrorCode.TOOL_BINDING_INVALID,
                    "capabilityKey must not contain whitespace"
                );
            }
        }
        if ("planning_tool".equals(capabilityKey) || capabilityKey.contains("planning_tool")) {
            throw new Phase2ContractException(
                MvpErrorCode.TOOL_BINDING_INVALID,
                "planning_tool is not a user capability"
            );
        }
        if (capabilityKey.startsWith(BUILTIN_PREFIX)) {
            if (!BUILT_IN_KEYS.contains(capabilityKey)) {
                throw new Phase2ContractException(
                    MvpErrorCode.TOOL_BINDING_INVALID,
                    "unknown builtin capabilityKey"
                );
            }
            return;
        }
        if (capabilityKey.startsWith(MCP_PREFIX)) {
            if (!isValidMcpToolId(capabilityKey.substring(MCP_PREFIX.length()))) {
                throw new Phase2ContractException(
                    MvpErrorCode.TOOL_BINDING_INVALID,
                    "mcp capabilityKey requires a valid tool id"
                );
            }
            return;
        }
        throw new Phase2ContractException(
            MvpErrorCode.TOOL_BINDING_INVALID,
            "capabilityKey format is invalid"
        );
    }

    public static Set<String> requireAllValid(Iterable<String> capabilityKeys) {
        if (capabilityKeys == null) {
            throw new Phase2ContractException(
                MvpErrorCode.TOOL_BINDING_INVALID,
                "capabilityKeys must not be null"
            );
        }
        Set<String> validated = new LinkedHashSet<>();
        for (String capabilityKey : capabilityKeys) {
            requireValid(capabilityKey);
            if (!validated.add(capabilityKey)) {
                throw new Phase2ContractException(
                    MvpErrorCode.TOOL_BINDING_INVALID,
                    "capabilityKeys must not contain duplicates"
                );
            }
        }
        return Collections.unmodifiableSet(validated);
    }

    private static boolean isValidMcpToolId(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (!value.equals(value.trim())) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
                return false;
            }
            if (ch == '/' || ch == '\\' || ch == '?' || ch == '#') {
                return false;
            }
            boolean allowed =
                (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '_'
                    || ch == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
