import { describe, expect, it, vi } from 'vitest';
import { FakeMemoryIndexStore } from '../FakeMemoryIndexStore';
import { MemoryTaskQueue } from '../memoryTaskQueue';

describe('MemoryTaskQueueAccountSwitchTest', () => {
  it('stops executing after account switch stop()', async () => {
    const store = new FakeMemoryIndexStore();
    let executions = 0;
    const executor = vi.fn(async () => {
      executions += 1;
      await new Promise((resolve) => setTimeout(resolve, 30));
    });

    const queueA = new MemoryTaskQueue({
      userId: 'user-a',
      store,
      executor,
      pollIntervalMs: 20,
      now: () => Date.now(),
      isOnline: () => true,
    });

    await queueA.enqueue({
      conversationId: 'c1',
      requestId: 'r1',
      type: 'ANALYZE_TURN',
    });
    queueA.start();
    await new Promise((resolve) => setTimeout(resolve, 10));
    queueA.stop();

    const queueB = new MemoryTaskQueue({
      userId: 'user-b',
      store,
      executor: vi.fn(async () => {
        executions += 1;
      }),
      pollIntervalMs: 20,
      isOnline: () => true,
    });
    queueB.start();
    await queueB.enqueue({
      conversationId: 'c2',
      requestId: 'r2',
      type: 'ANALYZE_TURN',
    });

    await new Promise((resolve) => setTimeout(resolve, 80));
    queueB.stop();

    const tasksA = await store.listTasks('user-a');
    const tasksB = await store.listTasks('user-b');
    expect(queueA.isStopped()).toBe(true);
    expect(tasksA.every((task) => task.userId === 'user-a')).toBe(true);
    expect(tasksB.every((task) => task.userId === 'user-b')).toBe(true);
    // user-a queue must not keep pumping after stop
    const aExecBefore = executions;
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(executions).toBe(aExecBefore);
  });

  it('does not re-enqueue same requestId and resets on different requestId', async () => {
    const store = new FakeMemoryIndexStore();
    const queue = new MemoryTaskQueue({
      userId: 'user-a',
      store,
      executor: async () => undefined,
      isOnline: () => true,
    });

    const first = await queue.enqueue({
      conversationId: 'c1',
      requestId: 'r1',
      type: 'ANALYZE_TURN',
    });
    const same = await queue.enqueue({
      conversationId: 'c1',
      requestId: 'r1',
      type: 'ANALYZE_TURN',
    });
    expect(same.requestId).toBe(first.requestId);

    await store.putTask({
      ...first,
      status: 'DONE',
      attempt: 1
    });
    const next = await queue.enqueue({
      conversationId: 'c1',
      requestId: 'r2',
      type: 'ANALYZE_TURN',
    });
    expect(next.requestId).toBe('r2');
    expect(next.status).toBe('PENDING');
    expect(next.attempt).toBe(0);
  });
});
