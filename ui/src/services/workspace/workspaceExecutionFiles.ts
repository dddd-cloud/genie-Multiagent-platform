import type { BrowserSkillExecutionSignal } from '@/contracts';
import {
  WorkspaceError,
  assertFileBytes,
  assertWorkspaceFileId,
  normalizeFileName,
  type WorkspaceBinaryFile,
  type WorkspaceFile,
  type WorkspaceScope,
} from '@/platform/workspace/types';
import type { WorkspaceService } from './workspaceService';

export interface WorkspaceExecutionFileBridge {
  loadInputFiles(
    signal: BrowserSkillExecutionSignal,
    abortSignal?: AbortSignal,
  ): Promise<readonly WorkspaceBinaryFile[]>;
  saveOutputFiles(
    signal: BrowserSkillExecutionSignal,
    files: readonly WorkspaceBinaryFile[],
    abortSignal?: AbortSignal,
  ): Promise<readonly WorkspaceFile[]>;
  deleteFilesByName(
    signal: BrowserSkillExecutionSignal,
    names: readonly string[],
    abortSignal?: AbortSignal,
  ): Promise<readonly string[]>;
}

export interface WorkspaceExecutionFileBridgeOptions {
  readonly service: WorkspaceService;
  readonly scope: WorkspaceScope;
  /** IDs are granted explicitly by the active user for this bridge instance. */
  readonly fileIds: readonly string[];
}

const EXECUTION_LIMITS = {
  MAX_INPUT_FILES: 32,
  MAX_INPUT_BYTES: 50 * 1024 * 1024,
  MAX_OUTPUT_FILES: 32,
  MAX_OUTPUT_BYTES: 50 * 1024 * 1024,
} as const;

function assertNotAborted(signal?: AbortSignal): void {
  if (signal?.aborted) throw new DOMException('aborted', 'AbortError');
}

function grantedIds(fileIds: readonly string[]): readonly string[] {
  if (fileIds.length > EXECUTION_LIMITS.MAX_INPUT_FILES) {
    throw new WorkspaceError('FILE_COUNT_LIMIT', '本次 Python 执行的输入文件过多');
  }
  const ids = fileIds.map(assertWorkspaceFileId);
  if (new Set(ids).size !== ids.length) {
    throw new WorkspaceError('INVALID_FILE', 'Python 文件授权包含重复文件');
  }
  return ids;
}

function outputFile(file: WorkspaceBinaryFile): WorkspaceBinaryFile {
  assertFileBytes(file.bytes);
  const name = normalizeFileName(file.name);
  const mimeType = file.mimeType.trim() || 'application/octet-stream';
  return { name, mimeType, bytes: file.bytes.slice(0) };
}

function deletedFileNames(names: readonly string[]): readonly string[] {
  if (names.length > EXECUTION_LIMITS.MAX_INPUT_FILES) {
    throw new WorkspaceError('FILE_COUNT_LIMIT', 'Python 删除文件数量超过上限');
  }
  const normalized = names.map(normalizeFileName);
  if (new Set(normalized).size !== normalized.length) {
    throw new WorkspaceError('INVALID_FILE', 'Python 删除文件列表包含重复文件');
  }
  return normalized;
}

/**
 * Creates a one-scope, explicit-grant bridge. No global registry is used, so a
 * live execution cannot discover files merely by knowing a workspace identity.
 */
export function createWorkspaceExecutionFileBridge(
  options: WorkspaceExecutionFileBridgeOptions,
): WorkspaceExecutionFileBridge {
  const { service, scope } = options;
  const fileIds = grantedIds(options.fileIds);

  return {
    async loadInputFiles(_signal, abortSignal) {
      assertNotAborted(abortSignal);
      const metadata = new Map((await service.list(scope)).map((file) => [file.id, file]));
      const files: WorkspaceBinaryFile[] = [];
      let totalBytes = 0;
      for (const fileId of fileIds) {
        assertNotAborted(abortSignal);
        const file = metadata.get(fileId);
        if (!file) throw new WorkspaceError('FILE_NOT_FOUND', '授权文件已不存在');
        const bytes = await service.read(scope, fileId);
        if (!bytes || bytes.byteLength !== file.size) {
          throw new WorkspaceError('INVALID_FILE', '授权文件内容不可用');
        }
        totalBytes += bytes.byteLength;
        if (totalBytes > EXECUTION_LIMITS.MAX_INPUT_BYTES) {
          throw new WorkspaceError('WORKSPACE_SIZE_LIMIT', 'Python 输入文件超过本次执行上限');
        }
        files.push({ name: file.name, mimeType: file.mimeType, bytes: bytes.slice(0) });
      }
      return files;
    },

    async saveOutputFiles(_signal, files, abortSignal) {
      if (files.length > EXECUTION_LIMITS.MAX_OUTPUT_FILES) {
        throw new WorkspaceError('FILE_COUNT_LIMIT', 'Python 产物数量超过上限');
      }
      const saved: WorkspaceFile[] = [];
      let totalBytes = 0;
      for (const candidate of files) {
        assertNotAborted(abortSignal);
        const file = outputFile(candidate);
        totalBytes += file.bytes.byteLength;
        if (totalBytes > EXECUTION_LIMITS.MAX_OUTPUT_BYTES) {
          throw new WorkspaceError('WORKSPACE_SIZE_LIMIT', 'Python 产物总大小超过上限');
        }
        const savedFile = await service.upsertByName(
          scope,
          { ...file, source: 'assistant' },
        );
        saved.push(savedFile);
      }
      return saved;
    },

    async deleteFilesByName(_signal, names, abortSignal) {
      const requestedNames = deletedFileNames(names);
      const granted = new Set(fileIds);
      const currentByName = new Map(
        (await service.list(scope)).map((file) => [file.name, file]),
      );
      const deleted: string[] = [];
      for (const name of requestedNames) {
        assertNotAborted(abortSignal);
        const file = currentByName.get(name);
        if (!file) continue;
        if (!granted.has(file.id)) {
          throw new WorkspaceError('SCOPE_MISMATCH', 'Python 无权删除未挂载的工作区文件');
        }
        await service.remove(scope, file.id);
        deleted.push(name);
      }
      return deleted;
    },
  };
}
