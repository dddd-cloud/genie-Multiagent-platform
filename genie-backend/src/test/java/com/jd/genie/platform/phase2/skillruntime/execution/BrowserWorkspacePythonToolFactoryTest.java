package com.jd.genie.platform.phase2.skillruntime.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrowserWorkspacePythonToolFactoryTest {
    @Test
    void createsAnOptionalBrowserWorkspacePythonTool() {
        var factory = new BrowserWorkspacePythonToolFactory(
                new BrowserSkillExecutionCoordinator(),
                new ObjectMapper(),
                new SkillPackageHasher()
        );

        var tool = factory.create(
                new CurrentUser("tenant", "owner", "owner", "Owner", UserRole.USER),
                AgentContext.builder().requestId("request").build()
        );

        assertThat(tool.getName()).isEqualTo(BrowserWorkspacePythonToolFactory.TOOL_NAME);
        assertThat(tool.getDescription())
                .contains("按需文件访问")
                .contains("/workspace/test.py")
                .contains("自动同步")
                .doesNotContain("input/<文件名>", "output/<文件名>");
        assertThat(tool.toParams()).containsEntry("type", "object");
        assertThat(tool.toParams().get("required")).isEqualTo(List.of("command"));
        assertThat(((Map<?, ?>) tool.toParams().get("properties")).containsKey("command")).isTrue();
        assertThat(((Map<?, ?>) tool.toParams().get("properties")).containsKey("script_path")).isTrue();
        assertThat(((Map<?, ?>) tool.toParams().get("properties")).containsKey("code")).isTrue();
        assertThat(((Map<?, ?>) tool.toParams().get("properties")).containsKey("file_path")).isTrue();
        assertThat(BrowserWorkspacePythonToolFactory.SCRIPT)
                .contains("Path(\"/workspace\")")
                .contains("runpy.run_path")
                .contains("payload = to_py()")
                .contains("_sync_workspace_changes")
                .contains("read_file")
                .contains("list_files")
                .contains("deletedFiles")
                .contains("\"ok\": False");
    }
}
