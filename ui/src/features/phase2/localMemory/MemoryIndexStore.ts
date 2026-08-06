import type { MemoryIndexRecord, MemoryTaskRecord, MemoryTaskType } from './types';

export interface MemoryIndexStore {
  getIndex(userId: string, path: string): Promise<MemoryIndexRecord | null>;
  putIndex(record: MemoryIndexRecord): Promise<void>;
  listIndex(userId: string): Promise<MemoryIndexRecord[]>;
  deleteIndex(userId: string, path: string): Promise<void>;

  getTask(
    userId: string,
    conversationId: string,
    type: MemoryTaskType,
  ): Promise<MemoryTaskRecord | null>;
  putTask(record: MemoryTaskRecord): Promise<void>;
  listTasks(userId: string): Promise<MemoryTaskRecord[]>;
  deleteTask(
    userId: string,
    conversationId: string,
    type: MemoryTaskType,
  ): Promise<void>;

  /**
   * Atomically claim the next runnable task for a user.
   * PENDING/RETRY with retryAt <= now, or stale RUNNING (lease expired).
   */
  claimNextTask(
    userId: string,
    now: number,
    leaseMs: number,
  ): Promise<MemoryTaskRecord | null>;
}
