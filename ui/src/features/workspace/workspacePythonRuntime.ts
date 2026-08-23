import type { WorkspaceBinaryFile } from '@/platform/workspace/types';
import { getPyodideIndexURL } from '@/features/phase2/skillRuntime/PyodideRuntimeManager';

export interface WorkspacePythonResult {
  readonly success: boolean;
  readonly stdout: string;
  readonly stderr: string;
  readonly error: string | null;
  readonly files: readonly WorkspaceBinaryFile[];
}

export function runWorkspacePython(
  entrypoint: string,
  files: readonly WorkspaceBinaryFile[],
  timeoutMs = 120_000,
): Promise<WorkspacePythonResult> {
  const worker = new Worker(new URL('./workspacePython.worker.ts', import.meta.url), { type: 'module' });
  const executionId = `workspace-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      worker.terminate();
      reject(new Error('浏览器 Python 执行超时'));
    }, timeoutMs);
    worker.onerror = () => {
      clearTimeout(timer);
      worker.terminate();
      reject(new Error('浏览器 Python Worker 启动失败'));
    };
    worker.onmessage = (event: MessageEvent<WorkspacePythonResult & { executionId: string }>) => {
      if (event.data.executionId !== executionId) return;
      clearTimeout(timer);
      worker.terminate();
      resolve(event.data);
    };
    const copies = files.map((file) => ({ ...file, bytes: file.bytes.slice(0) }));
    worker.postMessage({
      type: 'run',
      executionId,
      entrypoint,
      files: copies,
      indexURL: getPyodideIndexURL(),
    }, copies.map((file) => file.bytes));
  });
}
