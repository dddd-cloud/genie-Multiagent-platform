import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Phase2McpServerResponse } from '@/contracts/phase2';
import * as mcpService from '@/services/phase2/mcp';
import McpEditorPage from '../McpEditorPage';

vi.mock('@/services/phase2/mcp', () => ({
  createMcpServer: vi.fn(),
  deleteMcpServer: vi.fn(),
  disableMcpServer: vi.fn(),
  enableMcpServer: vi.fn(),
  getMcpServer: vi.fn(),
  listMcpTools: vi.fn(),
  refreshMcpTools: vi.fn(),
  setMcpToolEnabled: vi.fn(),
  testMcpServer: vi.fn(),
  updateMcpServer: vi.fn(),
}));

vi.mock('../McpToolTable', () => ({
  default: () => <div data-testid="mcp-tool-table" />,
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

const server: Phase2McpServerResponse = {
  id: 'mcp-a',
  name: '地图服务',
  serverUrl: 'https://example.invalid/mcp',
  authType: 'NONE',
  authName: null,
  status: 'DRAFT',
  credentialConfigured: false,
  lastCheckStatus: null,
  lastCheckCode: null,
  lastCheckedAt: null,
  version: 3,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function renderEditor() {
  return render(
    <MemoryRouter initialEntries={['/app/mcp/mcp-a']}>
      <Routes>
        <Route path="/app/mcp/:serverId" element={<McpEditorPage />} />
        <Route path="/app/mcp" element={<div>MCP 列表</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('McpEditorPage deletion', () => {
  beforeEach(() => {
    vi.mocked(mcpService.getMcpServer).mockResolvedValue(server);
    vi.mocked(mcpService.listMcpTools).mockResolvedValue([]);
    vi.mocked(mcpService.deleteMcpServer).mockResolvedValue(null);
  });

  it('opens a visible confirmation before deleting', async () => {
    renderEditor();

    fireEvent.click(await screen.findByTestId('mcp-delete'));

    expect(
      await screen.findByText('删除后，已发现的 MCP 工具将同时停用。'),
    ).toBeInTheDocument();
    expect(mcpService.deleteMcpServer).not.toHaveBeenCalled();
  });

  it('deletes with the loaded version only after confirmation', async () => {
    renderEditor();

    fireEvent.click(await screen.findByTestId('mcp-delete'));
    const deleteButtons = await screen.findAllByRole('button', { name: '删除' });
    fireEvent.click(deleteButtons[deleteButtons.length - 1]!);

    await waitFor(() => {
      expect(mcpService.deleteMcpServer).toHaveBeenCalledWith('mcp-a', {
        version: 3,
      });
    });
  });
});
