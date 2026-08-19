import { describe, expect, it } from 'vitest';
import {
  clearConversationDraft,
  peekConversationDraft,
  stashConversationDraft,
} from '../pendingConversationDraft';

describe('pendingConversationDraft', () => {
  it('keeps a first-send draft until it is cleared', () => {
    stashConversationDraft('c1', {
      requestId: 'r1',
      inputInfo: { message: 'hello', deepThink: false },
    });
    expect(peekConversationDraft('c1')?.inputInfo.message).toBe('hello');
    clearConversationDraft('c1');
    expect(peekConversationDraft('c1')).toBeNull();
  });
});
