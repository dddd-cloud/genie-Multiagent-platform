import type {
  BrowserSkillExecutionManifest,
  BrowserSkillExecutionResult,
  BrowserSkillExecutionSignal,
} from '@/contracts';
import type { WorkspaceBinaryFile } from '@/platform/workspace/types';

export type PyodideRuntimeState =
  | 'UNINITIALIZED'
  | 'LOADING'
  | 'READY'
  | 'BUSY'
  | 'FAILED';

export const BROWSER_SKILL_EXECUTION_LIMITS = {
  MAX_ZIP_BYTES: 8 * 1024 * 1024,
  MAX_ZIP_UNCOMPRESSED_BYTES: 32 * 1024 * 1024,
  MAX_ZIP_ENTRIES: 256,
  MAX_ENTRY_BYTES: 2 * 1024 * 1024,
  MAX_STDOUT_CHARS: 32_000,
  MAX_STDERR_CHARS: 16_000,
  MAX_OUTPUT_JSON_CHARS: 64_000,
  MAX_INPUT_JSON_CHARS: 64_000,
  MAX_PACKAGES: 32,
  MAX_PACKAGE_SPEC_LENGTH: 128,
  MAX_WORKSPACE_INPUT_FILES: 32,
  MAX_WORKSPACE_INPUT_BYTES: 50 * 1024 * 1024,
  MAX_WORKSPACE_OUTPUT_FILES: 32,
  MAX_WORKSPACE_OUTPUT_BYTES: 50 * 1024 * 1024,
  MAX_WORKSPACE_OUTPUT_FILE_BYTES: 25 * 1024 * 1024,
} as const;

export type WorkspaceExecutionInputFile = WorkspaceBinaryFile;
export type WorkspaceExecutionOutputFile = WorkspaceBinaryFile;

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
      outputFiles?: readonly WorkspaceExecutionOutputFile[];
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
      workspaceFiles?: readonly WorkspaceExecutionInputFile[];
      timeoutMs: number;
    }
  | { type: 'cancel'; executionId?: string };

export interface QueuedSkillExecution {
  signal: BrowserSkillExecutionSignal;
  abortSignal?: AbortSignal;
}

export type { BrowserSkillExecutionManifest, BrowserSkillExecutionResult, BrowserSkillExecutionSignal };
