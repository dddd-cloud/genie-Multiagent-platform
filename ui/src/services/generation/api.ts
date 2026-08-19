import { phase2Post } from '@/services/phase2/client';
import type { GenerationDraftResponse, GenerationTarget } from './types';

export function generateDraft(prompt: string, target?: GenerationTarget) {
  return phase2Post<GenerationDraftResponse>('/api/v2/generation/drafts', { prompt, target });
}
