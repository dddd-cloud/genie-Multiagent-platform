import type { ConversationDraft } from './types';

const drafts = new Map<string, ConversationDraft>();

export function stashConversationDraft(
  conversationId: string,
  draft: ConversationDraft,
): void {
  drafts.set(conversationId, draft);
}

export function peekConversationDraft(
  conversationId: string | undefined,
): ConversationDraft | null {
  if (!conversationId) {
    return null;
  }
  return drafts.get(conversationId) ?? null;
}

export function clearConversationDraft(conversationId: string): void {
  drafts.delete(conversationId);
}
