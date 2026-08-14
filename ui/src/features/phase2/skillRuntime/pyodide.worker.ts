/// <reference lib="webworker" />
/**
 * Pyodide runs only inside this Worker — never on the UI thread.
 * One Worker + FIFO queue is owned by PyodideRuntimeManager on the main thread.
 */
import type { MainToWorkerMessage, WorkerToMainMessage } from './types';
import { BROWSER_SKILL_EXECUTION_LIMITS as LIMITS } from './types';
import {BROWSER_SKILL_EXECUTION_MANIFEST_PATH,} from '@/contracts';
import { unzipSync } from 'fflate';
import {
  isAllowedPyodidePackageSpec,
  isSafeRelativePath,
  parseExecutionManifest,
} from './signal';

declare const self: DedicatedWorkerGlobalScope;

type PyodideInterface = {
  loadPackage: (names: string | string[]) => Promise<unknown>;
  runPythonAsync: (code: string, options?: { globals?: unknown }) => Promise<unknown>;
  globals: {
    get: (name: string) => unknown;
    set: (name: string, value: unknown) => void;
  };
  FS: {
    mkdirTree: (path: string) => void;
    writeFile: (path: string, data: Uint8Array | string) => void;
    chdir: (path: string) => void;
    readdir: (path: string) => string[];
    isDir: (mode: number) => boolean;
    stat: (path: string) => { mode: number };
    unlink: (path: string) => void;
    rmdir: (path: string) => void;
  };
  pyimport: (name: string) => {
    install: (specs: string | string[]) => Promise<void>;
    destroy?: () => void;
  };
};

let pyodide: PyodideInterface | null = null;
let busy = false;

function post(msg: WorkerToMainMessage): void {
  self.postMessage(msg);
}

function truncate(text: string, max: number): { text: string; truncated: boolean } {
  if (text.length <= max) return {
    text,
    truncated: false
  };
  return {
    text: text.slice(0, max),
    truncated: true
  };
}

function safeJsonStringify(value: unknown): string | null {
  try {
    const json = JSON.stringify(value);
    if (json === undefined) return null;
    if (json.length > LIMITS.MAX_OUTPUT_JSON_CHARS) {
      return json.slice(0, LIMITS.MAX_OUTPUT_JSON_CHARS);
    }
    return json;
  } catch {
    return null;
  }
}

function unpackToFs(
  fs: PyodideInterface['FS'],
  root: string,
  zipBytes: ArrayBuffer,
): void {
  const entries = unzipSync(new Uint8Array(zipBytes), {
    filter(file) {
      return !file.name.endsWith('/');
    },
  });
  const names = Object.keys(entries);
  if (names.length === 0 || names.length > LIMITS.MAX_ZIP_ENTRIES) {
    throw new Error(`zip entry count invalid: ${names.length}`);
  }
  fs.mkdirTree(root);
  for (const name of names) {
    const normalized = name.replace(/\\/g, '/');
    if (!isSafeRelativePath(normalized)) {
      throw new Error(`unsafe path: ${name}`);
    }
    const data = entries[name];
    if (!data || data.byteLength > LIMITS.MAX_ENTRY_BYTES) {
      throw new Error(`entry too large: ${name}`);
    }
    const full = `${root}/${normalized}`;
    const dir = full.slice(0, full.lastIndexOf('/'));
    if (dir && dir !== root) {
      fs.mkdirTree(dir);
    }
    fs.writeFile(full, data);
  }
}

function rmTree(fs: PyodideInterface['FS'], path: string): void {
  try {
    const entries = fs.readdir(path);
    for (const name of entries) {
      if (name === '.' || name === '..') continue;
      const child = `${path}/${name}`;
      try {
        const st = fs.stat(child);
        if (fs.isDir(st.mode)) {
          rmTree(fs, child);
        } else {
          fs.unlink(child);
        }
      } catch {
        try {
          fs.unlink(child);
        } catch {
          /* ignore */
        }
      }
    }
    fs.rmdir(path);
  } catch {
    /* ignore cleanup errors */
  }
}

async function initPyodide(indexURL: string): Promise<void> {
  if (pyodide) return;
  post({
    type: 'loading',
    progress: 'loading pyodide'
  });
  // Dynamic import keeps the main UI bundle free of eager Pyodide weight.
  const { loadPyodide } = await import('pyodide');
  pyodide = (await loadPyodide({ indexURL })) as unknown as PyodideInterface;
  post({ type: 'ready' });
}

async function installPackages(packages: string[]): Promise<void> {
  if (!pyodide || packages.length === 0) return;
  for (const spec of packages) {
    if (!isAllowedPyodidePackageSpec(spec)) {
      throw Object.assign(new Error(`unsupported package: ${spec}`), {errorCode: 'SKILL_EXECUTION_FAILED',});
    }
  }

  const micropipSpecs: string[] = [];
  for (const spec of packages) {
    // Version constraints must go through micropip — never silently drop them.
    if (/[<>=!~]/.test(spec)) {
      micropipSpecs.push(spec);
      continue;
    }
    try {
      await pyodide.loadPackage(spec);
    } catch {
      micropipSpecs.push(spec);
    }
  }

  if (micropipSpecs.length === 0) return;

  await pyodide.loadPackage('micropip');
  const micropip = pyodide.pyimport('micropip');
  try {
    await micropip.install(micropipSpecs);
  } catch (error) {
    throw Object.assign(
      new Error(
        error instanceof Error
          ? `package install failed: ${error.message}`
          : 'package install failed',
      ),
      { errorCode: 'SKILL_EXECUTION_FAILED' },
    );
  } finally {
    micropip.destroy?.();
  }
}

async function execute(
  executionId: string,
  entrypointName: string,
  zipBytes: ArrayBuffer,
): Promise<WorkerToMainMessage> {
  if (!pyodide) {
    return {
      type: 'failed',
      executionId,
      errorCode: 'SKILL_EXECUTION_FAILED',
      message: 'pyodide not initialized',
    };
  }

  const root = `/skills/${executionId}`;
  let stdout = '';
  let stderr = '';
  let truncated = false;

  const stdoutHandler = (text: string) => {
    const next = stdout + text;
    const cut = truncate(next, LIMITS.MAX_STDOUT_CHARS);
    stdout = cut.text;
    truncated = truncated || cut.truncated;
  };
  const stderrHandler = (text: string) => {
    const next = stderr + text;
    const cut = truncate(next, LIMITS.MAX_STDERR_CHARS);
    stderr = cut.text;
    truncated = truncated || cut.truncated;
  };

  try {
    unpackToFs(pyodide.FS, root, zipBytes);
    const manifestRaw = new TextDecoder('utf-8').decode(
      (() => {
        const entries = unzipSync(new Uint8Array(zipBytes));
        const bytes = entries[BROWSER_SKILL_EXECUTION_MANIFEST_PATH];
        if (!bytes) {
          throw new Error(`missing ${BROWSER_SKILL_EXECUTION_MANIFEST_PATH}`);
        }
        return bytes;
      })(),
    );
    const manifestJson = JSON.parse(manifestRaw) as unknown;
    const manifest = parseExecutionManifest(manifestJson, executionId);
    if (!manifest) {
      return {
        type: 'result',
        executionId,
        success: false,
        outputJson: null,
        stdout: null,
        stderr: null,
        errorCode: 'SKILL_PACKAGE_INVALID',
        message: 'invalid execution manifest',
      };
    }
    if (manifest.entrypointName !== entrypointName) {
      return {
        type: 'result',
        executionId,
        success: false,
        outputJson: null,
        stdout: null,
        stderr: null,
        errorCode: 'SKILL_PACKAGE_INVALID',
        message: 'entrypointName mismatch vs signal',
      };
    }

    await installPackages(manifest.packages);
    pyodide.FS.chdir(root);

    // Capture via sys.stdout/stderr only — do not also hook setStdout,
    // or print() would be duplicated.
    await pyodide.runPythonAsync(`
import sys, json, asyncio, importlib.util, pathlib
_joy_stdout = []
_joy_stderr = []
class _JoyCapture:
    def __init__(self, buf):
        self._buf = buf
    def write(self, s):
        if s:
            self._buf.append(s)
    def flush(self):
        pass
sys.stdout = _JoyCapture(_joy_stdout)
sys.stderr = _JoyCapture(_joy_stderr)
`);

    const scriptPath = `${root}/${manifest.scriptRelativePath}`;
    const inputObj = JSON.parse(manifest.inputJson);

    pyodide.globals.set('_joy_script_path', scriptPath);
    pyodide.globals.set('_joy_input', inputObj);

    await pyodide.runPythonAsync(`
_script = pathlib.Path(_joy_script_path)
_spec = importlib.util.spec_from_file_location("_joy_skill_mod", _script)
if _spec is None or _spec.loader is None:
    raise RuntimeError("cannot load entrypoint module")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
_main = getattr(_mod, "main", None)
if _main is None:
    raise RuntimeError("entrypoint must define main(input)")
_result = _main(_joy_input)
if asyncio.iscoroutine(_result):
    _result = await _result
_joy_result = _result
_joy_stdout_text = "".join(_joy_stdout)
_joy_stderr_text = "".join(_joy_stderr)
`);

    const resultProxy = pyodide.globals.get('_joy_result');
    const outStdout = String(pyodide.globals.get('_joy_stdout_text') ?? '');
    const outStderr = String(pyodide.globals.get('_joy_stderr_text') ?? '');
    if (outStdout) stdoutHandler(outStdout);
    if (outStderr) stderrHandler(outStderr);

    let jsResult: unknown = resultProxy;
    try {
      if (
        resultProxy &&
        typeof resultProxy === 'object' &&
        'toJs' in resultProxy &&
        typeof (resultProxy as { toJs: (o?: unknown) => unknown }).toJs ===
          'function'
      ) {
        jsResult = (resultProxy as { toJs: (o?: unknown) => unknown }).toJs({dict_converter: Object.fromEntries,});
      }
    } catch {
      /* keep proxy as-is for stringify attempt */
    }

    const outputJson = safeJsonStringify(jsResult);
    if (outputJson === null) {
      return {
        type: 'result',
        executionId,
        success: false,
        outputJson: null,
        stdout: stdout || null,
        stderr: stderr || null,
        errorCode: 'SKILL_EXECUTION_FAILED',
        message: 'main() return value is not JSON-serializable',
        truncated,
      };
    }

    try {
      if (
        resultProxy &&
        typeof resultProxy === 'object' &&
        'destroy' in resultProxy &&
        typeof (resultProxy as { destroy: () => void }).destroy === 'function'
      ) {
        (resultProxy as { destroy: () => void }).destroy();
      }
    } catch {
      /* ignore */
    }

    return {
      type: 'result',
      executionId,
      success: true,
      outputJson,
      stdout: stdout || null,
      stderr: stderr || null,
      errorCode: null,
      message: null,
      truncated,
    };
  } catch (error) {
    const message =
      error instanceof Error ? error.message.slice(0, 500) : 'execution failed';
    const errorCode =
      error &&
      typeof error === 'object' &&
      'errorCode' in error &&
      typeof (error as { errorCode: unknown }).errorCode === 'string'
        ? (error as { errorCode: string }).errorCode
        : 'SKILL_EXECUTION_FAILED';
    stderrHandler(message);
    return {
      type: 'result',
      executionId,
      success: false,
      outputJson: null,
      stdout: stdout || null,
      stderr: stderr || null,
      errorCode,
      message,
      truncated,
    };
  } finally {
    try {
      pyodide.FS.chdir('/');
    } catch {
      /* ignore */
    }
    rmTree(pyodide.FS, root);
  }
}

self.onmessage = (event: MessageEvent<MainToWorkerMessage>) => {
  const msg = event.data;
  void (async () => {
    try {
      if (msg.type === 'init') {
        await initPyodide(msg.indexURL);
        return;
      }
      if (msg.type === 'cancel') {
        // Hard cancel is handled by main thread terminate(); soft no-op here.
        return;
      }
      if (msg.type === 'execute') {
        if (busy) {
          post({
            type: 'failed',
            executionId: msg.executionId,
            errorCode: 'SKILL_EXECUTION_FAILED',
            message: 'worker busy',
          });
          return;
        }
        if (zipBytesTooLarge(msg.zipBytes)) {
          post({
            type: 'result',
            executionId: msg.executionId,
            success: false,
            outputJson: null,
            stdout: null,
            stderr: null,
            errorCode: 'SKILL_PACKAGE_INVALID',
            message: 'zip too large',
          });
          return;
        }
        busy = true;
        try {
          const result = await execute(
            msg.executionId,
            msg.entrypointName,
            msg.zipBytes,
          );
          post(result);
        } finally {
          busy = false;
        }
      }
    } catch (error) {
      post({
        type: 'failed',
        executionId: msg.type === 'execute' ? msg.executionId : undefined,
        errorCode: 'SKILL_EXECUTION_FAILED',
        message: error instanceof Error ? error.message : 'worker failure',
      });
      busy = false;
    }
  })();
};

function zipBytesTooLarge(buf: ArrayBuffer): boolean {
  return buf.byteLength <= 0 || buf.byteLength > LIMITS.MAX_ZIP_BYTES;
}
