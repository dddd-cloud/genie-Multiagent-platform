import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import type { ExecutionMode, Phase2AgentResponse } from '@/contracts';
import type { Phase2TeamResponse } from '@/contracts/phase2';
import ExecutionModeSelector from '../ExecutionModeSelector';

vi.mock('@/services/phase2/agents', () => ({
  listAgents: vi.fn(),
}));

vi.mock('@/services/phase2/teams', () => ({
  listTeams: vi.fn(),
}));

import { listAgents } from '@/services/phase2/agents';
import { listTeams } from '@/services/phase2/teams';

const onlineAgent = (id: string, name: string): Phase2AgentResponse => ({
  id,
  name,
  description: '',
  promptMode: 'STRUCTURED',
  promptConfig: null,
  systemPrompt: '',
  modelName: null,
  status: 'ONLINE',
  version: 1,
  skillIds: [],
  capabilityKeys: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

const team = (id: string, name: string): Phase2TeamResponse => ({
  id,
  name,
  description: '',
  masterAgentId: 'a1',
  masterAgentName: '市场研究员',
  memberAgentIds: ['a2'],
  version: 1,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

describe('ExecutionModeSelectorTest', () => {
  beforeEach(() => {
    vi.mocked(listAgents).mockResolvedValue([
      onlineAgent('a1', '市场研究员'),
      onlineAgent('a2', '竞品研究员'),
    ]);
    vi.mocked(listTeams).mockResolvedValue([]);
  });

  it('defaults to AUTO and keeps modes collapsed', () => {
    render(<ExecutionModeSelector />);
    expect(screen.getByTestId('execution-mode-selector')).toHaveTextContent(
      'Auto',
    );
    expect(screen.queryByRole('option', { name: 'Solo' })).toBeNull();
    expect(screen.queryByRole('option', { name: 'Ensemble' })).toBeNull();
    expect(screen.queryByTestId('allowed-agent-selector')).toBeNull();
  });

  it('expands three modes when the trigger is clicked', () => {
    render(<ExecutionModeSelector value="AUTO" />);
    fireEvent.click(screen.getByTestId('execution-mode-selector'));
    expect(screen.getByRole('option', { name: 'Auto' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Solo' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Ensemble' })).toBeTruthy();
    expect(screen.queryByText('市场研究员')).toBeNull();
  });

  it('notifies onChange when selecting DIRECT', () => {
    const onChange = vi.fn();
    render(<ExecutionModeSelector value="AUTO" onChange={onChange} />);
    fireEvent.click(screen.getByTestId('execution-mode-selector'));
    fireEvent.click(screen.getByRole('option', { name: 'Solo' }));
    expect(onChange).toHaveBeenCalledWith('DIRECT');
  });

  it('opens a scrollable single-agent picker in Solo', async () => {
    const onAgents = vi.fn();
    render(
      <ExecutionModeSelector
        value="DIRECT"
        onAllowedAgentIdsChange={onAgents}
      />,
    );
    expect(screen.getByTestId('solo-agent-selector')).toHaveTextContent(
      '选择智能体',
    );
    fireEvent.click(screen.getByTestId('solo-agent-selector'));
    await waitFor(() => {
      expect(screen.getByTestId('solo-agent-menu')).toBeTruthy();
    });
    expect(screen.getByTestId('solo-agent-menu').firstElementChild).toHaveClass(
      'max-h-[216px]',
    );
    fireEvent.click(screen.getByTestId('solo-agent-option-a1'));
    expect(onAgents).toHaveBeenCalledWith(['a1']);
  });

  it('selects all ONLINE agents after choosing ORCHESTRATED without listing them in the mode menu', async () => {
    const onAgents = vi.fn();
    const Harness = () => {
      const [mode, setMode] = useState<ExecutionMode>('AUTO');
      return (
        <ExecutionModeSelector
          value={mode}
          onChange={setMode}
          onAllowedAgentIdsChange={onAgents}
        />
      );
    };
    render(<Harness />);
    fireEvent.click(screen.getByTestId('execution-mode-selector'));
    fireEvent.click(screen.getByRole('option', { name: 'Ensemble' }));
    await waitFor(() => {
      expect(onAgents).toHaveBeenCalledWith(['a1', 'a2']);
    });
    expect(screen.getByTestId('execution-mode-selector')).toHaveTextContent(
      'Ensemble',
    );
    expect(screen.queryByText('市场研究员')).toBeNull();
    expect(screen.getByTestId('allowed-agent-selector')).toHaveTextContent(
      'All',
    );
  });

  it('opens the agent picker beside Ensemble and can deselect', async () => {
    const onAgents = vi.fn();
    render(
      <ExecutionModeSelector
        value="ORCHESTRATED"
        allowedAgentIds={['a1', 'a2']}
        onAllowedAgentIdsChange={onAgents}
      />,
    );
    fireEvent.click(screen.getByTestId('allowed-agent-selector'));
    await waitFor(() => {
      expect(screen.getByText('市场研究员')).toBeTruthy();
    });
    fireEvent.click(screen.getByText('市场研究员'));
    expect(onAgents).toHaveBeenCalledWith(['a2']);
  });

  it('clears every agent from the picker', async () => {
    const onAgents = vi.fn();
    render(
      <ExecutionModeSelector
        value="ORCHESTRATED"
        allowedAgentIds={['a1', 'a2']}
        onAllowedAgentIdsChange={onAgents}
      />,
    );
    fireEvent.click(screen.getByTestId('allowed-agent-selector'));
    await waitFor(() => {
      expect(screen.getByTestId('allowed-agent-clear')).toBeTruthy();
    });
    fireEvent.click(screen.getByTestId('allowed-agent-clear'));
    expect(onAgents).toHaveBeenCalledWith([]);
  });

  it('shows 未选择 after clearing, then All after selecting all again', async () => {
    const Harness = () => {
      const [ids, setIds] = useState<string[]>(['a1', 'a2']);
      return (
        <ExecutionModeSelector
          value="ORCHESTRATED"
          allowedAgentIds={ids}
          onAllowedAgentIdsChange={setIds}
        />
      );
    };
    render(<Harness />);
    fireEvent.click(screen.getByTestId('allowed-agent-selector'));
    await waitFor(() => {
      expect(screen.getByTestId('allowed-agent-clear')).toBeTruthy();
    });
    fireEvent.click(screen.getByTestId('allowed-agent-clear'));
    expect(screen.getByTestId('allowed-agent-selector')).toHaveTextContent(
      '未选择',
    );
    fireEvent.click(screen.getByTestId('allowed-agent-all'));
    expect(screen.getByTestId('allowed-agent-selector')).toHaveTextContent(
      'All',
    );
  });

  it('lists teams instead of agents when teams exist', async () => {
    vi.mocked(listTeams).mockResolvedValue([team('t1', '调研小组')]);
    const onTeam = vi.fn();
    const onAgents = vi.fn();
    render(
      <ExecutionModeSelector
        value="ORCHESTRATED"
        onTeamIdChange={onTeam}
        onAllowedAgentIdsChange={onAgents}
      />,
    );
    await waitFor(() => {
      expect(screen.getByTestId('team-selector')).toHaveTextContent('选择团队');
    });
    expect(screen.queryByTestId('allowed-agent-selector')).toBeNull();

    fireEvent.click(screen.getByTestId('team-selector'));
    fireEvent.click(screen.getByTestId('team-option-t1'));
    expect(onTeam).toHaveBeenCalledWith('t1');
    expect(onAgents).toHaveBeenCalledWith([]);
  });

  it('falls back to the agent picker via 自定义 and back again', async () => {
    vi.mocked(listTeams).mockResolvedValue([team('t1', '调研小组')]);
    const onTeam = vi.fn();
    const onAgents = vi.fn();
    render(
      <ExecutionModeSelector
        value="ORCHESTRATED"
        teamId="t1"
        onTeamIdChange={onTeam}
        onAllowedAgentIdsChange={onAgents}
      />,
    );
    await waitFor(() => {
      expect(screen.getByTestId('team-selector')).toHaveTextContent('调研小组');
    });

    fireEvent.click(screen.getByTestId('team-selector'));
    fireEvent.click(screen.getByTestId('team-custom'));
    expect(onTeam).toHaveBeenCalledWith(null);
    expect(onAgents).toHaveBeenCalledWith(['a1', 'a2']);
    expect(screen.getByTestId('allowed-agent-selector')).toBeTruthy();

    // 自定义 keeps the panel open, so 返回团队 is reachable right away.
    fireEvent.click(screen.getByTestId('team-back'));
    await waitFor(() => {
      expect(screen.getByTestId('team-selector')).toBeTruthy();
    });
  });

  it('does not auto-select agents while a team branch is active', async () => {
    vi.mocked(listTeams).mockResolvedValue([team('t1', '调研小组')]);
    const onAgents = vi.fn();
    const Harness = () => {
      const [mode, setMode] = useState<ExecutionMode>('AUTO');
      return (
        <ExecutionModeSelector
          value={mode}
          onChange={setMode}
          onAllowedAgentIdsChange={onAgents}
        />
      );
    };
    render(<Harness />);
    await waitFor(() => {
      expect(vi.mocked(listTeams)).toHaveBeenCalled();
    });
    fireEvent.click(screen.getByTestId('execution-mode-selector'));
    fireEvent.click(screen.getByRole('option', { name: 'Ensemble' }));
    await waitFor(() => {
      expect(screen.getByTestId('team-selector')).toBeTruthy();
    });
    expect(onAgents).not.toHaveBeenCalledWith(['a1', 'a2']);
  });
});
