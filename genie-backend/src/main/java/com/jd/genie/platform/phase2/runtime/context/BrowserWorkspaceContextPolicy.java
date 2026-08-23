package com.jd.genie.platform.phase2.runtime.context;

/** Trusted usage policy activated when the UI supplies a bounded browser-workspace snapshot. */
public final class BrowserWorkspaceContextPolicy {
    public static final String MARKER = "[UNTRUSTED_BROWSER_WORKSPACE]";

    private BrowserWorkspaceContextPolicy() {
    }

    public static boolean hasSnapshot(String localContext) {
        return localContext != null && localContext.contains(MARKER);
    }

    public static String instructionFor(String localContext) {
        if (!hasSnapshot(localContext)) {
            return "";
        }
        return """

                [TRUSTED_BROWSER_WORKSPACE_POLICY]
                浏览器工作区快照只包含轻量文件索引和摘要，不包含完整文件正文。
                如果用户只询问文件名、路径或文件列表，直接依据索引回答；不要联网搜索，也不要调用后端代码解释器。
                如果回答确实需要某个文件的正文，先根据摘要选择文件，再调用 browser_workspace_python 的 read_file 按需读取；不要读取无关文件。
                只有需要执行代码、计算/转换数据、运行脚本或创建/修改/删除工作区文件时，才使用 run_script 或 run_code。
                执行时，当前浏览器工作区统一挂载在 /workspace。运行已有脚本必须使用 command=run_script 和快照中的 /workspace/<文件名>.py 路径。
                /workspace 只是浏览器 Python 沙箱内的逻辑挂载，不是后端容器目录；不得用 deep_search 或后端代码解释器寻找这些文件。
                [/TRUSTED_BROWSER_WORKSPACE_POLICY]
                """;
    }
}
