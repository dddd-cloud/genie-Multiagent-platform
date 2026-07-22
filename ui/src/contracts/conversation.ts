export interface CreateConversationRequest {
  title?: string | null;
}

export interface UpdateConversationRequest {
  title: string;
}

export interface ConversationResponse {
  id: string;
  title: string;
  lastMessageAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationListItem extends ConversationResponse {
  lastMessagePreview: string | null;
}
