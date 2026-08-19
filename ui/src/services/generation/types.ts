export type GenerationTarget = 'AGENT' | 'TEAM';

export interface GenerationDraftResponse {
  target: GenerationTarget;
  name: string;
  summary: string;
  confidence: number;
  draft: Record<string, unknown>;
  matchedResourceIds: string[];
  recommendedMarketplaceResources?: string[];
  suggestions: string[];
  status: 'READY' | 'NEEDS_CONFIGURATION' | 'INVALID';
  missingFields: string[];
  matchReasons: string[];
}
