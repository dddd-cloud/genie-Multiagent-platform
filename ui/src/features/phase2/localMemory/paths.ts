const LONG_TERM_SUFFIX = '/长期记忆.md';
const SUMMARY_SUFFIX = '/对话摘要.md';

function assertValidPathSegment(value: string, label: string): void {
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Invalid ${label}: empty`);
  }
  if (value === '.' || value === '..') {
    throw new Error(`Invalid ${label}: reserved segment`);
  }
  if (value.includes('\\') || value.includes('/') || value.includes('\0')) {
    throw new Error(`Invalid ${label}: path separator`);
  }
  if (value.includes('..')) {
    throw new Error(`Invalid ${label}: path traversal`);
  }
}

export function buildLongTermMemoryPath(userId: string): string {
  assertValidPathSegment(userId, 'userId');
  return `/memory/v1/users/${userId}${LONG_TERM_SUFFIX}`;
}

export function buildConversationSummaryPath(
  userId: string,
  conversationId: string,
): string {
  assertValidPathSegment(userId, 'userId');
  assertValidPathSegment(conversationId, 'conversationId');
  return `/memory/v1/users/${userId}/conversations/${conversationId}${SUMMARY_SUFFIX}`;
}

export function isAllowedMemoryPath(path: string): boolean {
  if (typeof path !== 'string' || path.length === 0) {
    return false;
  }
  if (path.includes('\\') || path.includes('\0') || path.includes('..')) {
    return false;
  }
  const longTerm = /^\/memory\/v1\/users\/([^/]+)\/长期记忆\.md$/;
  const summary =
    /^\/memory\/v1\/users\/([^/]+)\/conversations\/([^/]+)\/对话摘要\.md$/;
  const lt = longTerm.exec(path);
  if (lt) {
    try {
      assertValidPathSegment(lt[1], 'userId');
      return true;
    } catch {
      return false;
    }
  }
  const sm = summary.exec(path);
  if (sm) {
    try {
      assertValidPathSegment(sm[1], 'userId');
      assertValidPathSegment(sm[2], 'conversationId');
      return true;
    } catch {
      return false;
    }
  }
  return false;
}

export function assertAllowedMemoryPath(path: string): void {
  if (!isAllowedMemoryPath(path)) {
    throw new Error(`Disallowed memory path: ${path}`);
  }
}
