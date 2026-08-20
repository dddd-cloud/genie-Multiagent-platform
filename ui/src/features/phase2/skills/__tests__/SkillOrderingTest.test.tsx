import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useState } from 'react';
import AgentForm from '../../agents/AgentForm';
import { emptyAgentFormState, type AgentFormState } from '../../agents/agentFormModel';
import type { Phase2SkillResponse } from '@/contracts/phase2';

const skills: Phase2SkillResponse[] = [
  {
    id: 'skill-a',
    name: 'Skill A',
    description: '',
    instruction: 'a',
    outputRequirement: '',
    status: 'ENABLED',
    version: 1,
    capabilityKeys: [],
    createdAt: '',
    updatedAt: '',
  },
  {
    id: 'skill-b',
    name: 'Skill B',
    description: '',
    instruction: 'b',
    outputRequirement: '',
    status: 'ENABLED',
    version: 1,
    capabilityKeys: [],
    createdAt: '',
    updatedAt: '',
  },
  {
    id: 'skill-c',
    name: 'Skill C',
    description: '',
    instruction: 'c',
    outputRequirement: '',
    status: 'DISABLED',
    version: 1,
    capabilityKeys: [],
    createdAt: '',
    updatedAt: '',
  },
];

function SkillBindHarness() {
  const [value, setValue] = useState<AgentFormState>({
    ...emptyAgentFormState(),
    name: 'Bound',
    skillIds: ['skill-a', 'skill-b'],
  });
  return (
    <div>
      <div data-testid="selected">{value.skillIds.join(',')}</div>
      <AgentForm value={value} onChange={setValue} skills={skills} capabilities={[]} />
    </div>
  );
}

describe('SkillOrderingTest', () => {
  it('binds skills without exposing order controls', () => {
    render(<SkillBindHarness />);
    expect(screen.getByTestId('selected').textContent).toBe('skill-a,skill-b');
    expect(screen.getByTestId('agent-skills')).toBeTruthy();
    expect(screen.queryByTestId('agent-skill-up-skill-a')).toBeNull();
    expect(screen.queryByTestId('agent-skill-down-skill-a')).toBeNull();
    expect(screen.queryByText('Skill（有序）')).toBeNull();
  });

  it('lists enabled skill names for selection', () => {
    render(<SkillBindHarness />);
    expect(screen.getByText('Skill A')).toBeTruthy();
    expect(screen.getByText('Skill B')).toBeTruthy();
  });
});
