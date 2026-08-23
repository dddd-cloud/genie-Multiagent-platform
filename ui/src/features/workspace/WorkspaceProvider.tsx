import {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { buildWorkspaceScope } from '@/platform/workspace/scope';
import {
  IndexedDbWorkspaceFileStore,
} from '@/platform/workspace/IndexedDbWorkspaceFileStore';
import {
  WORKSPACE_LIMITS,
  type WorkspaceBinaryFile,
  type WorkspaceFileStore,
  type WorkspaceFolder,
  type WorkspaceRemoteFile,
} from '@/platform/workspace/types';
import type { UserWorkspace } from '@/platform/workspace/catalog';
import {
  WorkspaceService,
} from '@/services/workspace/workspaceService';
import {
  WorkspaceContext,
  type WorkspaceContextValue,
  type WorkspaceOperationFailure,
  type WorkspaceStatus,
} from './useWorkspace';
import type { WorkspaceRemoteAdapter } from '@/services/files/fileToolClient';
import { bindWorkspaceExecutionContext } from './executionBind';

export interface WorkspaceProviderProps {
  readonly userId: string;
  readonly workspaceId: string;
  readonly conversationId?: string;
  readonly children: ReactNode;
  readonly store?: WorkspaceFileStore;
  readonly remoteAdapter?: WorkspaceRemoteAdapter | null;
  readonly workspaces: readonly UserWorkspace[];
  readonly activeWorkspace: UserWorkspace;
  readonly selectWorkspace: (workspaceId: string) => void;
  readonly createWorkspace: (name: string) => void;
  readonly renameWorkspace: (name: string) => void;
  readonly deleteWorkspace: () => void;
}

function mimeForTextFile(name: string): string {
  const lower = name.toLowerCase();
  if (lower.endsWith('.py')) return 'text/x-python';
  if (lower.endsWith('.json')) return 'application/json';
  if (/\.(md|markdown|mdown)$/.test(lower)) return 'text/markdown';
  return 'text/plain';
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

function remoteKey(file: WorkspaceRemoteFile): string {
  return `${file.requestId ?? ''}:${file.fileName}`;
}

export const WorkspaceProvider = memo(function WorkspaceProvider({
  userId,
  workspaceId,
  conversationId,
  children,
  store: injectedStore,
  remoteAdapter,
  workspaces,
  activeWorkspace,
  selectWorkspace,
  createWorkspace,
  renameWorkspace,
  deleteWorkspace: onDeleteWorkspace,
}: WorkspaceProviderProps) {
  const scope = useMemo(
    () => buildWorkspaceScope(userId, workspaceId, conversationId),
    [userId, workspaceId, conversationId],
  );
  const defaultStore = useMemo(() => {
    if (injectedStore) return injectedStore;
    try {
      return new IndexedDbWorkspaceFileStore();
    } catch {
      return null;
    }
  }, [injectedStore]);
  const service = useMemo(
    // Browser workspaces are deliberately independent from chat uploads.
    () => (defaultStore ? new WorkspaceService(defaultStore, remoteAdapter ?? null) : null),
    [defaultStore, remoteAdapter],
  );
  const activeScopeKey = useRef(scope.key);
  const refreshGeneration = useRef(0);
  activeScopeKey.current = scope.key;
  const [status, setStatus] = useState<WorkspaceStatus>('loading');
  const [files, setFiles] = useState<WorkspaceContextValue['files']>([]);
  const [folders, setFolders] = useState<WorkspaceFolder[]>([]);
  const [remoteFiles, setRemoteFiles] = useState<WorkspaceRemoteFile[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [remoteError, setRemoteError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const generation = refreshGeneration.current + 1;
    refreshGeneration.current = generation;
    const scopeKey = scope.key;
    const isCurrent = () =>
      refreshGeneration.current === generation && activeScopeKey.current === scopeKey;
    if (!service) {
      if (isCurrent()) {
        setStatus('unavailable');
        setError('当前浏览器不支持持久化工作区');
      }
      return;
    }
    if (isCurrent()) {
      setStatus('loading');
      setError(null);
      setRemoteError(null);
    }
    const [localResult, folderResult] = await Promise.allSettled([
      service.list(scope),
      service.listFolders(scope),
    ]);
    if (!isCurrent()) return;
    if (localResult.status === 'rejected') {
      setStatus('error');
      setError(errorMessage(localResult.reason, '工作区文件读取失败'));
      return;
    }
    setFiles(localResult.value);
    setFolders(folderResult.status === 'fulfilled' ? folderResult.value : []);
    setRemoteFiles([]);
    setStatus('ready');
  }, [scope, service]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const uploadFiles = useCallback(
    async (items: File[], parentId: string | null = null): Promise<WorkspaceOperationFailure[]> => {
      if (!service) {
        return [{ name: 'workspace', message: '当前浏览器不支持持久化工作区' }];
      }
      const failures: WorkspaceOperationFailure[] = [];
      for (const item of items) {
        if (item.size > WORKSPACE_LIMITS.MAX_FILE_BYTES) {
          failures.push({
            name: item.name,
            message: '单个文件超过工作区大小限制',
          });
          continue;
        }
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
          failures.push({
            name: item.name,
            message: errorMessage(failure, '文件上传失败'),
          });
        }
      }
      await refresh();
      return failures;
    },
    [refresh, scope, service],
  );

  const importRemoteFile = useCallback(
    async (file: WorkspaceRemoteFile) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      await service.importRemote(scope, file);
      await refresh();
    },
    [refresh, scope, service],
  );

  const importRemoteFiles = useCallback(
    async (
      items: readonly WorkspaceRemoteFile[],
    ): Promise<WorkspaceOperationFailure[]> => {
      if (!service) {
        return [{ name: 'workspace', message: '当前浏览器不支持持久化工作区' }];
      }
      const result = await service.importRemoteFiles(scope, items);
      await refresh();
      return result.failures.map((failure) => ({
        name: failure.fileName,
        message: failure.message,
      }));
    },
    [refresh, scope, service],
  );

  const readFile = useCallback(
    (fileId: string) => {
      if (!service) return Promise.resolve(null);
      return service.read(scope, fileId);
    },
    [scope, service],
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

  const writeTextFile = useCallback(
    async (fileId: string | undefined, name: string, content: string, parentId: string | null = null) => {
      if (!service) throw new Error('当前浏览器不支持持久化工作区');
      const file = await service.write(scope, {
        id: fileId,
        name,
        mimeType: mimeForTextFile(name),
        bytes: new TextEncoder().encode(content).buffer as ArrayBuffer,
        source: 'user',
        parentId,
      });
      await refresh();
      return file;
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
      const folder = await service.createFolder(scope, name, parentId);
      await refresh();
      return folder;
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

  const deleteWorkspace = useCallback(async () => {
    if (service) {
      const [listed, listedFolders] = await Promise.all([
        service.list(scope),
        service.listFolders(scope),
      ]);
      await Promise.all(listed.map((file) => service.remove(scope, file.id)));
      // Only remove root folders — deleteFolder already cascades to descendants.
      await Promise.all(
        listedFolders.filter((folder) => folder.parentId === null).map((folder) => service.deleteFolder(scope, folder.id)),
      );
    }
    onDeleteWorkspace();
  }, [onDeleteWorkspace, scope, service]);

  useEffect(() => {
    if (!service) {
      bindWorkspaceExecutionContext(null);
      return;
    }
    bindWorkspaceExecutionContext({
      service,
      scope,
      fileIds: files.map((file) => file.id),
      refresh,
    });
    return () => bindWorkspaceExecutionContext(null);
  }, [files, refresh, scope, service]);

  const importedRemoteKeys = useMemo(
    () => new Set(files.flatMap((file) => (file.remote ? [remoteKey(file.remote)] : []))),
    [files],
  );
  const availableRemoteFiles = useMemo(
    () => remoteFiles.filter((file) => !importedRemoteKeys.has(remoteKey(file))),
    [importedRemoteKeys, remoteFiles],
  );
  const value = useMemo<WorkspaceContextValue>(
    () => ({
      workspaces,
      activeWorkspace,
      selectWorkspace,
      createWorkspace,
      renameWorkspace,
      deleteWorkspace,
      scope,
      status,
      files,
      folders,
      remoteFiles: availableRemoteFiles,
      error,
      remoteError,
      refresh,
      uploadFiles,
      importRemoteFile,
      importRemoteFiles,
      readFile,
      removeFile,
      renameFile,
      writeTextFile,
      saveRuntimeFiles,
      moveFile,
      createFolder,
      renameFolder,
      moveFolder,
      deleteFolder,
    }),
    [
      availableRemoteFiles,
      activeWorkspace,
      createFolder,
      createWorkspace,
      deleteFolder,
      deleteWorkspace,
      error,
      files,
      folders,
      importRemoteFile,
      importRemoteFiles,
      moveFile,
      moveFolder,
      readFile,
      remoteError,
      refresh,
      removeFile,
      renameFile,
      renameFolder,
      renameWorkspace,
      scope,
      status,
      selectWorkspace,
      uploadFiles,
      workspaces,
      writeTextFile,
      saveRuntimeFiles,
    ],
  );

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
});
