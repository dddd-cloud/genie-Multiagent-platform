import type { AgentPromptMode } from '@/contracts/phase2';
import type { AgentFormState } from '@/features/phase2/agents/agentFormModel';
import { emptyAgentFormState } from '@/features/phase2/agents/agentFormModel';
import type { TeamFormState } from '@/features/phase2/teams/teamFormModel';
import { emptyTeamFormState } from '@/features/phase2/teams/teamFormModel';
import type { GenerationDraftResponse } from '@/services/generation';
import type { MarketplaceDraftResponse } from '@/services/marketplace';

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

function stringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : [];
}

export function mapDraftToAgentForm(draft: Record<string, unknown>): AgentFormState {
  const base = emptyAgentFormState();
  const promptMode: AgentPromptMode =
    draft.promptMode === 'RAW' || draft.promptMode === 'STRUCTURED'
      ? draft.promptMode
      : base.promptMode;
  const promptConfigText =
    draft.promptConfig && typeof draft.promptConfig === 'object'
      ? JSON.stringify(draft.promptConfig, null, 2)
      : base.promptConfigText;
  const skillIds = stringList(draft.skillIds).length
    ? stringList(draft.skillIds)
    : stringList(draft.skills);
  return {
    ...base,
    name: stringValue(draft.name) ?? base.name,
    description: stringValue(draft.description) ?? base.description,
    promptMode,
    promptConfigText,
    systemPrompt: stringValue(draft.systemPrompt) ?? base.systemPrompt,
    modelName: stringValue(draft.modelName) ?? base.modelName,
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

export function generationDraftTarget(
  result: GenerationDraftResponse,
): 'AGENT' | 'TEAM' {
  return result.target === 'TEAM' ? 'TEAM' : 'AGENT';
}
