package com.jd.genie.platform.phase2.skillruntime.packageinfo;

/** Deterministic limits for the frozen R3 Skill Package contract. */
public final class SkillPackageLimits {
    public static final int MAX_SKILL_MD_BYTES = 256 * 1024;
    public static final int MAX_RESOURCE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_FILE_COUNT = 256;
    public static final long MAX_PACKAGE_BYTES = 16L * 1024 * 1024;
    public static final long MAX_BUNDLE_BYTES = 20L * 1024 * 1024;
    public static final int MAX_INPUT_JSON_BYTES = 1024 * 1024;
    public static final int MAX_OUTPUT_JSON_BYTES = 2 * 1024 * 1024;
    public static final int MAX_STDOUT_BYTES = 256 * 1024;
    public static final int MAX_STDERR_BYTES = 256 * 1024;
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;
    public static final int DEFAULT_EXECUTION_TIMEOUT_MS = 60_000;
    public static final int MAX_EXECUTION_TIMEOUT_MS = 300_000;

    private SkillPackageLimits() {}
}
