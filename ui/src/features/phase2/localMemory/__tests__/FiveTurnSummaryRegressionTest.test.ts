import { describe, expect, it, vi } from 'vitest';
import type { ConversationMessageResponse } from '@/contracts';
import { FakeMemoryIndexStore } from '../FakeMemoryIndexStore';
import { FakePrivateFileSystem } from '../FakePrivateFileSystem';
import { MemoryRepository } from '../memoryRepository';
import { MemoryTaskQueue } from '../memoryTaskQueue';
import { MemoryWorkflow } from '../memoryWorkflow';

const USER = 'user-five-turn';
const CONV = 'conv-five-turn';

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

function turns(count: number): ConversationMessageResponse[] {
  const out: ConversationMessageResponse[] = [];
  for (let i = 1; i <= count; i += 1) {
    out.push(...completedPair(i, `req-${i}`));
  }
  return out;
}

async function setup() {
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
  await repository.writeConversationSummary({
    schemaVersion: 1,
    conversationId: CONV,
    lastSummarizedTurnNo: 0,
    updatedAt: '2026-08-06T00:00:00.000Z',
    sections: {
      当前目标: 'goal',
      已确认事实: 'facts',
      已完成内容: 'done',
      未解决事项: 'open',
    },
  });
  return { store, workflow };
}

describe('FiveTurnSummaryRegressionTest', () => {
  it('does not enqueue analyze or summarize after completed turns', async () => {
    const { store, workflow } = await setup();
    await workflow.observeCompletedMessages(USER, CONV, turns(5));

    const tasks = await store.listTasks(USER);
    expect(tasks).toHaveLength(0);
  });

  it('does not enqueue from the frontend when only 4 turns are completed', async () => {
    const { store, workflow } = await setup();
    await workflow.observeCompletedMessages(USER, CONV, turns(4));

    const tasks = await store.listTasks(USER);
    expect(tasks).toHaveLength(0);
  });
});
