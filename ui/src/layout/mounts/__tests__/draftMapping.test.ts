import { describe, expect, it } from 'vitest';
import { emptyAgentFormState } from '@/features/phase2/agents/agentFormModel';
import {
  mapDraftToAgentForm,
  mapDraftToTeamForm,
} from '@/layout/mounts/draftMapping';

describe('draftMapping', () => {
  it('renames skills to skillIds and drops marketplace extras', () => {
    const form = mapDraftToAgentForm({
      name: 'CSV Agent',
      description: 'analyze csv',
      skills: ['skill-a'],
      recommendedMarketplaceResources: ['res-1'],
    });
    expect(form.name).toBe('CSV Agent');
    expect(form.skillIds).toEqual(['skill-a']);
    expect(form).toEqual({
      ...emptyAgentFormState(),
      name: 'CSV Agent',
      description: 'analyze csv',
      skillIds: ['skill-a'],
    });
  });

  it('keeps team master/members pending when the draft left them empty', () => {
    const form = mapDraftToTeamForm({
      name: 'Research Team',
      description: 'two agents',
      masterAgentId: null,
      memberAgentIds: [],
    });
    expect(form.name).toBe('Research Team');
    expect(form.masterAgentId).toBeNull();
    expect(form.memberAgentIds).toEqual([]);
  });
});
