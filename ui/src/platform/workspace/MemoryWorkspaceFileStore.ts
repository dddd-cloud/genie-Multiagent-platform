import {
  WORKSPACE_LIMITS,
  WorkspaceError,
  assertWorkspaceFileId,
  normalizeWorkspaceFileRecord,
  previewKind,
  type WorkspaceFile,
  type WorkspaceFileRecord,
  type WorkspaceFileStore,
  type WorkspaceScope,
} from './types';
import { assertWorkspaceScope } from './scope';

function withoutBytes(record: WorkspaceFileRecord): WorkspaceFile {
  const { bytes: _bytes, ...metadata } = record;
  return metadata;
}

export class MemoryWorkspaceFileStore implements WorkspaceFileStore {
  private readonly records = new Map<string, WorkspaceFileRecord>();

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
    if (current.some((item) => item.id !== next.id && item.name === next.name)) {
      throw new WorkspaceError('DUPLICATE_FILE_NAME', '工作区内已存在同名文件');
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
}
