import { describe, expect, it, vi } from 'vitest';
import { BrowserSkillExecutionContract } from '@/contracts';
import { BrowserSkillExecutionRunner } from '../BrowserSkillExecutionRunner';
import type { PyodideRuntimeManager } from '../PyodideRuntimeManager';
import type { WorkerToMainMessage } from '../types';
import { buildValidExecutionZip } from './zipFixture';

describe('PyodideJsonInputOutputTest', () => {
  it('maps worker result messages onto BrowserSkillExecutionResult', async () => {
    const workerMsg: WorkerToMainMessage = {
      type: 'result',
      executionId: 'exec-json-1',
      success: true,
      outputJson: '{"answer":42}',
      stdout: 'hi',
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
      fetchBundle: vi.fn(async () =>
        buildValidExecutionZip({ executionId: 'exec-json-1' }),
      ),
      postResult: vi.fn(async () => undefined),
    });

    const result = await runner.handleLiveSignal({
      schemaVersion: BrowserSkillExecutionContract.SCHEMA_VERSION,
      executionId: 'exec-json-1',
      skillId: 'skill-json',
      entrypointName: 'main',
      packageHash: 'hash',
      timeoutMs: 1_000,
    });

    expect(result).toEqual({
      schemaVersion: BrowserSkillExecutionContract.SCHEMA_VERSION,
      executionId: 'exec-json-1',
      success: true,
      outputJson: '{"answer":42}',
      stdout: 'hi',
      stderr: null,
      errorCode: null,
      message: null,
    });
  });

  it('maps worker failed messages to failure result', async () => {
    const workerMsg: WorkerToMainMessage = {
      type: 'failed',
      executionId: 'exec-json-2',
      errorCode: 'SKILL_EXECUTION_FAILED',
      message: 'boom',
    };
    const runtime = {
      execute: vi.fn(async () => workerMsg),
      cancel: vi.fn(),
    } as unknown as PyodideRuntimeManager;

    const runner = new BrowserSkillExecutionRunner({
      runtime,
      fetchBundle: vi.fn(async () =>
        buildValidExecutionZip({ executionId: 'exec-json-2' }),
      ),
      postResult: vi.fn(async () => undefined),
    });

    const result = await runner.handleLiveSignal({
      schemaVersion: BrowserSkillExecutionContract.SCHEMA_VERSION,
      executionId: 'exec-json-2',
      skillId: 'skill-json',
      entrypointName: 'main',
      packageHash: 'hash',
      timeoutMs: 1_000,
    });

    expect(result).toMatchObject({
      executionId: 'exec-json-2',
      success: false,
      errorCode: 'SKILL_EXECUTION_FAILED',
      message: 'boom',
    });
  });
});
