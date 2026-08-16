import { describe, expect, it, vi } from 'vitest';
import type { ConversationMessageResponse } from '@/contracts';
import { FakeMemoryIndexStore } from '../FakeMemoryIndexStore';
import { FakePrivateFileSystem } from '../FakePrivateFileSystem';
import { MemoryRepository } from '../memoryRepository';
import { MemoryTaskQueue } from '../memoryTaskQueue';
import { MemoryWorkflow } from '../memoryWorkflow';
import { MEMORY_LIMITS, codePointLength } from '../types';

const USER = 'user-budget';
const CONV = 'conv-budget';

function completedPair(
  turnNo: number,
  requestId: string,
): ConversationMessageResponse[] {
  const ts = '2026-08-06T00:00:00.000Z';
  return [
    {
      id: `u-${requestId}`,
      turnNo,
      role: 'USER',
      status: 'COMPLETED',
      requestId,
      content: `user-${turnNo}`,
      streamSnapshot: null,
      payloadVersion: 1,
      deepThink: null,
      outputStyle: null,
      errorCode: null,
      errorMessage: null,
      createdAt: ts,
      updatedAt: ts,
    },
    {
      id: `a-${requestId}`,
      turnNo,
      role: 'ASSISTANT',
      status: 'COMPLETED',
      requestId,
      content: `assistant-${turnNo}`,
      streamSnapshot: null,
      payloadVersion: 1,
      deepThink: null,
      outputStyle: null,
      errorCode: null,
      errorMessage: null,
      createdAt: ts,
      updatedAt: ts,
    },
  ];
}

describe('BudgetEarlySummaryRegressionTest', () => {
  it('does not enqueue SUMMARIZE from the frontend when over budget', async () => {
    const fs = new FakePrivateFileSystem();
    const store = new FakeMemoryIndexStore();
    const repository = new MemoryRepository(USER, fs, store);
    const queue = new MemoryTaskQueue({
      userId: USER,
      store,
      executor: vi.fn(async () => undefined),
      isOnline: () => true,
    });
    const workflow = new MemoryWorkflow({
      userId: USER,
      repository,
      queue,
      getAuthUserId: () => USER,
      fetchMessages: async () => [],
    });

    const lastTurn = 3;
    const sectionPad = 's'.repeat(4_900);
    await repository.writeConversationSummary({
      schemaVersion: 1,
      conversationId: CONV,
      lastSummarizedTurnNo: lastTurn,
      updatedAt: '2026-08-06T00:00:00.000Z',
      sections: {
        当前目标: sectionPad,
        已确认事实: sectionPad,
        已完成内容: sectionPad,
        未解决事项: sectionPad,
      },
    });
    await repository.writeLongTermMemory({
      schemaVersion: 1,
      updatedAt: '2026-08-06T00:00:00.000Z',
      sections: {
        基本信息: [{ key: 'bulk', value: 'L'.repeat(11_000) }],
        回答偏好: [],
        长期目标: [],
        长期约束: [],
      },
    });

    const ltm = await repository.readLongTermMemory();
    const summary = await repository.readConversationSummary(CONV);
    expect(ltm.status).toBe('READY');
    expect(summary.status).toBe('READY');
    if (ltm.status !== 'READY' || summary.status !== 'READY') return;

    const combined =
      codePointLength(ltm.raw) + codePointLength(summary.raw);
    expect(combined).toBeGreaterThanOrEqual(
      MEMORY_LIMITS.LOCAL_CONTEXT_WARN_CODEPOINTS,
    );
    expect(summary.doc.lastSummarizedTurnNo).toBe(lastTurn);

    const messages: ConversationMessageResponse[] = [
      ...completedPair(1, 'req-1'),
      ...completedPair(2, 'req-2'),
      ...completedPair(lastTurn, 'req-3'),
    ];
    // maxTurn === lastSummarizedTurnNo → 5-turn delta is 0
    await workflow.observeCompletedMessages(USER, CONV, messages);

    const tasks = await store.listTasks(USER);
    expect(tasks).toHaveLength(0);
  });
});
