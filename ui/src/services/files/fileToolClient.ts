import {
  WORKSPACE_LIMITS,
  WorkspaceError,
  normalizeFileName,
  type WorkspaceRemoteFile,
  type WorkspaceScope,
} from '@/platform/workspace/types';
import { applyCsrfHeaders } from '@/features/auth/csrf';

const WORKSPACE_BASE = '/api/v2/workspaces';

type FileToolResponse = {
  code?: unknown;
  data?: unknown;
  results?: unknown;
  fileName?: unknown;
  fileSize?: unknown;
  requestId?: unknown;
  downloadUrl?: unknown;
  domainUrl?: unknown;
  ossUrl?: unknown;
};

export class FileToolError extends Error {
  readonly status: number;

  constructor(message: string, status = 0) {
    super(message);
    this.name = 'FileToolError';
    this.status = status;
  }
}

export interface WorkspaceRemoteAdapter {
  list(scope: WorkspaceScope, signal?: AbortSignal): Promise<WorkspaceRemoteFile[]>;
  upload(
    scope: WorkspaceScope,
    file: Blob,
    fileName: string,
    signal?: AbortSignal,
  ): Promise<WorkspaceRemoteFile>;
  download(
    scope: WorkspaceScope,
    file: WorkspaceRemoteFile,
    signal?: AbortSignal,
  ): Promise<ArrayBuffer>;
}

function textValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function numberValue(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
    ? value
    : undefined;
}

function workspaceFilesRoute(scope: WorkspaceScope): string | null {
  if (!scope.conversationId) return null;
  return `${WORKSPACE_BASE}/${encodeURIComponent(scope.conversationId)}/files`;
}

function fileRoute(
  scope: WorkspaceScope,
  action: 'download' | 'preview',
  fileName: string,
): string {
  const base = workspaceFilesRoute(scope);
  if (!base) {
    throw new WorkspaceError('INVALID_SCOPE', '当前工作区缺少会话标识');
  }
  return `${base}/${encodeURIComponent(fileName)}/${action}`;
}

function parseRemoteFile(
  raw: FileToolResponse,
  fallbackName: string | undefined,
  scope: WorkspaceScope,
  expectedSize?: number,
): WorkspaceRemoteFile | null {
  const rawName = textValue(raw.fileName) ?? fallbackName;
  if (!rawName) return null;
  let fileName: string;
  try {
    fileName = normalizeFileName(rawName);
  } catch {
    return null;
  }
  if (fallbackName && fileName !== fallbackName) return null;
  const fileSize = numberValue(raw.fileSize);
  if (raw.fileSize !== undefined && fileSize === undefined) return null;
  if (expectedSize !== undefined && fileSize !== undefined && fileSize !== expectedSize) {
    return null;
  }
  const downloadUrl = fileRoute(scope, 'download', fileName);
  const domainUrl = fileRoute(scope, 'preview', fileName);
  return {
    fileName,
    fileSize,
    requestId: textValue(raw.requestId),
    downloadUrl,
    domainUrl,
    ossUrl: downloadUrl,
  };
}

function responsePayload(body: FileToolResponse): FileToolResponse {
  if (textValue(body.code) === 'OK' && body.data && typeof body.data === 'object') {
    return body.data as FileToolResponse;
  }
  return body.data && typeof body.data === 'object' && !('fileName' in body) && !('results' in body)
    ? (body.data as FileToolResponse)
    : body;
}

async function parseJson(response: Response): Promise<FileToolResponse> {
  try {
    return (await response.json()) as FileToolResponse;
  } catch {
    throw new FileToolError('文件服务返回了无效响应', response.status);
  }
}

async function assertOk(response: Response): Promise<void> {
  if (response.ok) return;
  let message = `文件服务请求失败（${response.status}）`;
  try {
    const body = (await response.json()) as { message?: unknown; detail?: unknown };
    message = textValue(body.message) ?? textValue(body.detail) ?? message;
  } catch {
    // Keep the status-based message for non-JSON errors.
  }
  throw new FileToolError(message, response.status);
}

function requestHeaders(headers: Record<string, string>): Record<string, string> {
  return applyCsrfHeaders(headers);
}

async function readLimitedResponse(response: Response): Promise<ArrayBuffer> {
  const declaredBytes = Number(response.headers.get('content-length') ?? 0);
  if (declaredBytes > WORKSPACE_LIMITS.MAX_FILE_BYTES) {
    throw new WorkspaceError('FILE_TOO_LARGE', '远端文件超过工作区大小限制');
  }
  if (!response.body) {
    const bytes = await response.arrayBuffer();
    if (bytes.byteLength > WORKSPACE_LIMITS.MAX_FILE_BYTES) {
      throw new WorkspaceError('FILE_TOO_LARGE', '远端文件超过工作区大小限制');
    }
    return bytes;
  }

  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!value) continue;
      total += value.byteLength;
      if (total > WORKSPACE_LIMITS.MAX_FILE_BYTES) {
        await reader.cancel('workspace file is too large');
        throw new WorkspaceError('FILE_TOO_LARGE', '远端文件超过工作区大小限制');
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return merged.buffer;
}

export class FileToolWorkspaceAdapter implements WorkspaceRemoteAdapter {
  async list(scope: WorkspaceScope, signal?: AbortSignal): Promise<WorkspaceRemoteFile[]> {
    const route = workspaceFilesRoute(scope);
    if (!route) return [];
    const response = await fetch(
      `${route}?page=1&pageSize=${WORKSPACE_LIMITS.MAX_FILES}`,
      {
        method: 'GET',
        credentials: 'include',
        signal,
        headers: requestHeaders({ Accept: 'application/json' }),
      },
    );
    await assertOk(response);
    const body = responsePayload(await parseJson(response));
    if (!Array.isArray(body.results)) return [];
    return body.results
      .map((item) =>
        item && typeof item === 'object'
          ? parseRemoteFile(item as FileToolResponse, undefined, scope)
          : null,
      )
      .filter((item): item is WorkspaceRemoteFile => item !== null);
  }

  async upload(
    scope: WorkspaceScope,
    file: Blob,
    fileName: string,
    signal?: AbortSignal,
  ): Promise<WorkspaceRemoteFile> {
    if (file.size > WORKSPACE_LIMITS.MAX_FILE_BYTES) {
      throw new WorkspaceError('FILE_TOO_LARGE', '单个文件超过工作区大小限制');
    }
    const route = workspaceFilesRoute(scope);
    if (!route) {
      throw new WorkspaceError('INVALID_SCOPE', '当前工作区缺少会话标识');
    }
    let normalizedFileName: string;
    try {
      normalizedFileName = normalizeFileName(fileName);
    } catch {
      throw new FileToolError('文件名不合法');
    }
    const form = new FormData();
    form.append('file', file, normalizedFileName);
    const response = await fetch(route, {
      method: 'POST',
      credentials: 'include',
      signal,
      headers: requestHeaders({}),
      body: form,
    });
    await assertOk(response);
    const remote = parseRemoteFile(
      responsePayload(await parseJson(response)),
      normalizedFileName,
      scope,
      file.size,
    );
    if (!remote) throw new FileToolError('文件服务未返回文件信息', response.status);
    return remote;
  }

  async download(
    scope: WorkspaceScope,
    file: WorkspaceRemoteFile,
    signal?: AbortSignal,
  ): Promise<ArrayBuffer> {
    let fileName: string;
    try {
      fileName = normalizeFileName(file.fileName);
    } catch {
      throw new FileToolError('远端文件名不合法');
    }
    const url = fileRoute(scope, 'download', fileName);
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
      signal,
      headers: requestHeaders({ Accept: '*/*' }),
    });
    await assertOk(response);
    return readLimitedResponse(response);
  }
}

export const fileToolWorkspaceAdapter = new FileToolWorkspaceAdapter();
