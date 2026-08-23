export interface CreateConversationRequest {
  title?: string | null;
  privacyMode?: boolean;
  /** Tags the conversation as belonging to a browser workspace; omit for ordinary chat. */
  workspaceId?: string | null;
}

export interface UpdateConversationRequest {
  title?: string;
  privacyMode?: boolean;
}

export interface ConversationResponse {
  id: string;
  title: string;
  privacyMode: boolean;
  /** Present only for a conversation created from the workspace page; absent for ordinary chat. */
  workspaceId?: string | null;
  lastMessageAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationListItem extends ConversationResponse {
  lastMessagePreview: string | null;
}
