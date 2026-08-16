import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Phase2AgentResponse } from '@/contracts/phase2';
import * as agentService from '@/services/phase2/agents';
import * as skillService from '@/services/phase2/skills';
import AgentEditorPage from '../AgentEditorPage';

vi.mock('@/services/phase2/agents', () => ({
  createAgent: vi.fn(),
  deleteAgent: vi.fn(),
  getAgent: vi.fn(),
  listModels: vi.fn(),
  listToolCapabilities: vi.fn(),
  offlineAgent: vi.fn(),
  onlineAgent: vi.fn(),
  updateAgent: vi.fn(),
}));

vi.mock('@/services/phase2/skills', () => ({
  listSkills: vi.fn(),
}));

vi.mock('../AgentForm', () => ({
  default: () => <div data-testid="agent-form" />,
}));

vi.mock('../AgentTestModal', () => ({
  default: () => null,
}));

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

const agent: Phase2AgentResponse = {
  id: 'agent-a',
  name: '研究助手',
  description: 'desc',
  promptMode: 'STRUCTURED',
  promptConfig: {},
  systemPrompt: '',
  modelName: 'qwen3.7-max',
  status: 'OFFLINE',
  version: 4,
  skillIds: [],
  capabilityKeys: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function renderEditor() {
  return render(
    <MemoryRouter initialEntries={['/app/agents/agent-a']}>
      <Routes>
        <Route path="/app/agents/:agentId" element={<AgentEditorPage />} />
        <Route path="/app/agents" element={<div>Agent 列表</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AgentEditorPage deletion', () => {
  beforeEach(() => {
    vi.mocked(agentService.getAgent).mockResolvedValue(agent);
    vi.mocked(agentService.listModels).mockResolvedValue([]);
    vi.mocked(agentService.listToolCapabilities).mockResolvedValue([]);
    vi.mocked(skillService.listSkills).mockResolvedValue([]);
    vi.mocked(agentService.deleteAgent).mockResolvedValue(null);
  });

  it('opens a visible confirmation for an OFFLINE agent', async () => {
    renderEditor();

    fireEvent.click(await screen.findByTestId('agent-delete'));

    expect(
      await screen.findByText('下线后的 Agent 将被删除，此操作不可恢复。'),
    ).toBeInTheDocument();
    expect(agentService.deleteAgent).not.toHaveBeenCalled();
  });

  it('deletes with the loaded version only after confirmation', async () => {
    renderEditor();

    fireEvent.click(await screen.findByTestId('agent-delete'));
    const deleteButtons = await screen.findAllByRole('button', {
      name: /删\s*除/,
    });
    fireEvent.click(deleteButtons[deleteButtons.length - 1]!);

    await waitFor(() => {
      expect(agentService.deleteAgent).toHaveBeenCalledWith('agent-a', {
        version: 4,
      });
    });
  });
});
