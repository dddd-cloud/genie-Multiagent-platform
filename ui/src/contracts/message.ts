export const CONVERSATION_MESSAGE_ROLES =
  ['USER', 'ASSISTANT'] as const;

export type ConversationMessageRole =
  (typeof CONVERSATION_MESSAGE_ROLES)[number];

export const CONVERSATION_MESSAGE_STATUSES =
  ['PENDING', 'STREAMING', 'COMPLETED', 'FAILED', 'INTERRUPTED'] as const;

export type ConversationMessageStatus =
  (typeof CONVERSATION_MESSAGE_STATUSES)[number];

export interface ConversationMessageResponse {
  id: string;
  turnNo: number;
  role: ConversationMessageRole;
  status: ConversationMessageStatus;
  requestId: string;
  content: string | null;
  streamSnapshot: string | null;
  payloadVersion: number;
  deepThink: number | null;
  outputStyle: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}
