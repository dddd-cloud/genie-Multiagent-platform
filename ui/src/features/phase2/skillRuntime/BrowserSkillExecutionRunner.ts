import type {
  BrowserSkillExecutionResult,
  BrowserSkillExecutionSignal,
} from '@/contracts';
import { BROWSER_SKILL_EXECUTION_SCHEMA_VERSION } from '@/contracts';
import {
  buildFailureResult,
  fetchSkillExecutionBundle,
  postSkillExecutionResult,
} from '@/services/phase2/skillExecution';
import {
  BundleValidationError,
  validateSkillBundleAgainstSignal,
} from './bundleGuard';
import {
  getSharedPyodideRuntimeManager,
  type PyodideRuntimeManager,
} from './PyodideRuntimeManager';
import { parseBrowserSkillExecutionSignal } from './signal';
import type { WorkerToMainMessage } from './types';
import { getBoundWorkspaceExecutionContext } from '@/features/workspace/executionBind';
import { createWorkspaceExecutionFileBridge } from '@/services/workspace/workspaceExecutionFiles';

export interface BrowserSkillExecutionRunnerOptions {
  runtime?: PyodideRuntimeManager;
  fetchBundle?: typeof fetchSkillExecutionBundle;
  postResult?: typeof postSkillExecutionResult;
  /** When false (snapshot hydrate), signals are ignored. */
  allowExecute?: boolean;
}

/**
 * Consumes live skill_execution SSE control packets.
 * Snapshot / hydrate must call with allowExecute=false (or use ignoreSignal).
 */
export class BrowserSkillExecutionRunner {
  private readonly runtime: PyodideRuntimeManager;
  private readonly fetchBundle: typeof fetchSkillExecutionBundle;
  private readonly postResult: typeof postSkillExecutionResult;
  private allowExecute: boolean;
  private readonly inFlight = new Map<string, AbortController>();
  /** Prevent re-executing the same executionId after a terminal result. */
  private readonly finishedIds = new Set<string>();
  private stopped = false;

  constructor(options: BrowserSkillExecutionRunnerOptions = {}) {
    this.runtime = options.runtime ?? getSharedPyodideRuntimeManager();
    this.fetchBundle = options.fetchBundle ?? fetchSkillExecutionBundle;
    this.postResult = options.postResult ?? postSkillExecutionResult;
    this.allowExecute = options.allowExecute !== false;
  }

  setAllowExecute(allow: boolean): void {
    this.allowExecute = allow;
  }

  /**
   * Live SSE path — may trigger GET bundle → Worker → POST result.
   */
  async handleLiveSignal(
    rawSignal: unknown,
    abortSignal?: AbortSignal,
  ): Promise<BrowserSkillExecutionResult | null> {
    if (this.stopped || !this.allowExecute) {
      return null;
    }
    const signal = parseBrowserSkillExecutionSignal(rawSignal);
    if (!signal) {
      return null;
    }
    return this.run(signal, abortSignal);
  }

  /**
   * Snapshot hydrate / replay — MUST NOT execute Python.
   */
  ignoreSnapshotSignal(): void {
    // intentionally no-op (hard contract rule)
  }

  stop(): void {
    this.stopped = true;
    for (const [executionId, controller] of this.inFlight) {
      controller.abort();
      this.runtime.cancel(executionId);
    }
    this.inFlight.clear();
  }

  private async run(
    signal: BrowserSkillExecutionSignal,
    outerAbort?: AbortSignal,
  ): Promise<BrowserSkillExecutionResult> {
    if (this.finishedIds.has(signal.executionId)) {
      return buildFailureResult(
        signal.executionId,
        'SKILL_EXECUTION_FAILED',
        'execution already finished',
      );
    }
    if (this.inFlight.has(signal.executionId)) {
      return buildFailureResult(
        signal.executionId,
        'SKILL_EXECUTION_FAILED',
        'duplicate live signal ignored',
      );
    }

    const controller = new AbortController();
    this.inFlight.set(signal.executionId, controller);
    const onOuterAbort = () => controller.abort();
    outerAbort?.addEventListener('abort', onOuterAbort, { once: true });

    try {
      const zipBytes = await this.fetchBundle(
        signal.executionId,
        controller.signal,
      );
      // Main-thread defensive unpack + manifest bind before Worker.
      validateSkillBundleAgainstSignal(zipBytes, signal);

      const bind = getBoundWorkspaceExecutionContext();
      const bridge = bind
        ? createWorkspaceExecutionFileBridge({
            service: bind.service,
            scope: bind.scope,
            fileIds: bind.fileIds,
          })
        : null;
      const workspaceFiles = bridge
        ? await bridge.loadInputFiles(signal, controller.signal)
        : undefined;

      const workerMsg = await this.runtime.execute(
        signal.executionId,
        signal.entrypointName,
        zipBytes,
        signal.timeoutMs,
        controller.signal,
        workspaceFiles,
      );
      if (bridge && workerMsg.type === 'result' && workerMsg.outputFiles?.length) {
        try {
          await bridge.saveOutputFiles(signal, workerMsg.outputFiles, controller.signal);
        } catch {
          // Output persistence must not hide a successful Python result.
        }
      }
      const result = toResult(signal.executionId, workerMsg);
      this.finishedIds.add(signal.executionId);
      try {
        await this.postResult(signal.executionId, result, controller.signal);
      } catch {
        // Result callback failure must not alter chat terminal state.
      }
      return result;
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        const result = buildFailureResult(
          signal.executionId,
          'SKILL_EXECUTION_FAILED',
          'cancelled',
        );
        this.finishedIds.add(signal.executionId);
        try {
          await this.postResult(signal.executionId, result);
        } catch {
          /* ignore */
        }
        return result;
      }
      const errorCode =
        error instanceof BundleValidationError
          ? error.errorCode
          : 'SKILL_EXECUTION_FAILED';
      const result = buildFailureResult(
        signal.executionId,
        errorCode,
        error instanceof Error ? error.message.slice(0, 500) : 'execution failed',
      );
      this.finishedIds.add(signal.executionId);
      try {
        await this.postResult(signal.executionId, result);
      } catch {
        /* ignore */
      }
      return result;
    } finally {
      outerAbort?.removeEventListener('abort', onOuterAbort);
      this.inFlight.delete(signal.executionId);
    }
  }
}

function toResult(
  executionId: string,
  msg: WorkerToMainMessage,
): BrowserSkillExecutionResult {
  if (msg.type === 'result') {
    return {
      schemaVersion: BROWSER_SKILL_EXECUTION_SCHEMA_VERSION,
      executionId,
      success: msg.success,
      outputJson: msg.outputJson,
      stdout: msg.stdout,
      stderr: msg.stderr,
      errorCode: msg.errorCode,
      message: msg.message,
    };
  }
  if (msg.type === 'failed') {
    return buildFailureResult(executionId, msg.errorCode, msg.message);
  }
  return buildFailureResult(
    executionId,
    'SKILL_EXECUTION_FAILED',
    'unexpected worker message',
  );
}

let sharedRunner: BrowserSkillExecutionRunner | null = null;

export function getSharedBrowserSkillExecutionRunner(): BrowserSkillExecutionRunner {
  if (!sharedRunner) {
    sharedRunner = new BrowserSkillExecutionRunner({ allowExecute: true });
  }
  return sharedRunner;
}

export function resetSharedBrowserSkillExecutionRunner(): void {
  sharedRunner?.stop();
  sharedRunner = null;
}
