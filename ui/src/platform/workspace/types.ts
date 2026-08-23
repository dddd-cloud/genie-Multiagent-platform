export const WORKSPACE_LIMITS = {
  MAX_FILES: 200,
  MAX_FILE_BYTES: 25 * 1024 * 1024,
  MAX_TOTAL_BYTES: 100 * 1024 * 1024,
  MAX_PREVIEW_BYTES: 512 * 1024,
  MAX_FILE_ID_LENGTH: 255,
  MAX_NAME_LENGTH: 255,
  MAX_MIME_TYPE_LENGTH: 255,
  MAX_SCOPE_PART_LENGTH: 160,
  MAX_SCOPE_KEY_LENGTH: 4_400,
  MAX_REMOTE_URL_LENGTH: 2_048,
} as const;

export type WorkspaceFileSource = 'user' | 'assistant' | 'imported';
export type WorkspaceSyncStatus = 'local' | 'synced' | 'sync-failed';
export type WorkspacePreviewKind = 'text' | 'image' | 'pdf' | 'office' | 'binary';

export interface WorkspaceScope {
  readonly userId: string;
  readonly workspaceId: string;
  readonly conversationId?: string;
  readonly key: string;
}

export interface WorkspaceRemoteFile {
  readonly requestId?: string;
  readonly fileName: string;
  readonly fileSize?: number;
  readonly downloadUrl?: string;
  readonly domainUrl?: string;
  readonly ossUrl?: string;
}

export interface WorkspaceFile {
  readonly id: string;
  readonly scopeKey: string;
  readonly name: string;
  /** Containing folder id, or null when the file sits at the workspace root. */
  readonly parentId: string | null;
  readonly size: number;
  readonly mimeType: string;
  readonly kind: WorkspacePreviewKind;
  readonly source: WorkspaceFileSource;
  readonly syncStatus: WorkspaceSyncStatus;
  readonly remote?: WorkspaceRemoteFile;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface WorkspaceFileRecord extends WorkspaceFile {
  readonly bytes: ArrayBuffer;
}

/** A folder is metadata-only; its contents are files/folders whose `parentId` points to it. */
export interface WorkspaceFolder {
  readonly id: string;
  readonly scopeKey: string;
  readonly name: string;
  readonly parentId: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/**
 * Binary payload transferred only across the UI-to-Worker execution boundary.
 * It deliberately contains no URL, scope, credential, or persistent-store metadata.
 */
export interface WorkspaceBinaryFile {
  readonly name: string;
  readonly mimeType: string;
  readonly bytes: ArrayBuffer;
}

export interface WorkspaceFileStore {
  list(scope: WorkspaceScope): Promise<WorkspaceFile[]>;
  read(scope: WorkspaceScope, fileId: string): Promise<ArrayBuffer | null>;
  put(scope: WorkspaceScope, record: WorkspaceFileRecord): Promise<WorkspaceFile>;
  /**
   * Writes a derived record only while the source version is still current.
   * Remote sync uses this to avoid restoring a locally renamed or deleted file.
   */
  replaceIfCurrent(
    scope: WorkspaceScope,
    expectedUpdatedAt: string,
    record: WorkspaceFileRecord,
  ): Promise<WorkspaceFile | null>;
  remove(scope: WorkspaceScope, fileId: string): Promise<void>;
  rename(scope: WorkspaceScope, fileId: string, name: string): Promise<WorkspaceFile>;
  moveFile(scope: WorkspaceScope, fileId: string, parentId: string | null): Promise<WorkspaceFile>;
  listFolders(scope: WorkspaceScope): Promise<WorkspaceFolder[]>;
  createFolder(scope: WorkspaceScope, name: string, parentId: string | null): Promise<WorkspaceFolder>;
  renameFolder(scope: WorkspaceScope, folderId: string, name: string): Promise<WorkspaceFolder>;
  moveFolder(scope: WorkspaceScope, folderId: string, parentId: string | null): Promise<WorkspaceFolder>;
  /** Cascades: every descendant folder and file is removed too. */
  deleteFolder(scope: WorkspaceScope, folderId: string): Promise<void>;
}

export interface WorkspaceFileInput {
  readonly name: string;
  readonly mimeType?: string;
  readonly bytes: ArrayBuffer;
  readonly source?: WorkspaceFileSource;
  readonly syncStatus?: WorkspaceSyncStatus;
  readonly remote?: WorkspaceRemoteFile;
  readonly id?: string;
  readonly parentId?: string | null;
}

export class WorkspaceError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'WorkspaceError';
    this.code = code;
  }
}

const TEXT_EXTENSIONS = new Set([
  'csv',
  'css',
  'html',
  'js',
  'json',
  'jsx',
  'log',
  'md',
  'py',
  'sql',
  'text',
  'ts',
  'tsx',
  'txt',
  'xml',
  'yaml',
  'yml',
]);

const OFFICE_EXTENSIONS = new Set([
  'doc',
  'docx',
  'ppt',
  'pptx',
  'xls',
  'xlsx',
]);

export function fileExtension(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot <= 0 ? '' : name.slice(dot + 1).toLowerCase();
}

export function previewKind(name: string, mimeType = ''): WorkspacePreviewKind {
  const extension = fileExtension(name);
  if (mimeType.startsWith('image/')) return 'image';
  if (mimeType === 'application/pdf' || extension === 'pdf') return 'pdf';
  if (OFFICE_EXTENSIONS.has(extension)) return 'office';
  if (
    mimeType.startsWith('text/') ||
    mimeType === 'application/json' ||
    TEXT_EXTENSIONS.has(extension)
  ) {
    return 'text';
  }
  return 'binary';
}

const CONTROL_CHARACTERS = /[\u0000-\u001F\u007F]/;
const FILE_SOURCES = new Set<WorkspaceFileSource>(['user', 'assistant', 'imported']);
const SYNC_STATUSES = new Set<WorkspaceSyncStatus>(['local', 'synced', 'sync-failed']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function normalizeMimeType(value: string | undefined): string {
  const normalized = value?.trim().toLowerCase() || 'application/octet-stream';
  if (
    normalized.length > WORKSPACE_LIMITS.MAX_MIME_TYPE_LENGTH ||
    CONTROL_CHARACTERS.test(normalized)
  ) {
    return 'application/octet-stream';
  }
  return normalized;
}

export function normalizeFileName(name: string): string {
  const value = name.normalize('NFC').trim();
  if (!value || value === '.' || value === '..') {
    throw new WorkspaceError('INVALID_FILE_NAME', '文件名不能为空');
  }
  if (
    value.length > WORKSPACE_LIMITS.MAX_NAME_LENGTH ||
    value.includes('/') ||
    value.includes('\\') ||
    CONTROL_CHARACTERS.test(value)
  ) {
    throw new WorkspaceError('INVALID_FILE_NAME', '文件名包含非法路径字符');
  }
  return value;
}

export function assertWorkspaceFileId(value: string): string {
  const id = value.trim();
  if (
    !id ||
    id.length > WORKSPACE_LIMITS.MAX_FILE_ID_LENGTH ||
    id.includes('/') ||
    id.includes('\\') ||
    CONTROL_CHARACTERS.test(id)
  ) {
    throw new WorkspaceError('INVALID_FILE', '文件标识无效');
  }
  return id;
}

function isArrayBuffer(value: unknown): value is ArrayBuffer {
  return Object.prototype.toString.call(value) === '[object ArrayBuffer]';
}

export function assertFileBytes(bytes: ArrayBuffer): void {
  if (!isArrayBuffer(bytes)) {
    throw new WorkspaceError('INVALID_FILE', '文件内容无效');
  }
  if (bytes.byteLength > WORKSPACE_LIMITS.MAX_FILE_BYTES) {
    throw new WorkspaceError('FILE_TOO_LARGE', '单个文件超过工作区大小限制');
  }
}

function normalizeRemoteUrl(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim();
  if (
    !normalized ||
    normalized.length > WORKSPACE_LIMITS.MAX_REMOTE_URL_LENGTH ||
    CONTROL_CHARACTERS.test(normalized)
  ) {
    throw new WorkspaceError('INVALID_FILE', '远端文件地址无效');
  }
  return normalized;
}

export function normalizeWorkspaceRemoteFile(
  value: unknown,
  expectedRequestId?: string,
  expectedName?: string,
  expectedSize?: number,
): WorkspaceRemoteFile | undefined {
  if (value === undefined) return undefined;
  if (!isRecord(value)) {
    throw new WorkspaceError('INVALID_FILE', '远端文件元数据无效');
  }
  if (typeof value.fileName !== 'string') {
    throw new WorkspaceError('INVALID_FILE', '远端文件名无效');
  }
  const fileName = normalizeFileName(value.fileName);
  if (expectedName && fileName !== expectedName) {
    throw new WorkspaceError('INVALID_FILE', '远端文件名与当前文件不一致');
  }
  const requestId =
    typeof value.requestId === 'string' && value.requestId.trim()
      ? value.requestId.trim()
      : undefined;
  if (
    requestId &&
    (requestId.length > WORKSPACE_LIMITS.MAX_SCOPE_KEY_LENGTH ||
      CONTROL_CHARACTERS.test(requestId))
  ) {
    throw new WorkspaceError('INVALID_FILE', '远端文件作用域无效');
  }
  if (expectedRequestId && requestId !== expectedRequestId) {
    throw new WorkspaceError('SCOPE_MISMATCH', '远端文件不属于当前工作区');
  }
  const fileSize =
    value.fileSize === undefined
      ? undefined
      : typeof value.fileSize === 'number' &&
          Number.isSafeInteger(value.fileSize) &&
          value.fileSize >= 0
        ? value.fileSize
        : (() => {
            throw new WorkspaceError('INVALID_FILE', '远端文件大小无效');
          })();
  if (expectedSize !== undefined && fileSize !== undefined && fileSize !== expectedSize) {
    throw new WorkspaceError('INVALID_FILE', '远端文件大小与当前文件不一致');
  }
  return {
    fileName,
    fileSize,
    requestId,
    downloadUrl: normalizeRemoteUrl(value.downloadUrl),
    domainUrl: normalizeRemoteUrl(value.domainUrl),
    ossUrl: normalizeRemoteUrl(value.ossUrl),
  };
}

function assertTimestamp(value: unknown, label: string): string {
  if (typeof value !== 'string' || !Number.isFinite(Date.parse(value))) {
    throw new WorkspaceError('INVALID_FILE', `${label}无效`);
  }
  return value;
}

export function normalizeWorkspaceFileRecord(
  value: unknown,
  expectedScopeKey?: string,
): WorkspaceFileRecord {
  if (!isRecord(value)) {
    throw new WorkspaceError('INVALID_FILE', '工作区文件记录无效');
  }
  const scopeKey = value.scopeKey;
  if (typeof scopeKey !== 'string' || !scopeKey) {
    throw new WorkspaceError('INVALID_FILE', '工作区文件作用域无效');
  }
  if (expectedScopeKey && scopeKey !== expectedScopeKey) {
    throw new WorkspaceError('SCOPE_MISMATCH', '文件不属于当前工作区');
  }
  const id = typeof value.id === 'string' ? assertWorkspaceFileId(value.id) : (() => {
    throw new WorkspaceError('INVALID_FILE', '文件标识无效');
  })();
  const name = typeof value.name === 'string' ? normalizeFileName(value.name) : (() => {
    throw new WorkspaceError('INVALID_FILE', '文件名无效');
  })();
  const bytes = value.bytes;
  if (!isArrayBuffer(bytes)) {
    throw new WorkspaceError('INVALID_FILE', '文件内容无效');
  }
  assertFileBytes(bytes);
  const size = value.size;
  if (
    typeof size !== 'number' ||
    !Number.isSafeInteger(size) ||
    size < 0 ||
    size !== bytes.byteLength
  ) {
    throw new WorkspaceError('INVALID_FILE', '文件元数据与内容大小不一致');
  }
  if (typeof value.mimeType !== 'string') {
    throw new WorkspaceError('INVALID_FILE', '文件类型无效');
  }
  const mimeType = normalizeMimeType(value.mimeType);
  if (typeof value.source !== 'string' || !FILE_SOURCES.has(value.source as WorkspaceFileSource)) {
    throw new WorkspaceError('INVALID_FILE', '文件来源无效');
  }
  if (
    typeof value.syncStatus !== 'string' ||
    !SYNC_STATUSES.has(value.syncStatus as WorkspaceSyncStatus)
  ) {
    throw new WorkspaceError('INVALID_FILE', '文件同步状态无效');
  }
  const remote = normalizeWorkspaceRemoteFile(value.remote, undefined, name, size);
  return {
    id,
    scopeKey,
    name,
    parentId: normalizeParentId(value.parentId),
    size,
    mimeType,
    kind: previewKind(name, mimeType),
    source: value.source as WorkspaceFileSource,
    syncStatus: value.syncStatus as WorkspaceSyncStatus,
    remote,
    createdAt: assertTimestamp(value.createdAt, '创建时间'),
    updatedAt: assertTimestamp(value.updatedAt, '更新时间'),
    bytes: bytes.slice(0),
  };
}

/** Records written before folders existed have no `parentId`; treat them as workspace-root. */
function normalizeParentId(value: unknown): string | null {
  if (value === null || value === undefined) return null;
  return assertWorkspaceFileId(String(value));
}

export function assertFolderName(name: string): string {
  // Folders share the same naming rules as files (no path separators, no control characters).
  return normalizeFileName(name);
}

export function normalizeWorkspaceFolder(
  value: unknown,
  expectedScopeKey?: string,
): WorkspaceFolder {
  if (!isRecord(value)) {
    throw new WorkspaceError('INVALID_FOLDER', '工作区文件夹记录无效');
  }
  const scopeKey = value.scopeKey;
  if (typeof scopeKey !== 'string' || !scopeKey) {
    throw new WorkspaceError('INVALID_FOLDER', '工作区文件夹作用域无效');
  }
  if (expectedScopeKey && scopeKey !== expectedScopeKey) {
    throw new WorkspaceError('SCOPE_MISMATCH', '文件夹不属于当前工作区');
  }
  const id = typeof value.id === 'string' ? assertWorkspaceFileId(value.id) : (() => {
    throw new WorkspaceError('INVALID_FOLDER', '文件夹标识无效');
  })();
  const name = typeof value.name === 'string' ? assertFolderName(value.name) : (() => {
    throw new WorkspaceError('INVALID_FOLDER', '文件夹名无效');
  })();
  return {
    id,
    scopeKey,
    name,
    parentId: normalizeParentId(value.parentId),
    createdAt: assertTimestamp(value.createdAt, '创建时间'),
    updatedAt: assertTimestamp(value.updatedAt, '更新时间'),
  };
}

export function createWorkspaceFolderRecord(
  scope: WorkspaceScope,
  name: string,
  parentId: string | null,
  now = new Date().toISOString(),
): WorkspaceFolder {
  return {
    id: createWorkspaceFileId(),
    scopeKey: scope.key,
    name: assertFolderName(name),
    parentId: parentId === null ? null : assertWorkspaceFileId(parentId),
    createdAt: now,
    updatedAt: now,
  };
}

export function createWorkspaceFileId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    return `file-${Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')}`;
  }
  return `file-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function createWorkspaceRecord(
  scope: WorkspaceScope,
  input: WorkspaceFileInput,
  now = new Date().toISOString(),
): WorkspaceFileRecord {
  const name = normalizeFileName(input.name);
  const id = assertWorkspaceFileId(input.id ?? createWorkspaceFileId());
  assertFileBytes(input.bytes);
  const bytes = input.bytes.slice(0);
  const mimeType = normalizeMimeType(input.mimeType);
  const remote = normalizeWorkspaceRemoteFile(input.remote, undefined, name, bytes.byteLength);
  return {
    id,
    scopeKey: scope.key,
    name,
    parentId: input.parentId === undefined ? null : normalizeParentId(input.parentId),
    size: bytes.byteLength,
    mimeType,
    kind: previewKind(name, mimeType),
    source: input.source ?? 'user',
    syncStatus: input.syncStatus ?? 'local',
    remote,
    createdAt: now,
    updatedAt: now,
    bytes,
  };
}
