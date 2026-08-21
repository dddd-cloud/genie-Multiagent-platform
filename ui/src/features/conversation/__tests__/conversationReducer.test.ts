import { describe, expect, it } from 'vitest';
import {
  conversationReducer,
  initialConversationListState,
} from '../conversationReducer';
import type { ConversationListItem } from '@/contracts';

function item(
  id: string,
  title = `Conversation ${id}`,
): ConversationListItem {
  return {
    id,
    title,
    privacyMode: false,
    lastMessageAt: null,
    createdAt: '2026-07-21T00:00:00Z',
    updatedAt: '2026-07-21T00:00:00Z',
    lastMessagePreview: null,
  };
}

describe('conversationReducer', () => {
  it('LOAD_SUCCESS replaces items', () => {
    const prev = {
      ...initialConversationListState,
      items: [item('old')],
      loading: true,
    };
    const next = conversationReducer(prev, {
      type: 'LOAD_SUCCESS',
      items: [item('a'), item('b')],
      page: 1,
      hasMore: false,
    });
    expect(next.items.map((i) => i.id)).toEqual(['a', 'b']);
    expect(next.loading).toBe(false);
    expect(next.page).toBe(1);
    expect(next.hasMore).toBe(false);
  });

  it('APPEND_SUCCESS dedupes by id', () => {
    const prev = {
      ...initialConversationListState,
      items: [item('a'), item('b')],
    };
    const next = conversationReducer(prev, {
      type: 'APPEND_SUCCESS',
      items: [item('b', 'dup'), item('c')],
      page: 2,
      hasMore: true,
    });
    expect(next.items.map((i) => i.id)).toEqual(['a', 'b', 'c']);
    expect(next.items.find((i) => i.id === 'b')?.title).toBe('Conversation b');
    expect(next.page).toBe(2);
    expect(next.hasMore).toBe(true);
  });

  it('UPSERT inserts new item at front', () => {
    const prev = {
      ...initialConversationListState,
      items: [item('a')],
    };
    const next = conversationReducer(prev, {
      type: 'UPSERT',
      item: item('b'),
    });
    expect(next.items.map((i) => i.id)).toEqual(['b', 'a']);
  });

  it('UPSERT updates existing item in place', () => {
    const prev = {
      ...initialConversationListState,
      items: [item('a'), item('b')],
    };
    const next = conversationReducer(prev, {
      type: 'UPSERT',
      item: item('a', 'Updated'),
    });
    expect(next.items.map((i) => i.id)).toEqual(['a', 'b']);
    expect(next.items[0].title).toBe('Updated');
  });

  it('UPSERT moves an item to the front when lastMessageAt becomes set', () => {
    const prev = {
      ...initialConversationListState,
      items: [item('a'), item('b')],
    };
    const next = conversationReducer(prev, {
      type: 'UPSERT',
      item: {
        ...item('b'),
        lastMessageAt: '2026-08-21T00:00:00Z',
      },
    });
    expect(next.items.map((i) => i.id)).toEqual(['b', 'a']);
    expect(next.items[0].lastMessageAt).toBe('2026-08-21T00:00:00Z');
  });

  it('REMOVE deletes by id', () => {
    const prev = {
      ...initialConversationListState,
      items: [item('a'), item('b'), item('c')],
    };
    const next = conversationReducer(prev, {
      type: 'REMOVE',
      id: 'b'
    });
    expect(next.items.map((i) => i.id)).toEqual(['a', 'c']);
  });
});
