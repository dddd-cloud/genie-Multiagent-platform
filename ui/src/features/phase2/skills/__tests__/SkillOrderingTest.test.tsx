import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import AgentForm from '../../agents/AgentForm';
import {
  emptyAgentFormState,
  moveSkillId,
  type AgentFormState,
} from '../../agents/agentFormModel';
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

function SkillOrderHarness() {
  const [value, setValue] = useState<AgentFormState>({
    ...emptyAgentFormState(),
    name: 'Ordered',
    skillIds: ['skill-a', 'skill-b', 'skill-c'],
  });
  return (
    <div>
      <div data-testid="order">{value.skillIds.join(',')}</div>
      <AgentForm
        value={value}
        onChange={setValue}
        models={[]}
        skills={skills}
        capabilities={[]}
      />
    </div>
  );
}

describe('SkillOrderingTest', () => {
  it('moves skillIds up and down in AgentForm without drag-drop', () => {
    expect(moveSkillId(['a', 'b', 'c'], 2, 'up')).toEqual(['a', 'c', 'b']);
    expect(moveSkillId(['a', 'b', 'c'], 0, 'down')).toEqual(['b', 'a', 'c']);
    expect(moveSkillId(['a', 'b'], 0, 'up')).toEqual(['a', 'b']);

    render(<SkillOrderHarness />);
    expect(screen.getByTestId('order').textContent).toBe(
      'skill-a,skill-b,skill-c',
    );

    fireEvent.click(screen.getByTestId('agent-skill-up-skill-c'));
    expect(screen.getByTestId('order').textContent).toBe(
      'skill-a,skill-c,skill-b',
    );

    fireEvent.click(screen.getByTestId('agent-skill-down-skill-a'));
    expect(screen.getByTestId('order').textContent).toBe(
      'skill-c,skill-a,skill-b',
    );
  });

  it('lists skills for enable/disable display (status tags via names)', () => {
    render(<SkillOrderHarness />);
    expect(screen.getByText('Skill A')).toBeTruthy();
    expect(screen.getByText('Skill B')).toBeTruthy();
    expect(screen.getByText('Skill C')).toBeTruthy();
    expect(screen.getByTestId('agent-skill-list')).toBeTruthy();
  });
});
