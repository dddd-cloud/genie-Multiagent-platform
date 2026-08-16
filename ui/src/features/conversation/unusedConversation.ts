import type { ConversationListItem } from '@/contracts';

export function isUnusedConversation(
  item: Pick<ConversationListItem, 'lastMessageAt'>,
): boolean {
  return item.lastMessageAt == null;
}

export function unusedConversationIds(
  items: readonly Pick<ConversationListItem, 'id' | 'lastMessageAt'>[],
  exceptId?: string | null,
): string[] {
  return items
    .filter((item) => isUnusedConversation(item) && item.id !== exceptId)
    .map((item) => item.id);
}

export type NewConversationAction =
  | { type: 'noop' }
  | { type: 'reuse'; id: string }
  | { type: 'create' };

export function resolveNewConversationAction(
  items: readonly Pick<ConversationListItem, 'id' | 'lastMessageAt' | 'privacyMode'>[],
  currentId?: string | null,
  privacyMode = false,
): NewConversationAction {
  const matchesPrivacy = (
    item: Pick<ConversationListItem, 'privacyMode'>,
  ) => item.privacyMode === true === privacyMode;
  const current = currentId
    ? items.find((item) => item.id === currentId)
    : undefined;
  if (current && isUnusedConversation(current) && matchesPrivacy(current)) {
    return { type: 'noop' };
  }
  const unused = items
    .filter(
      (item) =>
        isUnusedConversation(item) &&
        matchesPrivacy(item) &&
        item.id !== currentId,
    )
    .map((item) => item.id);
  if (unused.length > 0) {
    return { type: 'reuse', id: unused[0] };
  }
  return { type: 'create' };
}
