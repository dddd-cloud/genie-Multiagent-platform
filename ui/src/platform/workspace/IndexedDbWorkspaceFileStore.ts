import {
  WORKSPACE_LIMITS,
  WorkspaceError,
  assertFolderName,
  assertWorkspaceFileId,
  createWorkspaceFolderRecord,
  normalizeWorkspaceFileRecord,
  normalizeWorkspaceFolder,
  type WorkspaceFile,
  type WorkspaceFileRecord,
  type WorkspaceFileStore,
  type WorkspaceFolder,
  type WorkspaceScope,
} from './types';
import { assertWorkspaceScope } from './scope';

const DATABASE_NAME = 'joyagent-workspace';
const DATABASE_VERSION = 3;
const FILE_STORE = 'files';
const FOLDER_STORE = 'folders';
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
    await this.migrateConversationScopedFiles(currentScope);
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

  /**
   * Older builds appended conversationId to scopeKey. Move conflict-free files
   * into the stable user/workspace partition without deleting ambiguous rows.
   */
  private async migrateConversationScopedFiles(scope: WorkspaceScope): Promise<void> {
    const db = await this.database;
    const transaction = db.transaction(FILE_STORE, 'readwrite');
    const done = transactionDone(transaction);
    const store = transaction.objectStore(FILE_STORE);
    const rawRecords = (await requestResult(store.getAll())) as unknown[];
    const currentNames = new Set<string>();
    for (const raw of rawRecords) {
      if (!raw || typeof raw !== 'object') continue;
      const rawScope = (raw as { scopeKey?: unknown }).scopeKey;
      const rawName = (raw as { name?: unknown }).name;
      if (rawScope === scope.key && typeof rawName === 'string') currentNames.add(rawName);
    }
    const legacyPrefix = `${scope.key}:`;
    for (const raw of rawRecords) {
      if (!raw || typeof raw !== 'object') continue;
      const rawScope = (raw as { scopeKey?: unknown }).scopeKey;
      if (typeof rawScope !== 'string' || !rawScope.startsWith(legacyPrefix)) continue;
      try {
        const record = normalizeWorkspaceFileRecord(raw, rawScope);
        if (currentNames.has(record.name)) continue;
        store.put({ ...record, scopeKey: scope.key });
        currentNames.add(record.name);
      } catch {
        // Leave malformed or conflicting legacy data untouched.
      }
    }
    await done;
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
    const transaction = db.transaction([FILE_STORE, FOLDER_STORE], 'readwrite');
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
    if (current.some((item) => item.id !== next.id && item.parentId === next.parentId && item.name === next.name)) {
      await done;
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '当前目录下已存在同名文件');
    }
    const siblingFolders = await this.readAllFolders(transaction, currentScope.key);
    if (siblingFolders.some((folder) => folder.parentId === next.parentId && folder.name === next.name)) {
      await done;
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '当前目录下已存在同名文件夹');
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
    if (current.some((item) => item.id !== next.id && item.parentId === next.parentId && item.name === next.name)) {
      await done;
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '当前目录下已存在同名文件');
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
        const folderStore = database.objectStoreNames.contains(FOLDER_STORE)
          ? request.transaction?.objectStore(FOLDER_STORE)
          : database.createObjectStore(FOLDER_STORE, { keyPath: 'id' });
        if (folderStore && !folderStore.indexNames.contains(SCOPE_INDEX)) {
          folderStore.createIndex(SCOPE_INDEX, 'scopeKey', { unique: false });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(
        new WorkspaceError('STORAGE_UNAVAILABLE', '工作区存储初始化失败'),
      );
    });
  }

  async listFolders(scope: WorkspaceScope): Promise<WorkspaceFolder[]> {
    const currentScope = assertWorkspaceScope(scope);
    const db = await this.database;
    const transaction = db.transaction(FOLDER_STORE, 'readonly');
    const done = transactionDone(transaction);
    const index = transaction.objectStore(FOLDER_STORE).index(SCOPE_INDEX);
    const rawRecords = await requestResult(index.getAll(currentScope.key));
    await done;
    return (rawRecords as unknown[])
      .flatMap((raw) => {
        try {
          return [normalizeWorkspaceFolder(raw, currentScope.key)];
        } catch {
          return [];
        }
      })
      .sort((left, right) => left.name.localeCompare(right.name));
  }

  async createFolder(
    scope: WorkspaceScope,
    name: string,
    parentId: string | null,
  ): Promise<WorkspaceFolder> {
    const currentScope = assertWorkspaceScope(scope);
    const folder = createWorkspaceFolderRecord(currentScope, name, parentId);
    const db = await this.database;
    const transaction = db.transaction([FOLDER_STORE, FILE_STORE], 'readwrite');
    const done = transactionDone(transaction);
    if (parentId !== null) {
      await this.assertFolderExists(transaction, currentScope.key, parentId);
    }
    await this.assertSiblingNameFree(transaction, currentScope.key, parentId, folder.name, null);
    transaction.objectStore(FOLDER_STORE).put(folder);
    await done;
    return folder;
  }

  async renameFolder(scope: WorkspaceScope, folderId: string, name: string): Promise<WorkspaceFolder> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(folderId);
    const nextName = assertFolderName(name);
    const db = await this.database;
    const transaction = db.transaction([FOLDER_STORE, FILE_STORE], 'readwrite');
    const done = transactionDone(transaction);
    const folderStore = transaction.objectStore(FOLDER_STORE);
    const raw = await requestResult(folderStore.get(id));
    const current = this.normalizeExistingFolder(raw, currentScope.key);
    await this.assertSiblingNameFree(transaction, currentScope.key, current.parentId, nextName, id);
    const updated: WorkspaceFolder = { ...current, name: nextName, updatedAt: new Date().toISOString() };
    folderStore.put(updated);
    await done;
    return updated;
  }

  async moveFolder(
    scope: WorkspaceScope,
    folderId: string,
    parentId: string | null,
  ): Promise<WorkspaceFolder> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(folderId);
    const db = await this.database;
    const transaction = db.transaction([FOLDER_STORE, FILE_STORE], 'readwrite');
    const done = transactionDone(transaction);
    const folderStore = transaction.objectStore(FOLDER_STORE);
    const raw = await requestResult(folderStore.get(id));
    const current = this.normalizeExistingFolder(raw, currentScope.key);
    if (parentId === id) {
      await done;
      throw new WorkspaceError('INVALID_FOLDER', '文件夹不能移动到自身内部');
    }
    if (parentId !== null) {
      await this.assertFolderExists(transaction, currentScope.key, parentId);
      const allFolders = await this.readAllFolders(transaction, currentScope.key);
      if (isDescendant(allFolders, parentId, id)) {
        await done;
        throw new WorkspaceError('INVALID_FOLDER', '文件夹不能移动到自己的子文件夹中');
      }
    }
    await this.assertSiblingNameFree(transaction, currentScope.key, parentId, current.name, id);
    const updated: WorkspaceFolder = { ...current, parentId, updatedAt: new Date().toISOString() };
    folderStore.put(updated);
    await done;
    return updated;
  }

  async deleteFolder(scope: WorkspaceScope, folderId: string): Promise<void> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(folderId);
    const db = await this.database;
    const transaction = db.transaction([FOLDER_STORE, FILE_STORE], 'readwrite');
    const done = transactionDone(transaction);
    const allFolders = await this.readAllFolders(transaction, currentScope.key);
    const allFiles = await this.readAllFiles(transaction, currentScope.key);
    const doomedFolderIds = new Set<string>([id]);
    let grew = true;
    while (grew) {
      grew = false;
      for (const folder of allFolders) {
        if (folder.parentId && doomedFolderIds.has(folder.parentId) && !doomedFolderIds.has(folder.id)) {
          doomedFolderIds.add(folder.id);
          grew = true;
        }
      }
    }
    const folderStore = transaction.objectStore(FOLDER_STORE);
    const fileStore = transaction.objectStore(FILE_STORE);
    for (const folder of allFolders) {
      if (doomedFolderIds.has(folder.id)) folderStore.delete(folder.id);
    }
    for (const file of allFiles) {
      if (file.parentId && doomedFolderIds.has(file.parentId)) fileStore.delete(file.id);
    }
    await done;
  }

  async moveFile(scope: WorkspaceScope, fileId: string, parentId: string | null): Promise<WorkspaceFile> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(fileId);
    const record = await this.getRecord(id);
    if (!record) {
      throw new WorkspaceError('FILE_NOT_FOUND', '文件不存在');
    }
    const current = this.normalizeExisting(record, currentScope.key);
    const db = await this.database;
    const transaction = db.transaction([FOLDER_STORE, FILE_STORE], 'readwrite');
    const done = transactionDone(transaction);
    if (parentId !== null) {
      await this.assertFolderExists(transaction, currentScope.key, parentId);
    }
    await this.assertSiblingNameFree(transaction, currentScope.key, parentId, current.name, null, id);
    await done;
    return this.put(currentScope, { ...current, parentId, updatedAt: new Date().toISOString() });
  }

  private async readAllFolders(transaction: IDBTransaction, scopeKey: string): Promise<WorkspaceFolder[]> {
    const raw = (await requestResult(
      transaction.objectStore(FOLDER_STORE).index(SCOPE_INDEX).getAll(scopeKey),
    )) as unknown[];
    return raw.flatMap((item) => {
      try {
        return [normalizeWorkspaceFolder(item, scopeKey)];
      } catch {
        return [];
      }
    });
  }

  private async readAllFiles(transaction: IDBTransaction, scopeKey: string): Promise<WorkspaceFileRecord[]> {
    const raw = (await requestResult(
      transaction.objectStore(FILE_STORE).index(SCOPE_INDEX).getAll(scopeKey),
    )) as unknown[];
    return raw.flatMap((item) => {
      try {
        return [normalizeWorkspaceFileRecord(item, scopeKey)];
      } catch {
        return [];
      }
    });
  }

  private async assertFolderExists(
    transaction: IDBTransaction,
    scopeKey: string,
    folderId: string,
  ): Promise<void> {
    const raw = await requestResult(transaction.objectStore(FOLDER_STORE).get(folderId));
    this.normalizeExistingFolder(raw, scopeKey);
  }

  /** Files and folders share one name-space within a directory, like a real filesystem. */
  private async assertSiblingNameFree(
    transaction: IDBTransaction,
    scopeKey: string,
    parentId: string | null,
    name: string,
    excludeFolderId: string | null,
    excludeFileId?: string,
  ): Promise<void> {
    const [folders, files] = await Promise.all([
      this.readAllFolders(transaction, scopeKey),
      this.readAllFiles(transaction, scopeKey),
    ]);
    const folderCollision = folders.some(
      (folder) => folder.parentId === parentId && folder.id !== excludeFolderId && folder.name === name,
    );
    const fileCollision = files.some(
      (file) => file.parentId === parentId && file.id !== excludeFileId && file.name === name,
    );
    if (folderCollision || fileCollision) {
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '当前目录下已存在同名文件或文件夹');
    }
  }

  private normalizeExistingFolder(raw: unknown, scopeKey: string): WorkspaceFolder {
    if (!raw) {
      throw new WorkspaceError('FOLDER_NOT_FOUND', '文件夹不存在');
    }
    try {
      return normalizeWorkspaceFolder(raw, scopeKey);
    } catch (error) {
      if (error instanceof WorkspaceError && error.code === 'SCOPE_MISMATCH') {
        throw new WorkspaceError('FOLDER_NOT_FOUND', '文件夹不存在');
      }
      throw error;
    }
  }
}

function isDescendant(
  folders: readonly WorkspaceFolder[],
  candidateId: string,
  ancestorId: string,
): boolean {
  const byId = new Map(folders.map((folder) => [folder.id, folder] as const));
  let current = byId.get(candidateId) ?? null;
  const seen = new Set<string>();
  while (current && current.parentId) {
    if (current.parentId === ancestorId) return true;
    if (seen.has(current.parentId)) return false;
    seen.add(current.parentId);
    current = byId.get(current.parentId) ?? null;
  }
  return false;
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
