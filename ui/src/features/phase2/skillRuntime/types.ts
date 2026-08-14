import type {
  BrowserSkillExecutionManifest,
  BrowserSkillExecutionResult,
  BrowserSkillExecutionSignal,
} from '@/contracts';

export type PyodideRuntimeState =
  | 'UNINITIALIZED'
  | 'LOADING'
  | 'READY'
  | 'BUSY'
  | 'FAILED';

export const BROWSER_SKILL_EXECUTION_LIMITS = {
  MAX_ZIP_BYTES: 8 * 1024 * 1024,
  MAX_ZIP_ENTRIES: 256,
  MAX_ENTRY_BYTES: 2 * 1024 * 1024,
  MAX_STDOUT_CHARS: 32_000,
  MAX_STDERR_CHARS: 16_000,
  MAX_OUTPUT_JSON_CHARS: 64_000,
  MAX_INPUT_JSON_CHARS: 64_000,
  MAX_PACKAGES: 32,
  MAX_PACKAGE_SPEC_LENGTH: 128,
} as const;

export type WorkerToMainMessage =
  | { type: 'ready' }
  | { type: 'loading'; progress?: string }
  | {
      type: 'result';
      executionId: string;
      success: boolean;
      outputJson: string | null;
      stdout: string | null;
      stderr: string | null;
      errorCode: string | null;
      message: string | null;
      truncated?: boolean;
    }
  | {
      type: 'failed';
      executionId?: string;
      errorCode: string;
      message: string;
    };

export type MainToWorkerMessage =
  | {
      type: 'init';
      indexURL: string;
    }
  | {
      type: 'execute';
      executionId: string;
      entrypointName: string;
      zipBytes: ArrayBuffer;
      timeoutMs: number;
    }
  | { type: 'cancel'; executionId?: string };

export interface QueuedSkillExecution {
  signal: BrowserSkillExecutionSignal;
  abortSignal?: AbortSignal;
}

export type { BrowserSkillExecutionManifest, BrowserSkillExecutionResult, BrowserSkillExecutionSignal };
