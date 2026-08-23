import { createContext, useContext } from 'react';
import type {
  WorkspaceBinaryFile,
  WorkspaceFile,
  WorkspaceFolder,
  WorkspaceRemoteFile,
  WorkspaceScope,
} from '@/platform/workspace/types';
import type { UserWorkspace } from '@/platform/workspace/catalog';

export type WorkspaceStatus = 'loading' | 'ready' | 'unavailable' | 'error';

export interface WorkspaceOperationFailure {
  readonly name: string;
  readonly message: string;
}

export interface WorkspaceContextValue {
  readonly workspaces: readonly UserWorkspace[];
  readonly activeWorkspace: UserWorkspace;
  readonly selectWorkspace: (workspaceId: string) => void;
  readonly createWorkspace: (name: string) => void;
  readonly renameWorkspace: (name: string) => void;
  readonly deleteWorkspace: () => Promise<void>;
  readonly scope: WorkspaceScope;
  readonly status: WorkspaceStatus;
  readonly files: WorkspaceFile[];
  readonly folders: WorkspaceFolder[];
  readonly remoteFiles: WorkspaceRemoteFile[];
  readonly error: string | null;
  readonly remoteError: string | null;
  readonly refresh: () => Promise<void>;
  readonly uploadFiles: (files: File[], parentId?: string | null) => Promise<WorkspaceOperationFailure[]>;
  readonly importRemoteFile: (file: WorkspaceRemoteFile) => Promise<void>;
  readonly importRemoteFiles: (
    files: readonly WorkspaceRemoteFile[],
  ) => Promise<WorkspaceOperationFailure[]>;
  readonly readFile: (fileId: string) => Promise<ArrayBuffer | null>;
  readonly removeFile: (fileId: string) => Promise<void>;
  readonly renameFile: (fileId: string, name: string) => Promise<void>;
  readonly writeTextFile: (
    fileId: string | undefined,
    name: string,
    content: string,
    parentId?: string | null,
  ) => Promise<WorkspaceFile>;
  readonly saveRuntimeFiles: (files: readonly WorkspaceBinaryFile[]) => Promise<void>;
  readonly moveFile: (fileId: string, parentId: string | null) => Promise<void>;
  readonly createFolder: (name: string, parentId: string | null) => Promise<WorkspaceFolder>;
  readonly renameFolder: (folderId: string, name: string) => Promise<void>;
  readonly moveFolder: (folderId: string, parentId: string | null) => Promise<void>;
  readonly deleteFolder: (folderId: string) => Promise<void>;
}

export const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

export function useWorkspace(): WorkspaceContextValue {
  const value = useContext(WorkspaceContext);
  if (!value) {
    throw new Error('useWorkspace must be used inside WorkspaceProvider');
  }
  return value;
}
