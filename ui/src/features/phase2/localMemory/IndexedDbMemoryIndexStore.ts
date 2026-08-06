import type { MemoryIndexStore } from './MemoryIndexStore';
import type { MemoryIndexRecord, MemoryTaskRecord, MemoryTaskType } from './types';

const DB_NAME = 'joyagent_phase2_memory';
const DB_VERSION = 1;
const INDEX_STORE = 'memory_index';
const TASK_STORE = 'memory_task_queue';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onerror = () => reject(request.error ?? new Error('IndexedDB open failed'));
    request.onsuccess = () => resolve(request.result);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(INDEX_STORE)) {
        db.createObjectStore(INDEX_STORE, { keyPath: ['userId', 'path'] });
      }
      if (!db.objectStoreNames.contains(TASK_STORE)) {
        db.createObjectStore(TASK_STORE, {keyPath: ['userId', 'conversationId', 'type'],});
      }
    };
  });
}

function reqToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'));
  });
}

function txDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error('IndexedDB transaction failed'));
    tx.onabort = () => reject(tx.error ?? new Error('IndexedDB transaction aborted'));
  });
}

export class IndexedDbMemoryIndexStore implements MemoryIndexStore {
  private dbPromise: Promise<IDBDatabase> | null = null;

  private db(): Promise<IDBDatabase> {
    if (!this.dbPromise) {
      this.dbPromise = openDb();
    }
    return this.dbPromise;
  }

  async getIndex(userId: string, path: string): Promise<MemoryIndexRecord | null> {
    const db = await this.db();
    const tx = db.transaction(INDEX_STORE, 'readonly');
    const store = tx.objectStore(INDEX_STORE);
    const result = await reqToPromise(
      store.get([userId, path]) as IDBRequest<MemoryIndexRecord | undefined>,
    );
    await txDone(tx);
    return result ?? null;
  }

  async putIndex(record: MemoryIndexRecord): Promise<void> {
    const db = await this.db();
    const tx = db.transaction(INDEX_STORE, 'readwrite');
    tx.objectStore(INDEX_STORE).put(record);
    await txDone(tx);
  }

  async listIndex(userId: string): Promise<MemoryIndexRecord[]> {
    const db = await this.db();
    const tx = db.transaction(INDEX_STORE, 'readonly');
    const all = await reqToPromise(
      tx.objectStore(INDEX_STORE).getAll() as IDBRequest<MemoryIndexRecord[]>,
    );
    await txDone(tx);
    return (all ?? []).filter((item) => item.userId === userId);
  }

  async deleteIndex(userId: string, path: string): Promise<void> {
    const db = await this.db();
    const tx = db.transaction(INDEX_STORE, 'readwrite');
    tx.objectStore(INDEX_STORE).delete([userId, path]);
    await txDone(tx);
  }

  async getTask(
    userId: string,
    conversationId: string,
    type: MemoryTaskType,
  ): Promise<MemoryTaskRecord | null> {
    const db = await this.db();
    const tx = db.transaction(TASK_STORE, 'readonly');
    const result = await reqToPromise(
      tx.objectStore(TASK_STORE).get([userId, conversationId, type]) as IDBRequest<
        MemoryTaskRecord | undefined
      >,
    );
    await txDone(tx);
    return result ?? null;
  }

  async putTask(record: MemoryTaskRecord): Promise<void> {
    const db = await this.db();
    const tx = db.transaction(TASK_STORE, 'readwrite');
    tx.objectStore(TASK_STORE).put(record);
    await txDone(tx);
  }

  async listTasks(userId: string): Promise<MemoryTaskRecord[]> {
    const db = await this.db();
    const tx = db.transaction(TASK_STORE, 'readonly');
    const all = await reqToPromise(
      tx.objectStore(TASK_STORE).getAll() as IDBRequest<MemoryTaskRecord[]>,
    );
    await txDone(tx);
    return (all ?? []).filter((item) => item.userId === userId);
  }

  async deleteTask(
    userId: string,
    conversationId: string,
    type: MemoryTaskType,
  ): Promise<void> {
    const db = await this.db();
    const tx = db.transaction(TASK_STORE, 'readwrite');
    tx.objectStore(TASK_STORE).delete([userId, conversationId, type]);
    await txDone(tx);
  }

  async claimNextTask(
    userId: string,
    now: number,
    leaseMs: number,
  ): Promise<MemoryTaskRecord | null> {
    const db = await this.db();
    const tx = db.transaction(TASK_STORE, 'readwrite');
    const store = tx.objectStore(TASK_STORE);
    const all = await reqToPromise(
      store.getAll() as IDBRequest<MemoryTaskRecord[]>,
    );
    const candidates = (all ?? [])
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
      await txDone(tx);
      return null;
    }

    if (next.status === 'RUNNING') {
      next.status = 'RETRY';
    }

    const claimed: MemoryTaskRecord = {
      ...next,
      status: 'RUNNING',
      retryAt: now + leaseMs,
    };
    store.put(claimed);
    await txDone(tx);
    return claimed;
  }
}
