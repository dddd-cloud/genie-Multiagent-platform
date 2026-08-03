import { describe, expect, it } from 'vitest';
import type { ConversationMessageResponse } from '@/contracts';
import { hydrateConversation } from '../hydrateConversation';
import reactSuccess from '../../../../../docs/mvp-contract/fixtures/snapshot/react-success.json';
import planSuccess from '../../../../../docs/mvp-contract/fixtures/snapshot/plan-success.json';
import failedSnapshot from '../../../../../docs/mvp-contract/fixtures/snapshot/failed.json';
import interruptedSnapshot from '../../../../../docs/mvp-contract/fixtures/snapshot/interrupted.json';
import truncatedSnapshot from '../../../../../docs/mvp-contract/fixtures/snapshot/truncated.json';
import invalidVersion from '../../../../../docs/mvp-contract/fixtures/snapshot/invalid-version.json';
import malformedJson from '../../../../../docs/mvp-contract/fixtures/snapshot/malformed-json.txt?raw';

const CONV_ID = 'conv-test-001';

function baseMessage(
  overrides: Partial<ConversationMessageResponse> &
    Pick<ConversationMessageResponse, 'id' | 'role' | 'status' | 'requestId'>,
): ConversationMessageResponse {
  return {
    turnNo: 1,
    content: null,
    streamSnapshot: null,
    payloadVersion: 1,
    deepThink: null,
    outputStyle: null,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-21T00:00:00Z',
    updatedAt: '2026-07-21T00:00:00Z',
    ...overrides,
  };
}

function pair(opts: {
  requestId: string;
  query: string;
  assistantStatus: ConversationMessageResponse['status'];
  streamSnapshot: string | null;
  content?: string | null;
  deepThink?: number | null;
  errorCode?: string | null;
  errorMessage?: string | null;
}): ConversationMessageResponse[] {
  return [
    baseMessage({
      id: `user-${opts.requestId}`,
      role: 'USER',
      status: 'COMPLETED',
      requestId: opts.requestId,
      content: opts.query,
      deepThink: opts.deepThink ?? 0,
      outputStyle: 'docs',
    }),
    baseMessage({
      id: `asst-${opts.requestId}`,
      role: 'ASSISTANT',
      status: opts.assistantStatus,
      requestId: opts.requestId,
      content: opts.content ?? null,
      streamSnapshot: opts.streamSnapshot,
      errorCode: opts.errorCode ?? null,
      errorMessage: opts.errorMessage ?? null,
    }),
  ];
}

describe('hydrateConversation', () => {
  it('replays react-success snapshot into response', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-react-001',
        query: '帮我分析这个项目',
        assistantStatus: 'COMPLETED',
        streamSnapshot: JSON.stringify(reactSuccess),
        content: 'fallback content',
      }),
      CONV_ID,
    );
    expect(items).toHaveLength(1);
    expect(items[0].loading).toBe(false);
    expect(items[0].sessionId).toBe(CONV_ID);
    expect(items[0].tip).toBe('');
    expect(items[0].response).toContain('ReAct');
    expect(items[0].deepThink).toBe(false);
  });

  it('replays plan-success and preserves deepThink from user turn', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-plan-001',
        query: '制定分析计划',
        assistantStatus: 'COMPLETED',
        streamSnapshot: JSON.stringify(planSuccess),
        content: 'Plan fallback',
        deepThink: 1,
      }),
      CONV_ID,
    );
    expect(items).toHaveLength(1);
    expect(items[0].loading).toBe(false);
    expect(items[0].deepThink).toBe(true);
    expect(items[0].response).toBeTruthy();
  });

  it('FAILED terminal state is not permanently loading', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-fail-001',
        query: '执行失败任务',
        assistantStatus: 'FAILED',
        streamSnapshot: JSON.stringify(failedSnapshot),
        errorCode: 'AGENT_DOWNSTREAM_ERROR',
        errorMessage: 'Agent downstream service unavailable',
      }),
      CONV_ID,
    );
    expect(items[0].loading).toBe(false);
    expect(items[0].persistedStatus).toBe('FAILED');
    expect(items[0].errorCode).toBe('AGENT_DOWNSTREAM_ERROR');
  });

  it('INTERRUPTED terminal state is not permanently loading', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-interrupt-001',
        query: '长时间任务',
        assistantStatus: 'INTERRUPTED',
        streamSnapshot: JSON.stringify(interruptedSnapshot),
        errorCode: 'CLIENT_DISCONNECTED',
        errorMessage: 'Client disconnected during streaming',
      }),
      CONV_ID,
    );
    expect(items[0].loading).toBe(false);
    expect(items[0].persistedStatus).toBe('INTERRUPTED');
    expect(items[0].errorMessage).toBeTruthy();
  });

  it('marks truncated snapshots', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-trunc-001',
        query: '截断任务',
        assistantStatus: 'COMPLETED',
        streamSnapshot: JSON.stringify(truncatedSnapshot),
        content: 'content fallback',
      }),
      CONV_ID,
    );
    expect(items[0].loading).toBe(false);
    expect(items[0].snapshotTruncated).toBe(true);
    expect(items[0].response).toContain('最终回答');
  });

  it('falls back to content for malformed-json snapshot', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-malformed',
        query: '坏 JSON',
        assistantStatus: 'COMPLETED',
        streamSnapshot: malformedJson,
        content: 'content from DB',
      }),
      CONV_ID,
    );
    expect(items[0].loading).toBe(false);
    expect(items[0].response).toBe('content from DB');
  });

  it('falls back to content for invalid-version snapshot', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-invalid-ver',
        query: '错误版本',
        assistantStatus: 'COMPLETED',
        streamSnapshot: JSON.stringify(invalidVersion),
        content: 'version fallback',
      }),
      CONV_ID,
    );
    expect(items[0].loading).toBe(false);
    expect(items[0].response).toBe('version fallback');
  });

  it('falls back to content when message payloadVersion is not 1', () => {
    const messages = pair({
      requestId: 'req-msg-ver',
      query: '消息版本错误',
      assistantStatus: 'COMPLETED',
      streamSnapshot: JSON.stringify(reactSuccess),
      content: 'message version fallback',
    }).map((m) =>
      m.role === 'ASSISTANT' ? {
        ...m,
        payloadVersion: 2
      } : m,
    );
    const items = hydrateConversation(messages, CONV_ID);
    expect(items[0].loading).toBe(false);
    expect(items[0].response).toBe('message version fallback');
  });

  it('falls back to content when streamSnapshot is null', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-null-snap',
        query: '空快照',
        assistantStatus: 'COMPLETED',
        streamSnapshot: null,
        content: 'null snapshot content',
      }),
      CONV_ID,
    );
    expect(items[0].loading).toBe(false);
    expect(items[0].response).toBe('null snapshot content');
  });

  it('applies deepThink per turn independently', () => {
    const messages: ConversationMessageResponse[] = [
      ...pair({
        requestId: 'req-1',
        query: 'turn1',
        assistantStatus: 'COMPLETED',
        streamSnapshot: null,
        content: 'a1',
        deepThink: 0,
      }),
      ...pair({
        requestId: 'req-2',
        query: 'turn2',
        assistantStatus: 'COMPLETED',
        streamSnapshot: null,
        content: 'a2',
        deepThink: 1,
      }).map((m) => ({
        ...m,
        turnNo: 2
      })),
    ];
    const items = hydrateConversation(messages, CONV_ID);
    expect(items).toHaveLength(2);
    expect(items[0].deepThink).toBe(false);
    expect(items[1].deepThink).toBe(true);
  });

  it('COMPLETED is not permanently loading', () => {
    const items = hydrateConversation(
      pair({
        requestId: 'req-done',
        query: 'done',
        assistantStatus: 'COMPLETED',
        streamSnapshot: null,
        content: 'done',
      }),
      CONV_ID,
    );
    expect(items[0].loading).toBe(false);
  });
});
