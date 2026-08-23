import { createWorkspaceId } from './scope';
import { WORKSPACE_LIMITS, WorkspaceError } from './types';

export interface UserWorkspace {
  readonly id: string;
  readonly name: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

const CATALOG_PREFIX = 'joyagent.workspaces.';
const ACTIVE_PREFIX = 'joyagent.activeWorkspace.';
const LEGACY_PREFIX = 'joyagent.workspaceId.';
const DEFAULT_NAME = '默认工作区';
const MAX_WORKSPACES = 20;

function normalizeWorkspaceName(value: string): string {
  const name = value.normalize('NFC').trim();
  if (!name || name.length > 80 || /[\u0000-\u001F\u007F]/.test(name)) {
    throw new WorkspaceError('INVALID_WORKSPACE_NAME', '工作区名称不能为空且不能超过 80 个字符');
  }
  return name;
}

function isWorkspace(value: unknown): value is UserWorkspace {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<UserWorkspace>;
  return Boolean(
    typeof item.id === 'string' &&
      item.id.trim() &&
      item.id.length <= WORKSPACE_LIMITS.MAX_SCOPE_PART_LENGTH &&
      typeof item.name === 'string' &&
      item.name.trim() &&
      typeof item.createdAt === 'string' &&
      Number.isFinite(Date.parse(item.createdAt)) &&
      typeof item.updatedAt === 'string' &&
      Number.isFinite(Date.parse(item.updatedAt)),
  );
}

function catalogKey(userId: string): string {
  return `${CATALOG_PREFIX}${userId}`;
}

function activeKey(userId: string): string {
  return `${ACTIVE_PREFIX}${userId}`;
}

function writeCatalog(userId: string, items: readonly UserWorkspace[]): void {
  localStorage.setItem(catalogKey(userId), JSON.stringify(items));
}

export function loadUserWorkspaces(userId: string): UserWorkspace[] {
  const normalizedUserId = userId.trim();
  if (!normalizedUserId) return [];
  try {
    const parsed = JSON.parse(localStorage.getItem(catalogKey(normalizedUserId)) ?? '[]') as unknown;
    const items = Array.isArray(parsed) ? parsed.filter(isWorkspace).slice(0, MAX_WORKSPACES) : [];
    if (items.length > 0) return items;

    // Preserve the browser workspace created by the previous single-workspace implementation.
    const legacyId = localStorage.getItem(`${LEGACY_PREFIX}${normalizedUserId}`)?.trim();
    const now = new Date().toISOString();
    const initial: UserWorkspace = {
      id: legacyId || createWorkspaceId(),
      name: DEFAULT_NAME,
      createdAt: now,
      updatedAt: now,
    };
    writeCatalog(normalizedUserId, [initial]);
    localStorage.setItem(activeKey(normalizedUserId), initial.id);
    return [initial];
  } catch {
    const now = new Date().toISOString();
    return [{ id: createWorkspaceId(), name: DEFAULT_NAME, createdAt: now, updatedAt: now }];
  }
}

export function loadActiveWorkspaceId(userId: string, items: readonly UserWorkspace[]): string {
  try {
    const stored = localStorage.getItem(activeKey(userId))?.trim();
    return items.some((item) => item.id === stored) ? stored! : items[0]?.id ?? '';
  } catch {
    return items[0]?.id ?? '';
  }
}

export function selectUserWorkspace(userId: string, workspaceId: string): void {
  localStorage.setItem(activeKey(userId), workspaceId);
}

export function createUserWorkspace(
  userId: string,
  items: readonly UserWorkspace[],
  requestedName: string,
): UserWorkspace[] {
  if (items.length >= MAX_WORKSPACES) {
    throw new WorkspaceError('WORKSPACE_COUNT_LIMIT', `每个用户最多创建 ${MAX_WORKSPACES} 个工作区`);
  }
  const name = normalizeWorkspaceName(requestedName);
  if (items.some((item) => item.name === name)) {
    throw new WorkspaceError('DUPLICATE_WORKSPACE_NAME', '已存在同名工作区');
  }
  const now = new Date().toISOString();
  const next = [...items, { id: createWorkspaceId(), name, createdAt: now, updatedAt: now }];
  writeCatalog(userId, next);
  selectUserWorkspace(userId, next[next.length - 1].id);
  return next;
}

export function renameUserWorkspace(
  userId: string,
  items: readonly UserWorkspace[],
  workspaceId: string,
  requestedName: string,
): UserWorkspace[] {
  const name = normalizeWorkspaceName(requestedName);
  if (items.some((item) => item.id !== workspaceId && item.name === name)) {
    throw new WorkspaceError('DUPLICATE_WORKSPACE_NAME', '已存在同名工作区');
  }
  const next = items.map((item) =>
    item.id === workspaceId
      ? { ...item, name, updatedAt: new Date().toISOString() }
      : item,
  );
  writeCatalog(userId, next);
  return next;
}

export function deleteUserWorkspace(
  userId: string,
  items: readonly UserWorkspace[],
  workspaceId: string,
): UserWorkspace[] {
  if (items.length <= 1) {
    throw new WorkspaceError('LAST_WORKSPACE', '至少保留一个工作区');
  }
  if (!items.some((item) => item.id === workspaceId)) {
    throw new WorkspaceError('WORKSPACE_NOT_FOUND', '工作区不存在');
  }
  const next = items.filter((item) => item.id !== workspaceId);
  writeCatalog(userId, next);
  const stored = localStorage.getItem(activeKey(userId))?.trim();
  if (!stored || stored === workspaceId || !next.some((item) => item.id === stored)) {
    selectUserWorkspace(userId, next[0].id);
  }
  return next;
}
