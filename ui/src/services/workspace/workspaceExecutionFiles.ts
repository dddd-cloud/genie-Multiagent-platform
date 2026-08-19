import type { BrowserSkillExecutionSignal } from '@/contracts';
import {
  WORKSPACE_LIMITS,
  WorkspaceError,
  assertFileBytes,
  assertWorkspaceFileId,
  fileExtension,
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

function uniqueName(name: string, occupied: Set<string>): string {
  const normalized = normalizeFileName(name);
  if (!occupied.has(normalized)) return normalized;
  const extension = fileExtension(normalized);
  const suffix = extension ? `.${extension}` : '';
  const stem = extension ? normalized.slice(0, -(extension.length + 1)) : normalized;
  for (let index = 1; index <= WORKSPACE_LIMITS.MAX_FILES; index += 1) {
    const candidate = normalizeFileName(`${stem} (${index})${suffix}`);
    if (!occupied.has(candidate)) return candidate;
  }
  throw new WorkspaceError('DUPLICATE_FILE_NAME', '无法为 Python 产物分配文件名');
}

function outputFile(file: WorkspaceBinaryFile, occupied: Set<string>): WorkspaceBinaryFile {
  assertFileBytes(file.bytes);
  const name = uniqueName(file.name, occupied);
  const mimeType = file.mimeType.trim() || 'application/octet-stream';
  return { name, mimeType, bytes: file.bytes.slice(0) };
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
      const occupied = new Set((await service.list(scope)).map((file) => file.name));
      const saved: WorkspaceFile[] = [];
      let totalBytes = 0;
      for (const candidate of files) {
        assertNotAborted(abortSignal);
        const file = outputFile(candidate, occupied);
        totalBytes += file.bytes.byteLength;
        if (totalBytes > EXECUTION_LIMITS.MAX_OUTPUT_BYTES) {
          throw new WorkspaceError('WORKSPACE_SIZE_LIMIT', 'Python 产物总大小超过上限');
        }
        const outcome = await service.upload(
          scope,
          { ...file, source: 'assistant' },
          abortSignal,
        );
        occupied.add(outcome.file.name);
        saved.push(outcome.file);
      }
      return saved;
    },
  };
}
