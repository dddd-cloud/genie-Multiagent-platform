import {
  WORKSPACE_LIMITS,
  WorkspaceError,
  normalizeFileName,
  type WorkspaceScope,
} from './types';

function validateScopePart(value: unknown, label: string): string {
  if (typeof value !== 'string') {
    throw new WorkspaceError('INVALID_SCOPE', `${label} 无效`);
  }
  const normalized = value.normalize('NFC').trim();
  if (
    !normalized ||
    normalized.length > WORKSPACE_LIMITS.MAX_SCOPE_PART_LENGTH ||
    normalized.includes('/') ||
    normalized.includes('\\') ||
    /[\u0000-\u001F\u007F]/.test(normalized)
  ) {
    throw new WorkspaceError('INVALID_SCOPE', `${label} 无效`);
  }
  return normalized;
}

export function createWorkspaceId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `workspace-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function buildWorkspaceScope(
  userId: string,
  workspaceId: string,
  conversationId?: string,
): WorkspaceScope {
  const user = validateScopePart(userId, 'userId');
  const workspace = validateScopePart(workspaceId, 'workspaceId');
  const conversation =
    conversationId === undefined
      ? undefined
      : validateScopePart(conversationId, 'conversationId');
  const key = [user, workspace, conversation ?? 'workspace']
    .map((part) => encodeURIComponent(part))
    .join(':');
  return {
    userId: user,
    workspaceId: workspace,
    conversationId: conversation,
    key,
  };
}

export function assertWorkspaceScope(scope: WorkspaceScope): WorkspaceScope {
  if (!scope || typeof scope !== 'object') {
    throw new WorkspaceError('INVALID_SCOPE', '工作区作用域无效');
  }
  const expected = buildWorkspaceScope(
    scope.userId,
    scope.workspaceId,
    scope.conversationId,
  );
  if (
    typeof scope.key !== 'string' ||
    scope.key.length > WORKSPACE_LIMITS.MAX_SCOPE_KEY_LENGTH ||
    scope.key !== expected.key
  ) {
    throw new WorkspaceError('INVALID_SCOPE', '工作区作用域无效');
  }
  return expected;
}

function fallbackScopeDigest(value: string): string {
  const hash = (seed: number): string => {
    let result = seed;
    for (let index = 0; index < value.length; index += 1) {
      result ^= value.charCodeAt(index);
      result = Math.imul(result, 0x01000193);
    }
    return (result >>> 0).toString(16).padStart(8, '0');
  };
  return [0x811c9dc5, 0x9e3779b9, 0x85ebca6b, 0xc2b2ae35].map(hash).join('');
}

function hex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

async function digestScopeValue(value: string): Promise<string> {
  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest(
      'SHA-256',
      new TextEncoder().encode(value),
    );
    return hex(digest);
  }
  return fallbackScopeDigest(value);
}

export async function createWorkspaceRemoteRequestId(scope: WorkspaceScope): Promise<string> {
  const currentScope = assertWorkspaceScope(scope);
  const digest = await digestScopeValue(`joyagent-workspace/v1/${currentScope.key}`);
  return `workspace-v1-${digest}`;
}

export async function createWorkspaceRemoteFileId(
  scope: WorkspaceScope,
  fileName: string,
): Promise<string> {
  const normalizedName = normalizeFileName(fileName);
  const requestId = await createWorkspaceRemoteRequestId(scope);
  const digest = await digestScopeValue(
    `joyagent-workspace-file/v1/${requestId}\u0000${normalizedName}`,
  );
  return `remote-v1-${digest}`;
}

export function isWorkspaceScopeKey(value: string): boolean {
  if (!value || value.length > WORKSPACE_LIMITS.MAX_SCOPE_KEY_LENGTH) {
    return false;
  }
  return value.split(':').every((part) => {
    try {
      return Boolean(decodeURIComponent(part));
    } catch {
      return false;
    }
  });
}
