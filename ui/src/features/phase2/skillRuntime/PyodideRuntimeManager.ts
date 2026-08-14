import type {
  MainToWorkerMessage,
  PyodideRuntimeState,
  WorkerToMainMessage,
} from './types';

export type WorkerFactory = () => Worker;

export interface PyodideRuntimeManagerOptions {
  indexURL: string;
  createWorker?: WorkerFactory;
  /** Default execution timeout if signal omits / for init waits. */
  defaultTimeoutMs?: number;
}

interface PendingExecute {
  executionId: string;
  resolve: (msg: WorkerToMainMessage) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

/**
 * Single Web Worker + FIFO queue for browser Pyodide skill execution.
 * Crash / timeout → terminate + recreate on next task.
 */
export class PyodideRuntimeManager {
  private state: PyodideRuntimeState = 'UNINITIALIZED';
  private worker: Worker | null = null;
  private readonly indexURL: string;
  private readonly createWorker: WorkerFactory;
  private readonly defaultTimeoutMs: number;
  private initPromise: Promise<void> | null = null;
  private readonly queue: Array<() => void> = [];
  private draining = false;
  private pending: PendingExecute | null = null;

  constructor(options: PyodideRuntimeManagerOptions) {
    this.indexURL = (options.indexURL ?? '').trim();
    this.defaultTimeoutMs = options.defaultTimeoutMs ?? 120_000;
    this.createWorker =
      options.createWorker ??
      (() =>
        new Worker(new URL('./pyodide.worker.ts', import.meta.url), {type: 'module',}));
  }

  getState(): PyodideRuntimeState {
    return this.state;
  }

  async ensureReady(): Promise<void> {
    if (!this.indexURL) {
      throw new Error('VITE_PYODIDE_INDEX_URL is required');
    }
    if (this.state === 'READY' || this.state === 'BUSY') return;
    // After timeout/crash, state is FAILED but a resolved initPromise must not
    // short-circuit recreation.
    if (this.state === 'FAILED' || this.state === 'UNINITIALIZED') {
      this.initPromise = null;
    }
    if (this.initPromise) return this.initPromise;

    this.state = 'LOADING';
    this.initPromise = new Promise<void>((resolve, reject) => {
      try {
        this.spawnWorker();
      } catch (error) {
        this.state = 'FAILED';
        this.initPromise = null;
        reject(error instanceof Error ? error : new Error('worker spawn failed'));
        return;
      }

      const initTimer = setTimeout(() => {
        cleanup();
        this.state = 'FAILED';
        this.initPromise = null;
        this.terminateWorker();
        reject(new Error('pyodide worker init timed out'));
      }, this.defaultTimeoutMs);

      const onReady = (event: MessageEvent<WorkerToMainMessage>) => {
        if (event.data.type === 'ready') {
          cleanup();
          this.state = 'READY';
          resolve();
        } else if (event.data.type === 'failed') {
          cleanup();
          this.state = 'FAILED';
          this.initPromise = null;
          reject(new Error(event.data.message));
        }
      };
      const onError = () => {
        cleanup();
        this.state = 'FAILED';
        this.initPromise = null;
        this.terminateWorker();
        reject(new Error('pyodide worker failed to initialize'));
      };
      const cleanup = () => {
        clearTimeout(initTimer);
        this.worker?.removeEventListener('message', onReady as EventListener);
        this.worker?.removeEventListener('error', onError);
      };
      this.worker?.addEventListener('message', onReady as EventListener);
      this.worker?.addEventListener('error', onError);
      this.post({
        type: 'init',
        indexURL: this.indexURL
      });
    });

    try {
      await this.initPromise;
    } catch (error) {
      this.initPromise = null;
      throw error;
    }
  }

  /**
   * Enqueue one execution. Resolves with worker result / failed message.
   */
  async execute(
    executionId: string,
    entrypointName: string,
    zipBytes: ArrayBuffer,
    timeoutMs: number,
    abortSignal?: AbortSignal,
  ): Promise<WorkerToMainMessage> {
    await this.ensureReady();

    return new Promise<WorkerToMainMessage>((resolve, reject) => {
      const run = () => {
        if (abortSignal?.aborted) {
          reject(new DOMException('aborted', 'AbortError'));
          this.drain();
          return;
        }
        if (!this.worker || this.state === 'FAILED') {
          this.initPromise = null;
          void this.ensureReady()
            .then(() => {
              this.queue.unshift(run);
              this.drain();
            })
            .catch(reject);
          return;
        }

        this.state = 'BUSY';
        const timer = setTimeout(() => {
          this.pending = null;
          this.terminateWorker();
          this.state = 'FAILED';
          this.initPromise = null;
          resolve({
            type: 'result',
            executionId,
            success: false,
            outputJson: null,
            stdout: null,
            stderr: null,
            errorCode: 'SKILL_EXECUTION_TIMEOUT',
            message: 'execution timed out',
          });
          this.drain();
        }, Math.max(1, timeoutMs || this.defaultTimeoutMs));

        this.pending = {
          executionId,
          resolve: (msg) => {
            clearTimeout(timer);
            this.pending = null;
            this.state = this.worker ? 'READY' : 'FAILED';
            resolve(msg);
            this.drain();
          },
          reject: (error) => {
            clearTimeout(timer);
            this.pending = null;
            this.state = 'FAILED';
            this.initPromise = null;
            reject(error);
            this.drain();
          },
          timer,
        };

        const onAbort = () => {
          clearTimeout(timer);
          this.pending = null;
          this.terminateWorker();
          this.state = 'FAILED';
          this.initPromise = null;
          reject(new DOMException('aborted', 'AbortError'));
          this.drain();
        };
        abortSignal?.addEventListener('abort', onAbort, { once: true });

        this.post({
          type: 'execute',
          executionId,
          entrypointName,
          zipBytes: zipBytes.slice(0),
          timeoutMs,
        });
      };

      this.queue.push(run);
      this.drain();
    });
  }

  /** Terminate current worker if executing the given id (or any). */
  cancel(executionId?: string): void {
    if (
      executionId &&
      this.pending &&
      this.pending.executionId !== executionId
    ) {
      return;
    }
    if (this.pending) {
      clearTimeout(this.pending.timer);
      this.pending.reject(new DOMException('aborted', 'AbortError'));
      this.pending = null;
    }
    this.terminateWorker();
    this.state = 'FAILED';
    this.initPromise = null;
  }

  dispose(): void {
    this.queue.length = 0;
    this.cancel();
    this.state = 'UNINITIALIZED';
  }

  private drain(): void {
    if (this.draining) return;
    if (this.pending) return;
    const next = this.queue.shift();
    if (!next) return;
    this.draining = true;
    try {
      next();
    } finally {
      this.draining = false;
    }
  }

  private spawnWorker(): void {
    this.terminateWorker();
    this.worker = this.createWorker();
    this.worker.addEventListener('message', this.onWorkerMessage);
    this.worker.addEventListener('error', this.onWorkerError);
  }

  private terminateWorker(): void {
    if (this.worker) {
      this.worker.removeEventListener('message', this.onWorkerMessage);
      this.worker.removeEventListener('error', this.onWorkerError);
      this.worker.terminate();
      this.worker = null;
    }
  }

  private readonly onWorkerMessage = (
    event: MessageEvent<WorkerToMainMessage>,
  ) => {
    const msg = event.data;
    if (msg.type === 'loading' || msg.type === 'ready') {
      return;
    }
    if (!this.pending) return;
    if (
      (msg.type === 'result' || msg.type === 'failed') &&
      msg.executionId &&
      msg.executionId !== this.pending.executionId
    ) {
      return;
    }
    this.pending.resolve(msg);
  };

  private readonly onWorkerError = () => {
    if (this.pending) {
      this.pending.reject(new Error('pyodide worker crashed'));
      this.pending = null;
    }
    this.terminateWorker();
    this.state = 'FAILED';
    this.initPromise = null;
    this.drain();
  };

  private post(msg: MainToWorkerMessage): void {
    this.worker?.postMessage(msg, msg.type === 'execute' ? [msg.zipBytes] : []);
  }
}

let singleton: PyodideRuntimeManager | null = null;

export function getPyodideIndexURL(): string {
  const url = import.meta.env.VITE_PYODIDE_INDEX_URL as string | undefined;
  return (url ?? '').trim();
}

export function getSharedPyodideRuntimeManager(
  createWorker?: WorkerFactory,
): PyodideRuntimeManager {
  if (!singleton) {
    singleton = new PyodideRuntimeManager({
      indexURL: getPyodideIndexURL(),
      createWorker,
    });
  }
  return singleton;
}

/** Test helper — reset singleton between cases. */
export function resetSharedPyodideRuntimeManager(): void {
  singleton?.dispose();
  singleton = null;
}
