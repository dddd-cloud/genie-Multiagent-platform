import {
  WORKSPACE_LIMITS,
  WorkspaceError,
  assertWorkspaceFileId,
  normalizeWorkspaceFileRecord,
  type WorkspaceFile,
  type WorkspaceFileRecord,
  type WorkspaceFileStore,
  type WorkspaceScope,
} from './types';
import { assertWorkspaceScope } from './scope';

const DATABASE_NAME = 'joyagent-workspace';
const DATABASE_VERSION = 2;
const FILE_STORE = 'files';
const SCOPE_INDEX = 'scopeKey';

type StoredFile = WorkspaceFileRecord;

function idbAvailable(): boolean {
  return typeof indexedDB !== 'undefined';
}

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'));
  });
}

function transactionDone(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onabort = () => reject(transaction.error ?? new Error('IndexedDB transaction aborted'));
    transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'));
  });
}

function withoutBytes(record: StoredFile): WorkspaceFile {
  const { bytes: _bytes, ...metadata } = record;
  return metadata;
}

export class IndexedDbWorkspaceFileStore implements WorkspaceFileStore {
  private readonly database: Promise<IDBDatabase>;

  constructor() {
    if (!idbAvailable()) {
      throw new WorkspaceError('STORAGE_UNAVAILABLE', '当前浏览器不支持持久化工作区');
    }
    this.database = this.open();
  }

  static isAvailable(): boolean {
    return idbAvailable();
  }

  async list(scope: WorkspaceScope): Promise<WorkspaceFile[]> {
    const currentScope = assertWorkspaceScope(scope);
    const db = await this.database;
    const transaction = db.transaction(FILE_STORE, 'readonly');
    const done = transactionDone(transaction);
    const index = transaction.objectStore(FILE_STORE).index(SCOPE_INDEX);
    const rawRecords = await requestResult(index.getAll(currentScope.key));
    await done;
    return (rawRecords as unknown[])
      .flatMap((raw) => {
        try {
          return [normalizeWorkspaceFileRecord(raw, currentScope.key)];
        } catch {
          // A stale/corrupt browser record must not hide healthy files.
          return [];
        }
      })
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
      .map(withoutBytes);
  }

  async read(scope: WorkspaceScope, fileId: string): Promise<ArrayBuffer | null> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(fileId);
    const record = await this.getRecord(id);
    if (!record) return null;
    try {
      return normalizeWorkspaceFileRecord(record, currentScope.key).bytes;
    } catch {
      return null;
    }
  }

  async put(scope: WorkspaceScope, record: WorkspaceFileRecord): Promise<WorkspaceFile> {
    const currentScope = assertWorkspaceScope(scope);
    const next = normalizeWorkspaceFileRecord(record, currentScope.key);
    const db = await this.database;
    const transaction = db.transaction(FILE_STORE, 'readwrite');
    const done = transactionDone(transaction);
    const store = transaction.objectStore(FILE_STORE);
    const existingRaw = await requestResult(store.get(next.id));
    const existing = existingRaw
      ? this.normalizeExisting(existingRaw, currentScope.key)
      : undefined;
    const currentRecords = (await requestResult(
      store.index(SCOPE_INDEX).getAll(currentScope.key),
    )) as unknown[];
    const current = currentRecords.flatMap((raw) => {
      try {
        return [normalizeWorkspaceFileRecord(raw, currentScope.key)];
      } catch {
        return [];
      }
    });
    if (current.some((item) => item.id !== next.id && item.name === next.name)) {
      await done;
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '工作区内已存在同名文件');
    }
    if (!existing && current.length >= WORKSPACE_LIMITS.MAX_FILES) {
      await done;
      throw new WorkspaceError('FILE_COUNT_LIMIT', '工作区文件数量已达到上限');
    }
    const totalBytes = current.reduce((total, item) => total + item.size, 0);
    const nextTotal = totalBytes - (existing?.size ?? 0) + next.size;
    if (nextTotal > WORKSPACE_LIMITS.MAX_TOTAL_BYTES) {
      await done;
      throw new WorkspaceError('WORKSPACE_SIZE_LIMIT', '工作区总容量已达到上限');
    }
    store.put(next);
    await done;
    return withoutBytes(next);
  }

  async replaceIfCurrent(
    scope: WorkspaceScope,
    expectedUpdatedAt: string,
    record: WorkspaceFileRecord,
  ): Promise<WorkspaceFile | null> {
    const currentScope = assertWorkspaceScope(scope);
    const next = normalizeWorkspaceFileRecord(record, currentScope.key);
    const db = await this.database;
    const transaction = db.transaction(FILE_STORE, 'readwrite');
    const done = transactionDone(transaction);
    const store = transaction.objectStore(FILE_STORE);
    const existingRaw = await requestResult(store.get(next.id));
    if (!existingRaw) {
      await done;
      return null;
    }
    let existing: StoredFile;
    try {
      existing = this.normalizeExisting(existingRaw, currentScope.key);
    } catch {
      await done;
      return null;
    }
    if (existing.updatedAt !== expectedUpdatedAt) {
      await done;
      return null;
    }
    const currentRecords = (await requestResult(
      store.index(SCOPE_INDEX).getAll(currentScope.key),
    )) as unknown[];
    const current = currentRecords.flatMap((raw) => {
      try {
        return [normalizeWorkspaceFileRecord(raw, currentScope.key)];
      } catch {
        return [];
      }
    });
    if (current.some((item) => item.id !== next.id && item.name === next.name)) {
      await done;
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '工作区内已存在同名文件');
    }
    const totalBytes = current.reduce((total, item) => total + item.size, 0);
    if (totalBytes - existing.size + next.size > WORKSPACE_LIMITS.MAX_TOTAL_BYTES) {
      await done;
      throw new WorkspaceError('WORKSPACE_SIZE_LIMIT', '工作区总容量已达到上限');
    }
    store.put(next);
    await done;
    return withoutBytes(next);
  }

  async remove(scope: WorkspaceScope, fileId: string): Promise<void> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(fileId);
    const db = await this.database;
    const transaction = db.transaction(FILE_STORE, 'readwrite');
    const done = transactionDone(transaction);
    const store = transaction.objectStore(FILE_STORE);
    const record = await requestResult(store.get(id));
    if (
      record &&
      typeof record === 'object' &&
      (record as { scopeKey?: unknown }).scopeKey === currentScope.key
    ) {
      store.delete(id);
    }
    await done;
  }

  async clearUser(userId: string): Promise<void> {
    const prefix = `${encodeURIComponent(userId)}:`;
    const db = await this.database;
    const transaction = db.transaction(FILE_STORE, 'readwrite');
    const done = transactionDone(transaction);
    const store = transaction.objectStore(FILE_STORE);
    const records = (await requestResult(store.getAll())) as Array<{
      id?: unknown;
      scopeKey?: unknown;
    }>;
    for (const record of records) {
      if (
        typeof record.id === 'string' &&
        typeof record.scopeKey === 'string' &&
        record.scopeKey.startsWith(prefix)
      ) {
        store.delete(record.id);
      }
    }
    await done;
  }

  async rename(scope: WorkspaceScope, fileId: string, name: string): Promise<WorkspaceFile> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(fileId);
    const record = await this.getRecord(id);
    if (!record) {
      throw new WorkspaceError('FILE_NOT_FOUND', '文件不存在');
    }
    let current: WorkspaceFileRecord;
    try {
      current = normalizeWorkspaceFileRecord(record, currentScope.key);
    } catch (error) {
      if (error instanceof WorkspaceError && error.code === 'SCOPE_MISMATCH') {
        throw new WorkspaceError('FILE_NOT_FOUND', '文件不存在');
      }
      throw error;
    }
    const updated: WorkspaceFileRecord = {
      ...current,
      name,
      updatedAt: new Date().toISOString(),
    };
    // Reuse put so rename receives the same name, metadata, and quota checks.
    return this.put(currentScope, updated);
  }

  private async getRecord(fileId: string): Promise<unknown> {
    const db = await this.database;
    const transaction = db.transaction(FILE_STORE, 'readonly');
    const done = transactionDone(transaction);
    const record = await requestResult(transaction.objectStore(FILE_STORE).get(fileId));
    await done;
    return record;
  }

  private normalizeExisting(raw: unknown, scopeKey: string): StoredFile {
    if (!raw || typeof raw !== 'object') {
      throw new WorkspaceError('FILE_ID_COLLISION', '文件标识已被损坏记录占用');
    }
    const rawScopeKey = (raw as { scopeKey?: unknown }).scopeKey;
    if (rawScopeKey !== scopeKey) {
      throw new WorkspaceError('FILE_ID_COLLISION', '文件标识已被其他工作区占用');
    }
    try {
      return normalizeWorkspaceFileRecord(raw, scopeKey);
    } catch {
      throw new WorkspaceError('FILE_ID_COLLISION', '文件标识已被损坏记录占用');
    }
  }

  private open(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
      request.onupgradeneeded = () => {
        const database = request.result;
        const store = database.objectStoreNames.contains(FILE_STORE)
          ? request.transaction?.objectStore(FILE_STORE)
          : database.createObjectStore(FILE_STORE, { keyPath: 'id' });
        if (store && !store.indexNames.contains(SCOPE_INDEX)) {
          store.createIndex(SCOPE_INDEX, 'scopeKey', { unique: false });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(
        new WorkspaceError('STORAGE_UNAVAILABLE', '工作区存储初始化失败'),
      );
    });
  }
}

export function createIndexedDbWorkspaceFileStore(): WorkspaceFileStore {
  return new IndexedDbWorkspaceFileStore();
}

export async function clearWorkspaceForUser(userId: string): Promise<void> {
  const normalized = userId.trim();
  if (!normalized || !IndexedDbWorkspaceFileStore.isAvailable()) return;
  try {
    const store = new IndexedDbWorkspaceFileStore();
    await store.clearUser(normalized);
  } catch {
    // Logout must still complete if IndexedDB is unavailable or quota-locked.
  }
}
