import { describe, expect, it, vi } from 'vitest';
import { FakeMemoryIndexStore } from '../FakeMemoryIndexStore';
import { FakePrivateFileSystem } from '../FakePrivateFileSystem';
import { MemoryRepository } from '../memoryRepository';
import { MemoryTaskQueue } from '../memoryTaskQueue';
import { MemoryWorkflow } from '../memoryWorkflow';
import { MemoryError } from '../types';

describe('OpfsUnavailableTest', () => {
  it('reports UNAVAILABLE and does not write markdown to IndexedDB', async () => {
    const fs = new FakePrivateFileSystem({ available: false });
    const store = new FakeMemoryIndexStore();
    const repository = new MemoryRepository('user-a', fs, store);

    expect(await repository.getOpfsStatus()).toBe('UNAVAILABLE');
    const read = await repository.readLongTermMemory();
    expect(read.status).toBe('UNAVAILABLE');

    await expect(
      repository.writeLongTermMemory({
        schemaVersion: 1,
        updatedAt: '2026-08-06T00:00:00.000Z',
        sections: {
          基本信息: [{
            key: 'a',
            value: 'b'
          }],
          回答偏好: [],
          长期目标: [],
          长期约束: [],
        },
      }),
    ).rejects.toBeInstanceOf(MemoryError);

    expect(await store.listIndex('user-a')).toHaveLength(0);
  });

  it('pauses queue when OPFS is unavailable and does not loop retry', async () => {
    const fs = new FakePrivateFileSystem({ available: false });
    const store = new FakeMemoryIndexStore();
    const repository = new MemoryRepository('user-a', fs, store);
    const executor = vi.fn(async () => {
      throw new MemoryError('OPFS_UNAVAILABLE', 'OPFS unavailable', false);
    });

    const queue = new MemoryTaskQueue({
      userId: 'user-a',
      store,
      executor,
      pollIntervalMs: 10,
      isOnline: () => true,
    });

    const workflow = new MemoryWorkflow({
      userId: 'user-a',
      repository,
      queue,
      getAuthUserId: () => 'user-a',
      fetchMessages: async () => [],
    });
    void workflow;

    await queue.enqueue({
      conversationId: 'c1',
      requestId: 'r1',
      type: 'ANALYZE_TURN',
    });
    queue.start();
    await new Promise((resolve) => setTimeout(resolve, 40));
    queue.stop();

    expect(executor.mock.calls.length).toBeLessThanOrEqual(1);
    const tasks = await store.listTasks('user-a');
    expect(tasks[0]?.status).toBe('PENDING');
    expect(tasks[0]?.attempt).toBe(0);

    await workflow.observeCompletedMessages('user-a', 'c1', [
      {
        id: 'm1',
        turnNo: 1,
        role: 'USER',
        status: 'COMPLETED',
        requestId: 'r2',
        content: 'hi',
        streamSnapshot: null,
        payloadVersion: 1,
        deepThink: null,
        outputStyle: null,
        errorCode: null,
        errorMessage: null,
        createdAt: '2026-08-06T00:00:00.000Z',
        updatedAt: '2026-08-06T00:00:00.000Z',
      },
      {
        id: 'm2',
        turnNo: 1,
        role: 'ASSISTANT',
        status: 'COMPLETED',
        requestId: 'r2',
        content: 'hello',
        streamSnapshot: null,
        payloadVersion: 1,
        deepThink: null,
        outputStyle: null,
        errorCode: null,
        errorMessage: null,
        createdAt: '2026-08-06T00:00:00.000Z',
        updatedAt: '2026-08-06T00:00:00.000Z',
      },
    ]);
    // Still no body stored in index store
    expect(JSON.stringify(await store.listIndex('user-a'))).not.toContain('hello');
  });
});
