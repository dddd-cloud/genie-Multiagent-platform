import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import type { Phase2AgentResponse } from '@/contracts/phase2';
import TeamForm from '../TeamForm';
import {
  emptyTeamFormState,
  validateTeamForm,
  type TeamFormState,
} from '../teamFormModel';

const agent = (
  id: string,
  name: string,
  status: Phase2AgentResponse['status'],
): Phase2AgentResponse => ({
  id,
  name,
  description: '',
  promptMode: 'RAW',
  promptConfig: null,
  systemPrompt: '',
  modelName: null,
  status,
  version: 1,
  skillIds: [],
  capabilityKeys: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

const AGENTS = [
  agent('a1', '主管 Agent', 'ONLINE'),
  agent('a2', '研究 Agent', 'ONLINE'),
  agent('a3', '草稿 Agent', 'DRAFT'),
];

function filledState(overrides: Partial<TeamFormState> = {}): TeamFormState {
  return {
    ...emptyTeamFormState(),
    name: '调研小组',
    description: '负责市场调研',
    masterAgentId: 'a1',
    memberAgentIds: ['a2'],
    ...overrides,
  };
}

function TeamFormHarness({ initial }: { initial: TeamFormState }) {
  const [value, setValue] = useState(initial);
  return <TeamForm value={value} onChange={setValue} agents={AGENTS} />;
}

describe('TeamEditorTest', () => {
  it('renders the form and reports the first missing field', () => {
    render(<TeamFormHarness initial={emptyTeamFormState()} />);
    expect(screen.getByTestId('team-form')).toBeTruthy();
    expect(screen.getByTestId('team-master')).toBeTruthy();
    expect(screen.getByText('尚未选择子 Agent')).toBeTruthy();

    expect(validateTeamForm(emptyTeamFormState())).toBe('请填写团队名称');
    expect(validateTeamForm(filledState({ masterAgentId: null }))).toBe(
      '请选择主 Agent（必须是已上线的 Agent）',
    );
    expect(validateTeamForm(filledState({ memberAgentIds: [] }))).toBe(
      '请至少选择 1 个子 Agent',
    );
    expect(validateTeamForm(filledState({ memberAgentIds: ['a1'] }))).toBe(
      '主 Agent 不能同时作为子 Agent',
    );
    expect(validateTeamForm(filledState())).toBeNull();
  });

  it('lists selected members in order and removes one on click', () => {
    render(
      <TeamFormHarness initial={filledState({ memberAgentIds: ['a2', 'a3'] })} />,
    );
    expect(screen.getByText('研究 Agent')).toBeTruthy();
    expect(screen.getByText('草稿 Agent')).toBeTruthy();
    expect(screen.getByText('未上线')).toBeTruthy();

    const removeButtons = screen
      .getByTestId('team-form')
      .querySelectorAll('button.ant-btn-dangerous');
    fireEvent.click(removeButtons[0]!);
    expect(screen.queryByText('研究 Agent')).toBeNull();
    expect(screen.getByText('草稿 Agent')).toBeTruthy();
  });

  it('notifies onChange when the name is edited', () => {
    const onChange = vi.fn();
    render(
      <TeamForm
        value={emptyTeamFormState()}
        onChange={onChange}
        agents={AGENTS}
      />,
    );
    fireEvent.change(screen.getByTestId('team-name'), {
      target: { value: '增长团队' },
    });
    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls[0]![0].name).toBe('增长团队');
  });
});
