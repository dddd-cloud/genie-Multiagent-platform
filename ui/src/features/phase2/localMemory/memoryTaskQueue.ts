import type { MemoryIndexStore } from './MemoryIndexStore';
import {
  MEMORY_BACKOFF_MS,
  MEMORY_LEASE_MS,
  MEMORY_LIMITS,
  type MemoryTaskRecord,
  type MemoryTaskType,
} from './types';

export type MemoryTaskExecutor = (task: MemoryTaskRecord) => Promise<void>;

export type MemoryTaskQueueOptions = {
  userId: string;
  store: MemoryIndexStore;
  executor: MemoryTaskExecutor;
  isOnline?: () => boolean;
  now?: () => number;
  leaseMs?: number;
  pollIntervalMs?: number;
  onLog?: (info: {
    type: MemoryTaskType;
    conversationId: string;
    requestId: string;
    errorCode?: string;
  }) => void;
};

function backoffMs(attempt: number): number {
  const idx = Math.min(Math.max(attempt - 1, 0), MEMORY_BACKOFF_MS.length - 1);
  return MEMORY_BACKOFF_MS[idx];
}

export class MemoryTaskQueue {
  private readonly userId: string;
  private readonly store: MemoryIndexStore;
  private readonly executor: MemoryTaskExecutor;
  private readonly isOnline: () => boolean;
  private readonly now: () => number;
  private readonly leaseMs: number;
  private readonly pollIntervalMs: number;
  private readonly onLog?: MemoryTaskQueueOptions['onLog'];

  private stopped = true;
  private running = false;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private pausedForUnavailable = false;

  constructor(options: MemoryTaskQueueOptions) {
    this.userId = options.userId;
    this.store = options.store;
    this.executor = options.executor;
    this.isOnline = options.isOnline ?? (() =>
      typeof navigator === 'undefined' ? true : navigator.onLine);
    this.now = options.now ?? (() => Date.now());
    this.leaseMs = options.leaseMs ?? MEMORY_LEASE_MS;
    this.pollIntervalMs = options.pollIntervalMs ?? 1_000;
    this.onLog = options.onLog;
  }

  start(): void {
    if (!this.stopped) {
      return;
    }
    this.stopped = false;
    this.pausedForUnavailable = false;
    void this.pump();
  }

  stop(): void {
    this.stopped = true;
    if (this.timer != null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }

  isStopped(): boolean {
    return this.stopped;
  }

  pauseForUnavailable(): void {
    this.pausedForUnavailable = true;
  }

  resumeFromUnavailable(): void {
    this.pausedForUnavailable = false;
    if (!this.stopped) {
      void this.pump();
    }
  }

  async enqueue(input: {
    conversationId: string;
    requestId: string;
    type: MemoryTaskType;
  }): Promise<MemoryTaskRecord> {
    const existing = await this.store.getTask(
      this.userId,
      input.conversationId,
      input.type,
    );

    if (existing && existing.requestId === input.requestId) {
      return existing;
    }

    const record: MemoryTaskRecord = {
      userId: this.userId,
      conversationId: input.conversationId,
      requestId: input.requestId,
      type: input.type,
      status: 'PENDING',
      retryAt: 0,
      attempt: 0,
    };
    await this.store.putTask(record);
    if (!this.stopped) {
      void this.pump();
    }
    return record;
  }

  async listTasks(): Promise<MemoryTaskRecord[]> {
    return this.store.listTasks(this.userId);
  }

  async retryFailed(conversationId?: string): Promise<void> {
    const tasks = await this.store.listTasks(this.userId);
    for (const task of tasks) {
      if (task.status !== 'FAILED') {
        continue;
      }
      if (conversationId && task.conversationId !== conversationId) {
        continue;
      }
      await this.store.putTask({
        ...task,
        status: 'PENDING',
        retryAt: 0,
        attempt: 0,
      });
    }
    if (!this.stopped) {
      void this.pump();
    }
  }

  async countByStatus(): Promise<Record<string, number>> {
    const tasks = await this.listTasks();
    const counts: Record<string, number> = {
      PENDING: 0,
      RUNNING: 0,
      RETRY: 0,
      DONE: 0,
      FAILED: 0,
    };
    for (const task of tasks) {
      counts[task.status] = (counts[task.status] ?? 0) + 1;
    }
    return counts;
  }

  private schedule(delayMs: number): void {
    if (this.stopped || this.timer != null) {
      return;
    }
    this.timer = setTimeout(() => {
      this.timer = null;
      void this.pump();
    }, delayMs);
  }

  private async pump(): Promise<void> {
    if (this.stopped || this.running || this.pausedForUnavailable) {
      return;
    }
    this.running = true;
    try {
      while (!this.stopped && !this.pausedForUnavailable) {
        if (!this.isOnline()) {
          this.schedule(this.pollIntervalMs);
          return;
        }

        const task = await this.store.claimNextTask(
          this.userId,
          this.now(),
          this.leaseMs,
        );
        if (!task) {
          this.schedule(this.pollIntervalMs);
          return;
        }

        try {
          await this.executor(task);
          if (this.stopped) {
            return;
          }
          await this.store.putTask({
            ...task,
            status: 'DONE',
            retryAt: this.now(),
          });
        } catch (error) {
          if (this.stopped) {
            return;
          }
          await this.handleFailure(task, error);
        }
      }
    } finally {
      this.running = false;
    }
  }

  private async handleFailure(
    task: MemoryTaskRecord,
    error: unknown,
  ): Promise<void> {
    const errorCode =
      error && typeof error === 'object' && 'errorCode' in error
        ? String((error as { errorCode: string }).errorCode)
        : 'MEMORY_FATAL';
    const retryable =
      error && typeof error === 'object' && 'retryable' in error
        ? Boolean((error as { retryable: boolean }).retryable)
        : true;

    this.onLog?.({
      type: task.type,
      conversationId: task.conversationId,
      requestId: task.requestId,
      errorCode,
    });

    if (errorCode === 'OPFS_UNAVAILABLE') {
      await this.store.putTask({
        ...task,
        status: 'PENDING',
        retryAt: this.now(),
      });
      this.pauseForUnavailable();
      return;
    }

    if (!this.isOnline()) {
      await this.store.putTask({
        ...task,
        status: 'PENDING',
        retryAt: this.now(),
      });
      return;
    }

    if (!retryable) {
      await this.store.putTask({
        ...task,
        status: 'FAILED',
        retryAt: this.now(),
      });
      return;
    }

    const attempt = task.attempt + 1;
    if (attempt >= MEMORY_LIMITS.MAX_ATTEMPT) {
      await this.store.putTask({
        ...task,
        attempt,
        status: 'FAILED',
        retryAt: this.now(),
      });
      return;
    }

    await this.store.putTask({
      ...task,
      attempt,
      status: 'RETRY',
      retryAt: this.now() + backoffMs(attempt),
    });
  }
}
