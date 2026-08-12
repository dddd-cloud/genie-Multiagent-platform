package com.jd.genie.platform.phase2contract;

/**
 * Frozen cross-module string/constants for browser skill execution control packets.
 * Business timeouts and size limits stay in work-package code.
 */
public final class BrowserSkillExecutionContract {

    public static final int SCHEMA_VERSION = 1;
    public static final String PRINTER_MESSAGE_TYPE = "browser_skill_execution";
    public static final String SSE_PACKAGE_TYPE = "skill_execution";
    public static final String RESULT_MAP_KEY = "browserSkillExecution";
    public static final String EXECUTION_MANIFEST_PATH = "__joyagent__/execution.json";

    private BrowserSkillExecutionContract() {
    }
}
