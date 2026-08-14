import { describe, expect, it, vi } from 'vitest';
import { BrowserSkillExecutionContract } from '@/contracts';
import { BrowserSkillExecutionRunner } from '../BrowserSkillExecutionRunner';
import type { PyodideRuntimeManager } from '../PyodideRuntimeManager';
import type { WorkerToMainMessage } from '../types';
import { buildValidExecutionZip } from './zipFixture';

function liveSignal(executionId = 'exec-live-1') {
  return {
    schemaVersion: BrowserSkillExecutionContract.SCHEMA_VERSION,
    executionId,
    skillId: 'skill-1',
    entrypointName: 'main',
    packageHash: 'abc123',
    timeoutMs: 5_000,
  };
}

describe('BrowserSkillLiveSignalTest', () => {
  it('handleLiveSignal fetches bundle, runs runtime, and posts result', async () => {
    const fetchBundle = vi.fn(async () =>
      buildValidExecutionZip({ executionId: 'exec-live-1' }),
    );
    const postResult = vi.fn(async () => undefined);
    const workerMsg: WorkerToMainMessage = {
      type: 'result',
      executionId: 'exec-live-1',
      success: true,
      outputJson: '{"ok":true}',
      stdout: null,
      stderr: null,
      errorCode: null,
      message: null,
    };
    const runtime = {
      execute: vi.fn(async () => workerMsg),
      cancel: vi.fn(),
    } as unknown as PyodideRuntimeManager;

    const runner = new BrowserSkillExecutionRunner({
      runtime,
      fetchBundle,
      postResult,
      allowExecute: true,
    });

    const result = await runner.handleLiveSignal(liveSignal());
    expect(fetchBundle).toHaveBeenCalledWith(
      'exec-live-1',
      expect.any(AbortSignal),
    );
    expect(runtime.execute).toHaveBeenCalled();
    expect(postResult).toHaveBeenCalled();
    expect(result).toMatchObject({
      schemaVersion: BrowserSkillExecutionContract.SCHEMA_VERSION,
      executionId: 'exec-live-1',
      success: true,
      outputJson: '{"ok":true}',
    });
  });
});
