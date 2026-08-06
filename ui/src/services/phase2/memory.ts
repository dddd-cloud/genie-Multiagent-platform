import type {
  ConversationSummaryResponse,
  MemoryPatchResponse,
} from '@/contracts/phase2';
import { phase2Post } from './client';
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
