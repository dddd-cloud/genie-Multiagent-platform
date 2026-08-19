import { createContext, useContext } from 'react';
import type {
  WorkspaceFile,
  WorkspaceRemoteFile,
  WorkspaceScope,
} from '@/platform/workspace/types';

export type WorkspaceStatus = 'loading' | 'ready' | 'unavailable' | 'error';

export interface WorkspaceOperationFailure {
  readonly name: string;
  readonly message: string;
}

export interface WorkspaceContextValue {
  readonly scope: WorkspaceScope;
  readonly status: WorkspaceStatus;
  readonly files: WorkspaceFile[];
  readonly remoteFiles: WorkspaceRemoteFile[];
  readonly error: string | null;
  readonly remoteError: string | null;
  readonly refresh: () => Promise<void>;
  readonly uploadFiles: (files: File[]) => Promise<WorkspaceOperationFailure[]>;
  readonly importRemoteFile: (file: WorkspaceRemoteFile) => Promise<void>;
  readonly importRemoteFiles: (
    files: readonly WorkspaceRemoteFile[],
  ) => Promise<WorkspaceOperationFailure[]>;
  readonly readFile: (fileId: string) => Promise<ArrayBuffer | null>;
  readonly removeFile: (fileId: string) => Promise<void>;
  readonly renameFile: (fileId: string, name: string) => Promise<void>;
}

export const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

export function useWorkspace(): WorkspaceContextValue {
  const value = useContext(WorkspaceContext);
  if (!value) {
    throw new Error('useWorkspace must be used inside WorkspaceProvider');
  }
  return value;
}
