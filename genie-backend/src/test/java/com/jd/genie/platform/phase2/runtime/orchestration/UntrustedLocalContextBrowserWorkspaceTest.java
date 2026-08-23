package com.jd.genie.platform.phase2.runtime.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UntrustedLocalContextBrowserWorkspaceTest {
    @Test
    void appendsTrustedSelectionPolicyForBrowserWorkspaceSnapshots() {
        String block = UntrustedLocalContext.block(
                "",
                "[UNTRUSTED_BROWSER_WORKSPACE]\n文件: /workspace/test.py\n[/UNTRUSTED_BROWSER_WORKSPACE]"
        );

        assertThat(block)
                .contains("[TRUSTED_BROWSER_WORKSPACE_POLICY]")
                .contains("直接依据索引回答")
                .contains("read_file")
                .contains("不得用 deep_search");
    }

    @Test
    void doesNotAppendBrowserPolicyToOrdinaryLocalContext() {
        assertThat(UntrustedLocalContext.block("memory", "summary"))
                .doesNotContain("[TRUSTED_BROWSER_WORKSPACE_POLICY]");
    }

    @Test
    void detectsBrowserWorkspaceSnapshotsForToolRouting() {
        assertThat(com.jd.genie.platform.phase2.runtime.context.BrowserWorkspaceContextPolicy
                .hasSnapshot("[UNTRUSTED_BROWSER_WORKSPACE]\n文件: /workspace/test.py"))
                .isTrue();
        assertThat(com.jd.genie.platform.phase2.runtime.context.BrowserWorkspaceContextPolicy
                .hasSnapshot("ordinary context"))
                .isFalse();
    }
}
