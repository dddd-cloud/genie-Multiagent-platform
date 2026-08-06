import type {
  ConversationMessageStatus,
  ExecutionMode,
  OutputStyle,
} from '@/contracts';
import type { OrchestrationUiState } from '@/features/phase2/orchestration/types';

export interface PersistedChatItem extends CHAT.ChatItem {
  deepThink: boolean;
  outputStyle: OutputStyle;
  persistedStatus?: ConversationMessageStatus;
  errorCode?: string | null;
  errorMessage?: string | null;
  snapshotTruncated?: boolean;
  orchestration?: OrchestrationUiState;
  orchestrationRecoveryWarning?: boolean;
}

export interface ConversationDraft {
  requestId: string;
  inputInfo: CHAT.TInputInfo;
  productType?: string;
  /** Default AUTO when Phase2 is enabled. */
  executionMode?: ExecutionMode;
  allowedAgentIds?: string[];
}
