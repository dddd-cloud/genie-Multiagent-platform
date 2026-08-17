import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import type { ExecutionMode, Phase2AgentResponse } from '@/contracts';
import ExecutionModeSelector from '../ExecutionModeSelector';

vi.mock('@/services/phase2/agents', () => ({
  listAgents: vi.fn(),
}));

import { listAgents } from '@/services/phase2/agents';

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

describe('ExecutionModeSelectorTest', () => {
  beforeEach(() => {
    vi.mocked(listAgents).mockResolvedValue([
      onlineAgent('a1', '市场研究员'),
      onlineAgent('a2', '竞品研究员'),
    ]);
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
});
