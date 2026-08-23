import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { buildWorkspaceScope } from '@/platform/workspace/scope';
import { IndexedDbWorkspaceFileStore } from '@/platform/workspace/IndexedDbWorkspaceFileStore';
import type {
  WorkspaceBinaryFile,
  WorkspaceFile,
  WorkspaceFileStore,
  WorkspaceFolder,
} from '@/platform/workspace/types';
import { WorkspaceService } from '@/services/workspace/workspaceService';

export interface WorkspaceFilesState {
  readonly status: 'loading' | 'ready' | 'unavailable' | 'error';
  readonly files: readonly WorkspaceFile[];
  readonly folders: readonly WorkspaceFolder[];
  readonly error: string | null;
  readonly refresh: () => Promise<void>;
  readonly readFile: (fileId: string) => Promise<ArrayBuffer | null>;
  readonly uploadFiles: (items: File[], parentId: string | null) => Promise<string[]>;
  readonly saveRuntimeFiles: (files: readonly WorkspaceBinaryFile[]) => Promise<void>;
  readonly removeFile: (fileId: string) => Promise<void>;
  readonly renameFile: (fileId: string, name: string) => Promise<void>;
  readonly moveFile: (fileId: string, parentId: string | null) => Promise<void>;
  readonly createFolder: (name: string, parentId: string | null) => Promise<void>;
  readonly renameFolder: (folderId: string, name: string) => Promise<void>;
  readonly moveFolder: (folderId: string, parentId: string | null) => Promise<void>;
  readonly deleteFolder: (folderId: string) => Promise<void>;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

let sharedStore: WorkspaceFileStore | null | undefined;

/** One IndexedDB connection is enough for every workspace row rendered on the page. */
function getSharedStore(): WorkspaceFileStore | null {
  if (sharedStore === undefined) {
    try {
      sharedStore = new IndexedDbWorkspaceFileStore();
    } catch {
      sharedStore = null;
    }
  }
  return sharedStore;
}

/**
 * Read/write access to one workspace's files and folders, independent of which
 * workspace is bound to the active chat. Used by the workspace page's file
 * tree so every workspace can be browsed, not only the one currently chatting.
 */
export function useWorkspaceFiles(userId: string, workspaceId: string): WorkspaceFilesState {
  const scope = useMemo(() => buildWorkspaceScope(userId, workspaceId), [userId, workspaceId]);
  const service = useMemo(() => {
    const store = getSharedStore();
    return store ? new WorkspaceService(store, null) : null;
  }, []);
  const [status, setStatus] = useState<WorkspaceFilesState['status']>('loading');
  const [files, setFiles] = useState<WorkspaceFile[]>([]);
  const [folders, setFolders] = useState<WorkspaceFolder[]>([]);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);

  const refresh = useCallback(async () => {
    const current = ++generation.current;
    if (!service) {
      setStatus('unavailable');
      setError('当前浏览器不支持持久化工作区');
      return;
    }
    setStatus((previous) => (previous === 'ready' ? previous : 'loading'));
    try {
      const [nextFiles, nextFolders] = await Promise.all([
        service.list(scope),
        service.listFolders(scope),
      ]);
      if (generation.current !== current) return;
      setFiles(nextFiles);
      setFolders(nextFolders);
      setStatus('ready');
      setError(null);
    } catch (failure) {
      if (generation.current !== current) return;
      setStatus('error');
      setError(errorMessage(failure, '工作区文件读取失败'));
    }
  }, [scope, service]);

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope.key]);

  const readFile = useCallback(
    (fileId: string) => (service ? service.read(scope, fileId) : Promise.resolve(null)),
    [scope, service],
  );

  const uploadFiles = useCallback(
    async (items: File[], parentId: string | null): Promise<string[]> => {
      if (!service) return items.map(() => '当前浏览器不支持持久化工作区');
      const failures: string[] = [];
      for (const item of items) {
        try {
          const bytes = await item.arrayBuffer();
          await service.upload(scope, {
            name: item.name,
            mimeType: item.type,
            bytes,
            source: 'user',
            parentId,
          });
        } catch (failure) {
          failures.push(`${item.name}：${errorMessage(failure, '上传失败')}`);
        }
      }
      await refresh();
      return failures;
    },
    [refresh, scope, service],
  );

  const saveRuntimeFiles = useCallback(
    async (items: readonly WorkspaceBinaryFile[]) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      for (const item of items) {
        await service.upsertByName(scope, { ...item, source: 'assistant' });
      }
      await refresh();
    },
    [refresh, scope, service],
  );

  const removeFile = useCallback(
    async (fileId: string) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      await service.remove(scope, fileId);
      await refresh();
    },
    [refresh, scope, service],
  );

  const renameFile = useCallback(
    async (fileId: string, name: string) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      await service.rename(scope, fileId, name);
      await refresh();
    },
    [refresh, scope, service],
  );

  const moveFile = useCallback(
    async (fileId: string, parentId: string | null) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      await service.moveFile(scope, fileId, parentId);
      await refresh();
    },
    [refresh, scope, service],
  );

  const createFolder = useCallback(
    async (name: string, parentId: string | null) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      await service.createFolder(scope, name, parentId);
      await refresh();
    },
    [refresh, scope, service],
  );

  const renameFolder = useCallback(
    async (folderId: string, name: string) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      await service.renameFolder(scope, folderId, name);
      await refresh();
    },
    [refresh, scope, service],
  );

  const moveFolder = useCallback(
    async (folderId: string, parentId: string | null) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      await service.moveFolder(scope, folderId, parentId);
      await refresh();
    },
    [refresh, scope, service],
  );

  const deleteFolder = useCallback(
    async (folderId: string) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      await service.deleteFolder(scope, folderId);
      await refresh();
    },
    [refresh, scope, service],
  );

  return {
    status,
    files,
    folders,
    error,
    refresh,
    readFile,
    uploadFiles,
    saveRuntimeFiles,
    removeFile,
    renameFile,
    moveFile,
    createFolder,
    renameFolder,
    moveFolder,
    deleteFolder,
  };
}
