import { describe, expect, it, vi } from 'vitest';
import { BrowserSkillExecutionContract } from '@/contracts';
import { BrowserSkillExecutionRunner } from '../BrowserSkillExecutionRunner';
import type { PyodideRuntimeManager } from '../PyodideRuntimeManager';
import { buildValidExecutionZip } from './zipFixture';

describe('PyodideResultCallbackTest', () => {
  it('posts BrowserSkillExecutionResult via injected postResult', async () => {
    const postResult = vi.fn(async () => undefined);
    const runtime = {
      execute: vi.fn(async () => ({
        type: 'result' as const,
        executionId: 'exec-cb-1',
        success: true,
        outputJson: '{"v":1}',
        stdout: null,
        stderr: null,
        errorCode: null,
        message: null,
      })),
      cancel: vi.fn(),
    } as unknown as PyodideRuntimeManager;

    const runner = new BrowserSkillExecutionRunner({
      runtime,
      fetchBundle: vi.fn(async () =>
        buildValidExecutionZip({ executionId: 'exec-cb-1' }),
      ),
      postResult,
    });

    await runner.handleLiveSignal({
      schemaVersion: BrowserSkillExecutionContract.SCHEMA_VERSION,
      executionId: 'exec-cb-1',
      skillId: 'skill-cb',
      entrypointName: 'main',
      packageHash: 'hash',
      timeoutMs: 1_000,
    });

    expect(postResult).toHaveBeenCalledTimes(1);
    expect(postResult).toHaveBeenCalledWith(
      'exec-cb-1',
      expect.objectContaining({
        schemaVersion: BrowserSkillExecutionContract.SCHEMA_VERSION,
        executionId: 'exec-cb-1',
        success: true,
        outputJson: '{"v":1}',
      }),
      expect.any(AbortSignal),
    );
  });
});
