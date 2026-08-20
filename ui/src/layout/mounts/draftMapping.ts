import type { AgentFormState } from '@/features/phase2/agents/agentFormModel';
import { emptyAgentFormState } from '@/features/phase2/agents/agentFormModel';
import type { TeamFormState } from '@/features/phase2/teams/teamFormModel';
import { emptyTeamFormState } from '@/features/phase2/teams/teamFormModel';
import type { MarketplaceDraftResponse } from '@/services/marketplace';

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

function stringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : [];
}

/** Catalog templates use "default"; the model catalog stores that as system default (null). */
function resolveMarketplaceModelName(value: unknown): string | null {
  const name = stringValue(value)?.trim();
  if (!name || name === 'default' || name === 'system-default') {
    return null;
  }
  return name;
}

export function mapDraftToAgentForm(draft: Record<string, unknown>): AgentFormState {
  const base = emptyAgentFormState();
  const skillIds = stringList(draft.skillIds).length
    ? stringList(draft.skillIds)
    : stringList(draft.skills);
  return {
    ...base,
    name: stringValue(draft.name) ?? base.name,
    description: stringValue(draft.description) ?? base.description,
    promptMode: 'RAW',
    promptConfigText: base.promptConfigText,
    systemPrompt: stringValue(draft.systemPrompt) ?? base.systemPrompt,
    modelName: resolveMarketplaceModelName(draft.modelName),
    skillIds,
    capabilityKeys: stringList(draft.capabilityKeys),
  };
}

export function mapDraftToTeamForm(draft: Record<string, unknown>): TeamFormState {
  const base = emptyTeamFormState();
  return {
    ...base,
    name: stringValue(draft.name) ?? base.name,
    description: stringValue(draft.description) ?? base.description,
    masterAgentId: stringValue(draft.masterAgentId) ?? null,
    memberAgentIds: stringList(draft.memberAgentIds),
  };
}

export function marketplaceDraftTarget(
  result: MarketplaceDraftResponse,
): 'AGENT' | 'TEAM' | null {
  if (result.type === 'AGENT') return 'AGENT';
  if (result.type === 'TEAM') return 'TEAM';
  return null;
}
