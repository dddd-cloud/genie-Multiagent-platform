import type { OutputStyle } from '@/contracts';
import type { ConversationMessageStatus } from '@/contracts';

export interface PersistedChatItem extends CHAT.ChatItem {
  deepThink: boolean;
  outputStyle: OutputStyle;
  persistedStatus?: ConversationMessageStatus;
  errorCode?: string | null;
  errorMessage?: string | null;
  snapshotTruncated?: boolean;
}

export interface ConversationDraft {
  requestId: string;
  inputInfo: CHAT.TInputInfo;
  productType?: string;
}
