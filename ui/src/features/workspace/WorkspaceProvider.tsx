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
  type WorkspaceFileStore,
  type WorkspaceRemoteFile,
} from '@/platform/workspace/types';
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
    () => (defaultStore ? new WorkspaceService(defaultStore, remoteAdapter) : null),
    [defaultStore, remoteAdapter],
  );
  const activeScopeKey = useRef(scope.key);
  const refreshGeneration = useRef(0);
  activeScopeKey.current = scope.key;
  const [status, setStatus] = useState<WorkspaceStatus>('loading');
  const [files, setFiles] = useState<WorkspaceContextValue['files']>([]);
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
    const [localResult, remoteResult] = await Promise.allSettled([
      service.list(scope),
      service.listRemote(scope),
    ]);
    if (!isCurrent()) return;
    if (localResult.status === 'rejected') {
      setStatus('error');
      setError(errorMessage(localResult.reason, '工作区文件读取失败'));
      return;
    }
    setFiles(localResult.value);
    if (remoteResult.status === 'fulfilled') {
      setRemoteFiles(remoteResult.value);
    } else {
      setRemoteFiles([]);
      setRemoteError(errorMessage(remoteResult.reason, '远端文件同步不可用'));
    }
    setStatus('ready');
  }, [scope, service]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const uploadFiles = useCallback(
    async (items: File[]): Promise<WorkspaceOperationFailure[]> => {
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

  useEffect(() => {
    if (!service) {
      bindWorkspaceExecutionContext(null);
      return;
    }
    bindWorkspaceExecutionContext({
      service,
      scope,
      fileIds: files.map((file) => file.id),
    });
    return () => bindWorkspaceExecutionContext(null);
  }, [files, scope, service]);

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
      scope,
      status,
      files,
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
    }),
    [
      availableRemoteFiles,
      error,
      files,
      importRemoteFile,
      importRemoteFiles,
      readFile,
      remoteError,
      refresh,
      removeFile,
      renameFile,
      scope,
      status,
      uploadFiles,
    ],
  );

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
});
