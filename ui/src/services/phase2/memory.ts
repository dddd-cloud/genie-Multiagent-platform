import type {
  ConversationSummaryResponse,
  MemoryFileResponse,
  MemoryMarkdownWriteRequest,
  MemoryPatchResponse,
  MemoryStatusResponse,
  MemorySummaryIndexResponse,
} from '@/contracts/phase2';
import { phase2Delete, phase2Get, phase2Post, phase2Put } from './client';
import type {
  MemoryAnalyzeTurnRequest,
  MemorySummarizeRequest,
} from './internalTypes';

export function analyzeTurn(
  body: MemoryAnalyzeTurnRequest,
  signal?: AbortSignal,
) {
  return phase2Post<MemoryPatchResponse>(
    '/api/v2/memory/analyze-turn',
    body,
    signal,
  );
}

export function summarizeConversation(
  body: MemorySummarizeRequest,
  signal?: AbortSignal,
) {
  return phase2Post<ConversationSummaryResponse>(
    '/api/v2/memory/summarize-conversation',
    body,
    signal,
  );
}

export function getMemoryStatus(signal?: AbortSignal) {
  return phase2Get<MemoryStatusResponse>('/api/v2/memory/status', undefined, signal);
}

export function getLongTermMemory(signal?: AbortSignal) {
  return phase2Get<MemoryFileResponse>('/api/v2/memory/long-term', undefined, signal);
}

export function putLongTermMemory(
  body: MemoryMarkdownWriteRequest,
  signal?: AbortSignal,
) {
  return phase2Put<MemoryFileResponse>('/api/v2/memory/long-term', body, signal);
}

export function deleteLongTermMemory(signal?: AbortSignal) {
  return phase2Delete<void>('/api/v2/memory/long-term', undefined, signal);
}

export function listMemorySummaries(signal?: AbortSignal) {
  return phase2Get<MemorySummaryIndexResponse>(
    '/api/v2/memory/summaries',
    undefined,
    signal,
  );
}

export function getConversationSummary(
  conversationId: string,
  signal?: AbortSignal,
) {
  return phase2Get<MemoryFileResponse>(
    `/api/v2/memory/conversations/${encodeURIComponent(conversationId)}/summary`,
    undefined,
    signal,
  );
}

export function putConversationSummary(
  conversationId: string,
  body: MemoryMarkdownWriteRequest,
  signal?: AbortSignal,
) {
  return phase2Put<MemoryFileResponse>(
    `/api/v2/memory/conversations/${encodeURIComponent(conversationId)}/summary`,
    body,
    signal,
  );
}

export function deleteConversationSummary(
  conversationId: string,
  signal?: AbortSignal,
) {
  return phase2Delete<void>(
    `/api/v2/memory/conversations/${encodeURIComponent(conversationId)}/summary`,
    undefined,
    signal,
  );
}
