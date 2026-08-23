import {
  WORKSPACE_LIMITS,
  WorkspaceError,
  assertFolderName,
  assertWorkspaceFileId,
  createWorkspaceFolderRecord,
  normalizeWorkspaceFileRecord,
  normalizeWorkspaceFolder,
  previewKind,
  type WorkspaceFile,
  type WorkspaceFileRecord,
  type WorkspaceFileStore,
  type WorkspaceFolder,
  type WorkspaceScope,
} from './types';
import { assertWorkspaceScope } from './scope';

function withoutBytes(record: WorkspaceFileRecord): WorkspaceFile {
  const { bytes: _bytes, ...metadata } = record;
  return metadata;
}

export class MemoryWorkspaceFileStore implements WorkspaceFileStore {
  private readonly records = new Map<string, WorkspaceFileRecord>();
  private readonly folders = new Map<string, WorkspaceFolder>();

  async list(scope: WorkspaceScope): Promise<WorkspaceFile[]> {
    const currentScope = assertWorkspaceScope(scope);
    return [...this.records.values()]
      .flatMap((record) => {
        try {
          return [normalizeWorkspaceFileRecord(record, currentScope.key)];
        } catch {
          return [];
        }
      })
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
      .map(withoutBytes);
  }

  async read(scope: WorkspaceScope, fileId: string): Promise<ArrayBuffer | null> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(fileId);
    const record = this.records.get(id);
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
    const existingRaw = this.records.get(next.id);
    let existing: WorkspaceFileRecord | undefined;
    if (existingRaw) {
      try {
        existing = normalizeWorkspaceFileRecord(existingRaw);
      } catch {
        throw new WorkspaceError('FILE_ID_COLLISION', '文件标识已被损坏记录占用');
      }
      if (existing.scopeKey !== currentScope.key) {
        throw new WorkspaceError('FILE_ID_COLLISION', '文件标识已被其他工作区占用');
      }
    }
    const current = [...this.records.values()].flatMap((item) => {
      try {
        return [normalizeWorkspaceFileRecord(item, currentScope.key)];
      } catch {
        return [];
      }
    });
    if (current.some((item) => item.id !== next.id && item.parentId === next.parentId && item.name === next.name)) {
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '当前目录下已存在同名文件');
    }
    if ([...this.folders.values()].some(
      (folder) => folder.scopeKey === currentScope.key && folder.parentId === next.parentId && folder.name === next.name,
    )) {
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '当前目录下已存在同名文件夹');
    }
    if (!existing && current.length >= WORKSPACE_LIMITS.MAX_FILES) {
      throw new WorkspaceError('FILE_COUNT_LIMIT', '工作区文件数量已达到上限');
    }
    const total = current.reduce((sum, item) => sum + item.size, 0);
    if (total - (existing?.size ?? 0) + next.size > WORKSPACE_LIMITS.MAX_TOTAL_BYTES) {
      throw new WorkspaceError('WORKSPACE_SIZE_LIMIT', '工作区总容量已达到上限');
    }
    this.records.set(next.id, next);
    return withoutBytes(next);
  }

  async replaceIfCurrent(
    scope: WorkspaceScope,
    expectedUpdatedAt: string,
    record: WorkspaceFileRecord,
  ): Promise<WorkspaceFile | null> {
    const currentScope = assertWorkspaceScope(scope);
    const next = normalizeWorkspaceFileRecord(record, currentScope.key);
    const existingRaw = this.records.get(next.id);
    if (!existingRaw) return null;
    let existing: WorkspaceFileRecord;
    try {
      existing = normalizeWorkspaceFileRecord(existingRaw, currentScope.key);
    } catch {
      return null;
    }
    if (existing.updatedAt !== expectedUpdatedAt) return null;
    return this.put(currentScope, next);
  }

  async remove(scope: WorkspaceScope, fileId: string): Promise<void> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(fileId);
    const record = this.records.get(id);
    if (record?.scopeKey === currentScope.key) this.records.delete(id);
  }

  async rename(scope: WorkspaceScope, fileId: string, name: string): Promise<WorkspaceFile> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(fileId);
    const record = this.records.get(id);
    let current: WorkspaceFileRecord;
    try {
      current = record
        ? normalizeWorkspaceFileRecord(record, currentScope.key)
        : (() => {
            throw new WorkspaceError('FILE_NOT_FOUND', '文件不存在');
          })();
    } catch (error) {
      if (error instanceof WorkspaceError && error.code === 'SCOPE_MISMATCH') {
        throw new WorkspaceError('FILE_NOT_FOUND', '文件不存在');
      }
      throw error;
    }
    const updated: WorkspaceFileRecord = {
      ...current,
      name: name,
      kind: previewKind(name, current.mimeType),
      updatedAt: new Date().toISOString(),
    };
    return this.put(currentScope, updated);
  }

  async moveFile(scope: WorkspaceScope, fileId: string, parentId: string | null): Promise<WorkspaceFile> {
    const currentScope = assertWorkspaceScope(scope);
    const id = assertWorkspaceFileId(fileId);
    const record = this.records.get(id);
    if (!record) {
      throw new WorkspaceError('FILE_NOT_FOUND', '文件不存在');
    }
    const current = normalizeWorkspaceFileRecord(record, currentScope.key);
    if (parentId !== null) this.requireFolder(currentScope.key, parentId);
    return this.put(currentScope, { ...current, parentId, updatedAt: new Date().toISOString() });
  }

  async listFolders(scope: WorkspaceScope): Promise<WorkspaceFolder[]> {
    const currentScope = assertWorkspaceScope(scope);
    return [...this.folders.values()]
      .filter((folder) => folder.scopeKey === currentScope.key)
      .sort((left, right) => left.name.localeCompare(right.name));
  }

  async createFolder(scope: WorkspaceScope, name: string, parentId: string | null): Promise<WorkspaceFolder> {
    const currentScope = assertWorkspaceScope(scope);
    if (parentId !== null) this.requireFolder(currentScope.key, parentId);
    const folder = createWorkspaceFolderRecord(currentScope, name, parentId);
    this.assertSiblingNameFree(currentScope.key, parentId, folder.name, null);
    this.folders.set(folder.id, folder);
    return folder;
  }

  async renameFolder(scope: WorkspaceScope, folderId: string, name: string): Promise<WorkspaceFolder> {
    const currentScope = assertWorkspaceScope(scope);
    const current = this.requireFolder(currentScope.key, folderId);
    const nextName = assertFolderName(name);
    this.assertSiblingNameFree(currentScope.key, current.parentId, nextName, folderId);
    const updated: WorkspaceFolder = { ...current, name: nextName, updatedAt: new Date().toISOString() };
    this.folders.set(updated.id, updated);
    return updated;
  }

  async moveFolder(scope: WorkspaceScope, folderId: string, parentId: string | null): Promise<WorkspaceFolder> {
    const currentScope = assertWorkspaceScope(scope);
    const current = this.requireFolder(currentScope.key, folderId);
    if (parentId === folderId) {
      throw new WorkspaceError('INVALID_FOLDER', '文件夹不能移动到自身内部');
    }
    if (parentId !== null) {
      this.requireFolder(currentScope.key, parentId);
      if (this.isDescendant(parentId, folderId)) {
        throw new WorkspaceError('INVALID_FOLDER', '文件夹不能移动到自己的子文件夹中');
      }
    }
    this.assertSiblingNameFree(currentScope.key, parentId, current.name, folderId);
    const updated: WorkspaceFolder = { ...current, parentId, updatedAt: new Date().toISOString() };
    this.folders.set(updated.id, updated);
    return updated;
  }

  async deleteFolder(scope: WorkspaceScope, folderId: string): Promise<void> {
    const currentScope = assertWorkspaceScope(scope);
    const doomed = new Set<string>([folderId]);
    let grew = true;
    while (grew) {
      grew = false;
      for (const folder of this.folders.values()) {
        if (folder.parentId && doomed.has(folder.parentId) && !doomed.has(folder.id)) {
          doomed.add(folder.id);
          grew = true;
        }
      }
    }
    for (const folder of [...this.folders.values()]) {
      if (folder.scopeKey === currentScope.key && doomed.has(folder.id)) this.folders.delete(folder.id);
    }
    for (const file of [...this.records.values()]) {
      if (file.scopeKey === currentScope.key && file.parentId && doomed.has(file.parentId)) {
        this.records.delete(file.id);
      }
    }
  }

  private requireFolder(scopeKey: string, folderId: string): WorkspaceFolder {
    const folder = this.folders.get(folderId);
    if (!folder || folder.scopeKey !== scopeKey) {
      throw new WorkspaceError('FOLDER_NOT_FOUND', '文件夹不存在');
    }
    return normalizeWorkspaceFolder(folder, scopeKey);
  }

  private assertSiblingNameFree(
    scopeKey: string,
    parentId: string | null,
    name: string,
    excludeFolderId: string | null,
  ): void {
    const folderCollision = [...this.folders.values()].some(
      (folder) =>
        folder.scopeKey === scopeKey &&
        folder.parentId === parentId &&
        folder.id !== excludeFolderId &&
        folder.name === name,
    );
    const fileCollision = [...this.records.values()].some((record) => {
      try {
        const file = normalizeWorkspaceFileRecord(record, scopeKey);
        return file.parentId === parentId && file.name === name;
      } catch {
        return false;
      }
    });
    if (folderCollision || fileCollision) {
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '当前目录下已存在同名文件或文件夹');
    }
  }

  private isDescendant(candidateId: string, ancestorId: string): boolean {
    let current = this.folders.get(candidateId) ?? null;
    const seen = new Set<string>();
    while (current && current.parentId) {
      if (current.parentId === ancestorId) return true;
      if (seen.has(current.parentId)) return false;
      seen.add(current.parentId);
      current = this.folders.get(current.parentId) ?? null;
    }
    return false;
  }
}
