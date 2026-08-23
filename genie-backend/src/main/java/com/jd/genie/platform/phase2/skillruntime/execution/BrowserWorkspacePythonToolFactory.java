package com.jd.genie.platform.phase2.skillruntime.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Creates the optional browser-side Python sandbox exposed to configured web Agents.
 * The generated code runs in the existing Pyodide Web Worker and never in the backend
 * or genie-tool container.
 */
@Component
public final class BrowserWorkspacePythonToolFactory {
    public static final String TOOL_NAME = "browser_workspace_python";
    public static final String SKILL_ID = "builtin-browser-workspace-python";
    private static final String SCRIPT_PATH = "browser_workspace_python.py";
    // Excludes openpyxl/xlsxwriter: absent from this Pyodide build's package
    // repo, so pyodide.loadPackage() rejects them outright (confirmed against
    // pyodide-lock.json). Declaring them would break every tool invocation,
    // since packages are loaded eagerly regardless of which command runs.
    private static final List<String> ALLOWED_PACKAGES = List.of(
            "numpy", "pandas", "matplotlib", "pillow", "scipy",
            "scikit-learn", "python-dateutil", "beautifulsoup4", "lxml",
            "sympy", "regex"
    );
    private static final String DESCRIPTION = """
            浏览器工作区按需文件访问与可选 Python 沙箱。工作区上下文只提供轻量索引和摘要；确实需要正文时用 read_file 读取指定文件，索引不足时用 list_files。
            仅当用户请求确实需要执行 Python、计算/分析/转换数据、运行脚本，或创建/修改/删除工作区文件时使用 run_script 或 run_code。不要读取无关文件。
            代码在当前网页的 Pyodide Web Worker 中执行，不访问后端代码解释器。当前选中的浏览器工作区统一挂载在 /workspace，包含其完整的多级子文件夹结构。
            运行已有脚本时使用 command=run_script 和快照中给出的绝对路径（例如 /workspace/scripts/main.py，可位于任意子文件夹）；临时代码使用 command=run_code。
            /workspace 中新增、修改或删除的文件（含子文件夹中的文件）会自动同步回当前工作区，并保留其原有的文件夹层级。不要把 /workspace 改写成 input/ 或 output/。
            多文件项目内可以使用相对导入/相对路径互相引用，只要都在 /workspace 之下即可正常运行。
            """;
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["command"],
              "properties": {
                "command": {
                  "type": "string",
                  "enum": ["list_files", "read_file", "run_script", "run_code"],
                  "description": "list_files 列出文件（含子文件夹）；read_file 按需读取一个文本文件；run_script 运行已有 Python 文件；run_code 执行临时代码。"
                },
                "file_path": {
                  "type": "string",
                  "description": "read_file 时必填，必须是 /workspace 下的绝对文件路径，可位于任意子文件夹。"
                },
                "max_chars": {
                  "type": "integer",
                  "minimum": 1,
                  "maximum": 12000,
                  "description": "read_file 最多返回字符数，默认 8000。"
                },
                "script_path": {
                  "type": "string",
                  "description": "run_script 时必填，必须是 /workspace 下的绝对 .py 文件路径，可位于任意子文件夹。"
                },
                "code": {
                  "type": "string",
                  "minLength": 1,
                  "description": "run_code 时必填。代码可直接读写 /workspace（含子文件夹）；可设置 JSON 可序列化的 result 变量。"
                }
              }
            }
            """;
    static final String SCRIPT = """
            import contextlib
            import hashlib
            import io
            import json
            import os
            import runpy
            import shutil
            import traceback
            from pathlib import Path

            WORKSPACE_ROOT = Path("/workspace")

            def _file_hash(path):
                return hashlib.sha256(path.read_bytes()).hexdigest()

            def _all_files(root):
                if not root.exists():
                    return {}
                return {
                    str(path.relative_to(root)): _file_hash(path)
                    for path in root.rglob("*")
                    if path.is_file()
                }

            def _json_safe(value):
                try:
                    json.dumps(value)
                    return value
                except (TypeError, ValueError, OverflowError):
                    return repr(value)

            def _mount_workspace(execution_root):
                if WORKSPACE_ROOT.exists():
                    shutil.rmtree(WORKSPACE_ROOT)
                WORKSPACE_ROOT.mkdir(parents=True)
                input_root = execution_root / "input"
                if input_root.exists():
                    for source in input_root.rglob("*"):
                        if source.is_file():
                            relative = source.relative_to(input_root)
                            destination = WORKSPACE_ROOT / relative
                            destination.parent.mkdir(parents=True, exist_ok=True)
                            shutil.copyfile(source, destination)

            def _sync_workspace_changes(execution_root, initial_files):
                output_root = execution_root / "output"
                output_root.mkdir(parents=True, exist_ok=True)
                current_files = _all_files(WORKSPACE_ROOT)
                changed = []
                for relative_name, digest in current_files.items():
                    if initial_files.get(relative_name) != digest:
                        destination = output_root / relative_name
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copyfile(WORKSPACE_ROOT / relative_name, destination)
                        changed.append(relative_name)
                deleted = sorted(name for name in initial_files if name not in current_files)
                return sorted(changed), deleted

            def _workspace_file(raw_path, suffix=None):
                if not isinstance(raw_path, str) or not raw_path.strip():
                    raise ValueError("file path is required")
                path = Path(raw_path)
                if not path.is_absolute():
                    raise ValueError("file path must be an absolute /workspace path")
                try:
                    path.resolve().relative_to(WORKSPACE_ROOT.resolve())
                except ValueError as exc:
                    raise ValueError("file path must stay inside /workspace") from exc
                if not path.is_file():
                    raise ValueError("file path must reference an existing workspace file")
                if suffix is not None and path.suffix.lower() != suffix:
                    raise ValueError("file path has an unsupported extension")
                return path

            def main(payload):
                execution_root = Path.cwd()
                original_cwd = Path.cwd()
                stdout_buffer = io.StringIO()
                stderr_buffer = io.StringIO()
                initial_files = {}
                try:
                    if not isinstance(payload, dict):
                        to_py = getattr(payload, "to_py", None)
                        if callable(to_py):
                            payload = to_py()
                    if not isinstance(payload, dict):
                        raise ValueError("tool input must be an object")
                    command = payload.get("command")
                    # Compatibility with calls created before the command field existed.
                    if command is None and isinstance(payload.get("code"), str):
                        command = "run_code"
                    if command not in ("list_files", "read_file", "run_script", "run_code"):
                        raise ValueError("command must be list_files, read_file, run_script or run_code")

                    _mount_workspace(execution_root)
                    initial_files = _all_files(WORKSPACE_ROOT)
                    os.chdir(WORKSPACE_ROOT)
                    namespace = None
                    with contextlib.redirect_stdout(stdout_buffer), contextlib.redirect_stderr(stderr_buffer):
                        if command == "list_files":
                            namespace = {"result": {
                                "files": [
                                    {"path": str(WORKSPACE_ROOT / relative), "size": (WORKSPACE_ROOT / relative).stat().st_size}
                                    for relative in sorted(_all_files(WORKSPACE_ROOT).keys())
                                ]
                            }}
                        elif command == "read_file":
                            file_path = _workspace_file(payload.get("file_path"))
                            max_chars = payload.get("max_chars", 8000)
                            if not isinstance(max_chars, int) or isinstance(max_chars, bool):
                                raise ValueError("max_chars must be an integer")
                            max_chars = max(1, min(12000, max_chars))
                            text = file_path.read_text(encoding="utf-8", errors="replace")
                            namespace = {"result": {
                                "path": str(file_path),
                                "content": text[:max_chars],
                                "truncated": len(text) > max_chars,
                                "totalChars": len(text),
                            }}
                        elif command == "run_script":
                            raw_path = payload.get("script_path")
                            script_path = _workspace_file(raw_path, ".py")
                            namespace = runpy.run_path(str(script_path), run_name="__main__")
                        else:
                            code = payload.get("code")
                            if not isinstance(code, str) or not code.strip():
                                raise ValueError("code is required for run_code")
                            namespace = {
                                "__name__": "__main__",
                                "__file__": "/workspace/<browser_workspace_python>",
                                "WORKSPACE_DIR": "/workspace",
                            }
                            exec(compile(code, "<browser_workspace_python>", "exec"), namespace, namespace)

                    changed_files, deleted_files = _sync_workspace_changes(execution_root, initial_files)
                    return {
                        "ok": True,
                        "stdout": stdout_buffer.getvalue(),
                        "stderr": stderr_buffer.getvalue(),
                        "result": _json_safe(namespace.get("result")) if namespace and "result" in namespace else None,
                        "changedFiles": changed_files,
                        "deletedFiles": deleted_files,
                    }
                except BaseException as exc:
                    return {
                        "ok": False,
                        "stdout": stdout_buffer.getvalue(),
                        "stderr": stderr_buffer.getvalue(),
                        "error": "".join(traceback.format_exception(type(exc), exc, exc.__traceback__)),
                    }
                finally:
                    os.chdir(original_cwd)
                    if WORKSPACE_ROOT.exists():
                        shutil.rmtree(WORKSPACE_ROOT)
            """;

    private final BrowserSkillExecutionCoordinator coordinator;
    private final ObjectMapper mapper;
    private final SkillPackageBytesSnapshot snapshot;
    private final SkillEntrypointView entrypoint;

    public BrowserWorkspacePythonToolFactory(
            BrowserSkillExecutionCoordinator coordinator,
            ObjectMapper mapper,
            SkillPackageHasher hasher
    ) {
        this.coordinator = coordinator;
        this.mapper = mapper;
        var file = new SkillPackageHasher.PackageFile(
                SCRIPT_PATH,
                SCRIPT.getBytes(StandardCharsets.UTF_8)
        );
        List<SkillPackageHasher.PackageFile> files = List.of(file);
        this.snapshot = new SkillPackageBytesSnapshot(hasher.filesystemHash(files), files);
        this.entrypoint = new SkillEntrypointView(
                "run_python",
                SkillEntrypointRuntime.pyodide,
                SCRIPT_PATH,
                DESCRIPTION,
                INPUT_SCHEMA,
                ALLOWED_PACKAGES
        );
    }

    public BaseTool create(CurrentUser user, AgentContext context) {
        return new BrowserPyodideSkillTool(
                TOOL_NAME,
                SKILL_ID,
                user,
                context,
                snapshot,
                entrypoint,
                coordinator,
                mapper,
                SkillPackageLimits.DEFAULT_EXECUTION_TIMEOUT_MS
        );
    }
}
