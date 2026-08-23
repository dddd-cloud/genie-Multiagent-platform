import {
  WORKSPACE_LIMITS,
  WorkspaceError,
  createWorkspaceRecord,
  normalizeWorkspaceRemoteFile,
  type WorkspaceFile,
  type WorkspaceFileInput,
  type WorkspaceFileStore,
  type WorkspaceFolder,
  type WorkspaceRemoteFile,
  type WorkspaceScope,
} from '@/platform/workspace/types';
import {
  createWorkspaceRemoteFileId,
} from '@/platform/workspace/scope';
import { FileToolError, fileToolWorkspaceAdapter, type WorkspaceRemoteAdapter } from '@/services/files/fileToolClient';

export interface WorkspaceUploadOutcome {
  readonly file: WorkspaceFile;
  readonly syncError?: string;
}

export interface WorkspaceRemoteImportOutcome {
  readonly file: WorkspaceFile;
  readonly syncError?: string;
}

export interface WorkspaceRemoteImportBatchOutcome {
  readonly imported: WorkspaceFile[];
  readonly failures: ReadonlyArray<{ readonly fileName: string; readonly message: string }>;
}

async function remoteFileId(
  scope: WorkspaceScope,
  remote: WorkspaceRemoteFile,
): Promise<string> {
  return createWorkspaceRemoteFileId(scope, remote.fileName);
}

async function assertRemoteScope(
  _scope: WorkspaceScope,
  remote: WorkspaceRemoteFile,
): Promise<WorkspaceRemoteFile> {
  const normalized = normalizeWorkspaceRemoteFile(remote);
  if (!normalized || normalized.fileSize === undefined) {
    throw new WorkspaceError('INVALID_FILE', '远端文件元数据不完整');
  }
  return {
    requestId: normalized.requestId,
    fileName: normalized.fileName,
    fileSize: normalized.fileSize,
  };
}

function nextUpdatedAt(previous: string): string {
  const previousMs = Date.parse(previous);
  return new Date(Math.max(Date.now(), previousMs + 1)).toISOString();
}

export class WorkspaceService {
  constructor(
    private readonly store: WorkspaceFileStore,
    private readonly remote: WorkspaceRemoteAdapter | null = fileToolWorkspaceAdapter,
  ) {}

  list(scope: WorkspaceScope): Promise<WorkspaceFile[]> {
    return this.store.list(scope);
  }

  read(scope: WorkspaceScope, fileId: string): Promise<ArrayBuffer | null> {
    return this.store.read(scope, fileId);
  }

  async upload(
    scope: WorkspaceScope,
    input: WorkspaceFileInput,
    signal?: AbortSignal,
  ): Promise<WorkspaceUploadOutcome> {
    const localRecord = createWorkspaceRecord(scope, input);
    const metadata = await this.store.put(scope, localRecord);
    if (!this.remote) return { file: metadata };

    try {
      const remote = await this.remote.upload(
        scope,
        new Blob([localRecord.bytes], { type: localRecord.mimeType }),
        localRecord.name,
        signal,
      );
      const scopedRemote = await assertRemoteScope(scope, remote);
      if (
        scopedRemote.fileName !== localRecord.name ||
        (scopedRemote.fileSize !== undefined && scopedRemote.fileSize !== localRecord.size)
      ) {
        throw new WorkspaceError('INVALID_FILE', '远端文件元数据与上传内容不一致');
      }
      const syncedRecord = {
        ...localRecord,
        syncStatus: 'synced' as const,
        remote: scopedRemote,
        updatedAt: nextUpdatedAt(localRecord.updatedAt),
      };
      const synced = await this.store.replaceIfCurrent(
        scope,
        localRecord.updatedAt,
        syncedRecord,
      );
      return { file: await this.currentFileOrFallback(scope, localRecord.id, synced ?? metadata) };
    } catch (error) {
      const failedRecord = {
        ...localRecord,
        syncStatus: 'sync-failed' as const,
        remote: undefined,
        updatedAt: nextUpdatedAt(localRecord.updatedAt),
      };
      const failed = await this.store.replaceIfCurrent(
        scope,
        localRecord.updatedAt,
        failedRecord,
      );
      return {
        file: await this.currentFileOrFallback(scope, localRecord.id, failed ?? metadata),
        syncError: error instanceof Error ? error.message : '远端同步失败',
      };
    }
  }

  async importRemote(
    scope: WorkspaceScope,
    remoteFile: WorkspaceRemoteFile,
    signal?: AbortSignal,
  ): Promise<WorkspaceRemoteImportOutcome> {
    if (!this.remote) {
      throw new FileToolError('未配置远端文件服务');
    }
    const remote = await assertRemoteScope(scope, remoteFile);
    if (remote.fileSize && remote.fileSize > WORKSPACE_LIMITS.MAX_FILE_BYTES) {
      throw new WorkspaceError('FILE_TOO_LARGE', '远端文件超过工作区大小限制');
    }
    const bytes = await this.remote.download(scope, remote, signal);
    const record = createWorkspaceRecord(scope, {
      id: await remoteFileId(scope, remote),
      name: remote.fileName,
      bytes,
      source: 'imported',
      syncStatus: 'synced',
      remote,
    });
    return { file: await this.store.put(scope, record) };
  }

  async importRemoteFiles(
    scope: WorkspaceScope,
    remoteFiles: readonly WorkspaceRemoteFile[],
    signal?: AbortSignal,
  ): Promise<WorkspaceRemoteImportBatchOutcome> {
    const imported: WorkspaceFile[] = [];
    const failures: Array<{ fileName: string; message: string }> = [];
    for (const remoteFile of remoteFiles) {
      if (signal?.aborted) break;
      try {
        imported.push((await this.importRemote(scope, remoteFile, signal)).file);
      } catch (error) {
        failures.push({
          fileName: remoteFile.fileName,
          message: error instanceof Error ? error.message : '导入远端文件失败',
        });
      }
    }
    return { imported, failures };
  }

  async listRemote(scope: WorkspaceScope, signal?: AbortSignal): Promise<WorkspaceRemoteFile[]> {
    if (!this.remote) return [];
    const remoteFiles = await this.remote.list(scope, signal);
    const scopedFiles: WorkspaceRemoteFile[] = [];
    for (const file of remoteFiles) {
      try {
        scopedFiles.push(await assertRemoteScope(scope, file));
      } catch {
        // Ignore a malformed/foreign remote row without failing the local workspace.
      }
    }
    return scopedFiles;
  }

  private async currentFileOrFallback(
    scope: WorkspaceScope,
    fileId: string,
    fallback: WorkspaceFile,
  ): Promise<WorkspaceFile> {
    return (await this.store.list(scope)).find((file) => file.id === fileId) ?? fallback;
  }

  remove(scope: WorkspaceScope, fileId: string): Promise<void> {
    return this.store.remove(scope, fileId);
  }

  rename(scope: WorkspaceScope, fileId: string, name: string): Promise<WorkspaceFile> {
    return this.store.rename(scope, fileId, name);
  }

  async write(
    scope: WorkspaceScope,
    input: WorkspaceFileInput,
  ): Promise<WorkspaceFile> {
    const record = createWorkspaceRecord(scope, {
      ...input,
      syncStatus: 'local',
      remote: undefined,
    });
    return this.store.put(scope, record);
  }

  async upsertByName(
    scope: WorkspaceScope,
    input: WorkspaceFileInput,
  ): Promise<WorkspaceFile> {
    const parentId = input.parentId ?? null;
    const existing = (await this.store.list(scope)).find(
      (file) => file.name === input.name && file.parentId === parentId,
    );
    return this.write(scope, { ...input, id: existing?.id ?? input.id });
  }

  moveFile(scope: WorkspaceScope, fileId: string, parentId: string | null): Promise<WorkspaceFile> {
    return this.store.moveFile(scope, fileId, parentId);
  }

  listFolders(scope: WorkspaceScope): Promise<WorkspaceFolder[]> {
    return this.store.listFolders(scope);
  }

  createFolder(scope: WorkspaceScope, name: string, parentId: string | null): Promise<WorkspaceFolder> {
    return this.store.createFolder(scope, name, parentId);
  }

  renameFolder(scope: WorkspaceScope, folderId: string, name: string): Promise<WorkspaceFolder> {
    return this.store.renameFolder(scope, folderId, name);
  }

  moveFolder(scope: WorkspaceScope, folderId: string, parentId: string | null): Promise<WorkspaceFolder> {
    return this.store.moveFolder(scope, folderId, parentId);
  }

  deleteFolder(scope: WorkspaceScope, folderId: string): Promise<void> {
    return this.store.deleteFolder(scope, folderId);
  }
}
