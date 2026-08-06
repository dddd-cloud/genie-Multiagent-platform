import querySSE, { type SSEConfig, type SseHandle } from '@/utils/querySSE';

const PHASE2_SSE_URL = '/web/api/v2/gpt/queryAgentStreamIncr';

export function queryPhase2SSE(config: SSEConfig): SseHandle {
  return querySSE(config, PHASE2_SSE_URL);
}
