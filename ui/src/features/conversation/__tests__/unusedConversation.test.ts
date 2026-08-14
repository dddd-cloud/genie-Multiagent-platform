import { describe, expect, it } from 'vitest';
import type { ConversationListItem } from '@/contracts';
import {
  isUnusedConversation,
  resolveNewConversationAction,
  unusedConversationIds,
} from '../unusedConversation';

function item(
  id: string,
  lastMessageAt: string | null,
): ConversationListItem {
  return {
    id,
    title: id,
    lastMessageAt,
    createdAt: '2026-08-14T00:00:00Z',
    updatedAt: '2026-08-14T00:00:00Z',
    lastMessagePreview: null,
  };
}

describe('unusedConversation', () => {
  it('treats missing lastMessageAt as unused', () => {
    expect(isUnusedConversation(item('a', null))).toBe(true);
    expect(isUnusedConversation(item('b', '2026-08-14T01:00:00Z'))).toBe(
      false,
    );
  });

  it('lists unused ids except the kept conversation', () => {
    const items = [
      item('empty-1', null),
      item('used', '2026-08-14T01:00:00Z'),
      item('empty-2', null),
    ];
    expect(unusedConversationIds(items)).toEqual(['empty-1', 'empty-2']);
    expect(unusedConversationIds(items, 'empty-1')).toEqual(['empty-2']);
  });

  it('stays on the current unused conversation', () => {
    expect(
      resolveNewConversationAction(
        [item('draft', null), item('used', '2026-08-14T01:00:00Z')],
        'draft',
      ),
    ).toEqual({ type: 'noop' });
  });

  it('reuses an existing unused conversation instead of creating another', () => {
    expect(
      resolveNewConversationAction(
        [item('used', '2026-08-14T01:00:00Z'), item('draft', null)],
        'used',
      ),
    ).toEqual({ type: 'reuse', id: 'draft' });
  });

  it('creates when every conversation already has messages', () => {
    expect(
      resolveNewConversationAction(
        [item('used', '2026-08-14T01:00:00Z')],
        'used',
      ),
    ).toEqual({ type: 'create' });
  });
});
