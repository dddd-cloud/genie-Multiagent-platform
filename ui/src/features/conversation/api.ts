import type {
  CreateConversationRequest,
  UpdateConversationRequest,
  ConversationResponse,
  ConversationListItem,
  ConversationMessageResponse,
  PageResponse,
} from '@/contracts';
import { requestMvp } from '@/services/mvp';

const BASE = '/api/v1/conversations';

export function createConversation(title?: string | null) {
  const data: CreateConversationRequest = { title: title ?? null };
  return requestMvp<ConversationResponse>({
    method: 'POST',
    url: BASE,
    data,
  });
}

export function listConversations(page: number, pageSize: number) {
  return requestMvp<PageResponse<ConversationListItem>>({
    method: 'GET',
    url: BASE,
    params: { page, pageSize },
  });
}

export function getConversation(id: string) {
  return requestMvp<ConversationResponse>({
    method: 'GET',
    url: `${BASE}/${id}`,
  });
}

export function getMessages(id: string) {
  return requestMvp<ConversationMessageResponse[]>({
    method: 'GET',
    url: `${BASE}/${id}/messages`,
  });
}

export function updateConversation(id: string, title: string) {
  const data: UpdateConversationRequest = { title };
  return requestMvp<ConversationResponse>({
    method: 'PATCH',
    url: `${BASE}/${id}`,
    data,
  });
}

export function deleteConversation(id: string) {
  return requestMvp<null>({
    method: 'DELETE',
    url: `${BASE}/${id}`,
  });
}
