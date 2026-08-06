import { describe, expect, it } from 'vitest';
import {
  assertAllowedMemoryPath,
  buildConversationSummaryPath,
  buildLongTermMemoryPath,
  isAllowedMemoryPath,
} from '../paths';

describe('UserScopedMemoryPathTest', () => {
  it('builds long-term and summary paths for valid ids', () => {
    expect(buildLongTermMemoryPath('user-a')).toBe(
      '/memory/v1/users/user-a/长期记忆.md',
    );
    expect(buildConversationSummaryPath('user-a', 'conv-1')).toBe(
      '/memory/v1/users/user-a/conversations/conv-1/对话摘要.md',
    );
  });

  it('rejects empty, dot, traversal and backslash ids', () => {
    expect(() => buildLongTermMemoryPath('')).toThrow();
    expect(() => buildLongTermMemoryPath('.')).toThrow();
    expect(() => buildLongTermMemoryPath('..')).toThrow();
    expect(() => buildLongTermMemoryPath('a/b')).toThrow();
    expect(() => buildLongTermMemoryPath('a\\b')).toThrow();
    expect(() => buildConversationSummaryPath('user', '..')).toThrow();
    expect(() => buildConversationSummaryPath('user', 'x/../y')).toThrow();
  });

  it('isolates different userId path prefixes', () => {
    const a = buildLongTermMemoryPath('alice');
    const b = buildLongTermMemoryPath('bob');
    expect(a.includes('/users/alice/')).toBe(true);
    expect(b.includes('/users/bob/')).toBe(true);
    expect(a).not.toBe(b);
    expect(isAllowedMemoryPath(a)).toBe(true);
    expect(isAllowedMemoryPath('/memory/v1/users/alice/../bob/长期记忆.md')).toBe(
      false,
    );
    expect(() => assertAllowedMemoryPath('/tmp/evil.md')).toThrow();
  });
});
