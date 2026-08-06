import type { MemoryIndexStore } from './MemoryIndexStore';
import type { MemoryIndexRecord, MemoryTaskRecord, MemoryTaskType } from './types';

function indexKey(userId: string, path: string): string {
  return `${userId}\0${path}`;
}

function taskKey(
  userId: string,
  conversationId: string,
  type: MemoryTaskType,
): string {
  return `${userId}\0${conversationId}\0${type}`;
}

export class FakeMemoryIndexStore implements MemoryIndexStore {
  private readonly index = new Map<string, MemoryIndexRecord>();
  private readonly tasks = new Map<string, MemoryTaskRecord>();

  async getIndex(userId: string, path: string): Promise<MemoryIndexRecord | null> {
    return this.index.get(indexKey(userId, path)) ?? null;
  }

  async putIndex(record: MemoryIndexRecord): Promise<void> {
    this.index.set(indexKey(record.userId, record.path), { ...record });
  }

  async listIndex(userId: string): Promise<MemoryIndexRecord[]> {
    return [...this.index.values()]
      .filter((item) => item.userId === userId)
      .map((item) => ({ ...item }));
  }

  async deleteIndex(userId: string, path: string): Promise<void> {
    this.index.delete(indexKey(userId, path));
  }

  async getTask(
    userId: string,
    conversationId: string,
    type: MemoryTaskType,
  ): Promise<MemoryTaskRecord | null> {
    return this.tasks.get(taskKey(userId, conversationId, type)) ?? null;
  }

  async putTask(record: MemoryTaskRecord): Promise<void> {
    this.tasks.set(
      taskKey(record.userId, record.conversationId, record.type),
      { ...record },
    );
  }

  async listTasks(userId: string): Promise<MemoryTaskRecord[]> {
    return [...this.tasks.values()]
      .filter((item) => item.userId === userId)
      .map((item) => ({ ...item }));
  }

  async deleteTask(
    userId: string,
    conversationId: string,
    type: MemoryTaskType,
  ): Promise<void> {
    this.tasks.delete(taskKey(userId, conversationId, type));
  }

  async claimNextTask(
    userId: string,
    now: number,
    leaseMs: number,
  ): Promise<MemoryTaskRecord | null> {
    const candidates = [...this.tasks.values()]
      .filter((task) => task.userId === userId)
      .filter((task) => {
        if (task.status === 'PENDING' || task.status === 'RETRY') {
          return task.retryAt <= now;
        }
        if (task.status === 'RUNNING') {
          return task.retryAt <= now;
        }
        return false;
      })
      .sort((a, b) => a.retryAt - b.retryAt);

    const next = candidates[0];
    if (!next) {
      return null;
    }

    const claimed: MemoryTaskRecord = {
      ...next,
      status: 'RUNNING',
      retryAt: now + leaseMs,
    };
    this.tasks.set(
      taskKey(claimed.userId, claimed.conversationId, claimed.type),
      claimed,
    );
    return { ...claimed };
  }
}
