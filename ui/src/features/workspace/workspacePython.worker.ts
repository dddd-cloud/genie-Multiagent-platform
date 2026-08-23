/// <reference lib="webworker" />

import { normalizeFileName } from '@/platform/workspace/types';

declare const self: DedicatedWorkerGlobalScope;

type WorkspaceFile = { name: string; mimeType: string; bytes: ArrayBuffer };
type RunMessage = {
  type: 'run';
  executionId: string;
  entrypoint: string;
  files: readonly WorkspaceFile[];
  indexURL: string;
};

type PyodideFs = {
  mkdirTree(path: string): void;
  writeFile(path: string, data: Uint8Array): void;
  readFile(path: string, options?: { encoding?: 'binary' }): Uint8Array | string;
  readdir(path: string): string[];
  stat(path: string): { mode: number };
  isDir(mode: number): boolean;
  chdir(path: string): void;
};

type Pyodide = {
  FS: PyodideFs;
  globals: { set(name: string, value: unknown): void; delete(name: string): void };
  runPythonAsync(code: string): Promise<unknown>;
};

let runtime: Pyodide | null = null;
let runtimeUrl = '';

async function getRuntime(indexURL: string): Promise<Pyodide> {
  if (runtime && runtimeUrl === indexURL) return runtime;
  const { loadPyodide } = await import('pyodide');
  runtime = (await loadPyodide({ indexURL })) as unknown as Pyodide;
  runtimeUrl = indexURL;
  return runtime;
}

function collectFiles(fs: PyodideFs, root: string): WorkspaceFile[] {
  const output: WorkspaceFile[] = [];
  for (const name of fs.readdir(root)) {
    if (name === '.' || name === '..' || name === '__pycache__') continue;
    const path = `${root}/${name}`;
    const stat = fs.stat(path);
    if (fs.isDir(stat.mode)) continue;
    const safeName = normalizeFileName(name);
    const raw = fs.readFile(path, { encoding: 'binary' });
    const bytes = raw instanceof Uint8Array ? raw.slice() : new Uint8Array();
    output.push({
      name: safeName,
      mimeType: safeName.endsWith('.py') ? 'text/x-python' : 'application/octet-stream',
      bytes: bytes.buffer,
    });
  }
  return output;
}

self.onmessage = (event: MessageEvent<RunMessage>) => {
  const message = event.data;
  if (message.type !== 'run') return;
  void (async () => {
    try {
      const pyodide = await getRuntime(message.indexURL);
      const root = `/workspace/${message.executionId}`;
      pyodide.FS.mkdirTree(root);
      for (const file of message.files) {
        pyodide.FS.writeFile(`${root}/${normalizeFileName(file.name)}`, new Uint8Array(file.bytes));
      }
      pyodide.FS.chdir(root);
      pyodide.globals.set('__joy_workspace_code', new TextDecoder().decode(
        new Uint8Array(message.files.find((file) => file.name === message.entrypoint)?.bytes ?? new ArrayBuffer(0)),
      ));
      pyodide.globals.set('__joy_workspace_entry', message.entrypoint);
      const raw = await pyodide.runPythonAsync(`
import contextlib, io, json, traceback
_stdout, _stderr = io.StringIO(), io.StringIO()
_success, _error = True, None
try:
    with contextlib.redirect_stdout(_stdout), contextlib.redirect_stderr(_stderr):
        exec(compile(__joy_workspace_code, __joy_workspace_entry, "exec"), {
            "__name__": "__main__", "__file__": __joy_workspace_entry
        })
except Exception:
    _success = False
    _error = traceback.format_exc()
json.dumps({"success": _success, "stdout": _stdout.getvalue(), "stderr": _stderr.getvalue(), "error": _error})
`);
      pyodide.globals.delete('__joy_workspace_code');
      pyodide.globals.delete('__joy_workspace_entry');
      const result = JSON.parse(String(raw)) as {
        success: boolean;
        stdout: string;
        stderr: string;
        error: string | null;
      };
      const files = collectFiles(pyodide.FS, root);
      self.postMessage(
        { type: 'result', executionId: message.executionId, ...result, files },
        { transfer: files.map((file) => file.bytes) },
      );
    } catch (error) {
      self.postMessage({
        type: 'result',
        executionId: message.executionId,
        success: false,
        stdout: '',
        stderr: '',
        error: error instanceof Error ? error.message : '浏览器 Python 执行失败',
        files: [],
      });
    }
  })();
};
