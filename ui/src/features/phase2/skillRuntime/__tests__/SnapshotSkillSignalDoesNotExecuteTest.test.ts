import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import type { ConversationMessageResponse } from '@/contracts';
import { BrowserSkillExecutionContract } from '@/contracts';
import { hydrateConversation } from '@/features/conversation/hydrateConversation';
import * as skillExecution from '@/services/phase2/skillExecution';
import { BrowserSkillExecutionRunner } from '../BrowserSkillExecutionRunner';
import type { PyodideRuntimeManager } from '../PyodideRuntimeManager';

function liveSignal(executionId = 'exec-snap-1') {
  return {
    schemaVersion: BrowserSkillExecutionContract.SCHEMA_VERSION,
    executionId,
    skillId: 'skill-1',
    entrypointName: 'main',
    packageHash: 'abc123',
    timeoutMs: 5_000,
  };
}

describe('SnapshotSkillSignalDoesNotExecuteTest', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('allowExecute=false does not call fetchBundle', async () => {
    const fetchBundle = vi.fn(async () => new ArrayBuffer(8));
    const runtime = {
      execute: vi.fn(),
      cancel: vi.fn(),
    } as unknown as PyodideRuntimeManager;
    const runner = new BrowserSkillExecutionRunner({
      runtime,
      fetchBundle,
      postResult: vi.fn(async () => undefined),
      allowExecute: false,
    });

    const result = await runner.handleLiveSignal(liveSignal());
    expect(result).toBeNull();
    expect(fetchBundle).not.toHaveBeenCalled();
    expect(runtime.execute).not.toHaveBeenCalled();
  });

  it('ignoreSnapshotSignal is a no-op and never fetches', () => {
    const fetchBundle = vi.fn(async () => new ArrayBuffer(8));
    const runner = new BrowserSkillExecutionRunner({
      fetchBundle,
      allowExecute: true,
    });
    runner.ignoreSnapshotSignal();
    expect(fetchBundle).not.toHaveBeenCalled();
  });

  it('hydrateConversation skill_execution packets skip execution (no bundle fetch)', () => {
    const fetchSpy = vi
      .spyOn(skillExecution, 'fetchSkillExecutionBundle')
      .mockResolvedValue(new ArrayBuffer(8));

    const snapshot = {
      payloadVersion: 1,
      truncated: false,
      events: [
        {
          status: 'success',
          response: '',
          responseAll: '',
          finished: false,
          useTimes: 0,
          useTokens: 0,
          resultMap: {
            [BrowserSkillExecutionContract.RESULT_MAP_KEY]: liveSignal(
              'exec-hydrate-1',
            ),
          },
          responseType: 'json',
          traceId: 't1',
          reqId: 'req-skill',
          encrypted: false,
          query: null,
          messages: null,
          packageType: BrowserSkillExecutionContract.SSE_PACKAGE_TYPE,
          errorMsg: null,
        },
        {
          status: 'success',
          response: 'done',
          responseAll: 'done',
          finished: true,
          useTimes: 0,
          useTokens: 0,
          resultMap: {},
          responseType: 'text',
          traceId: 't1',
          reqId: 'req-skill',
          encrypted: false,
          query: null,
          messages: null,
          packageType: 'result',
          errorMsg: null,
        },
      ],
    };

    const messages: ConversationMessageResponse[] = [
      {
        id: 'u1',
        turnNo: 1,
        role: 'USER',
        status: 'COMPLETED',
        requestId: 'req-skill',
        content: 'run skill',
        streamSnapshot: null,
        payloadVersion: 1,
        deepThink: 0,
        outputStyle: 'docs',
        errorCode: null,
        errorMessage: null,
        createdAt: '2026-08-06T00:00:00Z',
        updatedAt: '2026-08-06T00:00:00Z',
      },
      {
        id: 'a1',
        turnNo: 1,
        role: 'ASSISTANT',
        status: 'COMPLETED',
        requestId: 'req-skill',
        content: 'done',
        streamSnapshot: JSON.stringify(snapshot),
        payloadVersion: 1,
        deepThink: null,
        outputStyle: null,
        errorCode: null,
        errorMessage: null,
        createdAt: '2026-08-06T00:00:00Z',
        updatedAt: '2026-08-06T00:00:00Z',
      },
    ];

    const items = hydrateConversation(messages, 'conv-skill');
    expect(items).toHaveLength(1);
    expect(items[0].response).toBe('done');
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
