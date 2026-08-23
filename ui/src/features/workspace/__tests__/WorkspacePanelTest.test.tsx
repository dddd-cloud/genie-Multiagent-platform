import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { MemoryWorkspaceFileStore } from '@/platform/workspace/MemoryWorkspaceFileStore';
import type { UserWorkspace } from '@/platform/workspace/catalog';
import { WorkspaceProvider } from '@/features/workspace/WorkspaceProvider';
import { isMarkdownFile, WorkspacePanel } from '@/features/workspace/WorkspacePanel';

const now = '2026-08-21T00:00:00.000Z';

function renderWorkspace() {
  const store = new MemoryWorkspaceFileStore();
  const initial: UserWorkspace = {
    id: 'ws-1',
    name: '默认工作区',
    createdAt: now,
    updatedAt: now,
  };

  function Harness() {
    const [workspaces, setWorkspaces] = useState<UserWorkspace[]>([initial]);
    const [active, setActive] = useState(initial);
    return (
      <WorkspaceProvider
        userId="user-a"
        workspaceId={active.id}
        store={store}
        workspaces={workspaces}
        activeWorkspace={active}
        selectWorkspace={(id) => {
          const next = workspaces.find((item) => item.id === id);
          if (next) setActive(next);
        }}
        createWorkspace={(name) => {
          const next: UserWorkspace = {
            id: `ws-${workspaces.length + 1}`,
            name,
            createdAt: now,
            updatedAt: now,
          };
          setWorkspaces((items) => [...items, next]);
          setActive(next);
        }}
        renameWorkspace={(name) => {
          setWorkspaces((items) =>
            items.map((item) => (item.id === active.id ? { ...item, name } : item)),
          );
          setActive((item) => ({ ...item, name }));
        }}
        deleteWorkspace={() => {
          setWorkspaces((items) => {
            if (items.length <= 1) {
              throw new Error('至少保留一个工作区');
            }
            const next = items.filter((item) => item.id !== active.id);
            setActive(next[0]);
            return next;
          });
        }}
      >
        <WorkspacePanel />
      </WorkspaceProvider>
    );
  }

  return render(<Harness />);
}

describe('WorkspacePanel', () => {
  it('creates a workspace through an in-app dialog instead of window.prompt', async () => {
    const prompt = vi.spyOn(window, 'prompt');
    renderWorkspace();
    fireEvent.click(screen.getByTestId('workspace-create'));
    expect(screen.getByTestId('workspace-dialog')).toBeTruthy();
    fireEvent.change(screen.getByTestId('workspace-dialog-input'), {
      target: { value: '研究笔记' },
    });
    fireEvent.click(screen.getByTestId('workspace-dialog-confirm'));
    await waitFor(() => {
      const select = screen.getByTestId('workspace-select') as HTMLSelectElement;
      expect(select.selectedOptions[0]?.textContent).toBe('研究笔记');
    });
    expect(prompt).not.toHaveBeenCalled();
    prompt.mockRestore();
  });

  it('renames the current workspace from the panel dialog', async () => {
    renderWorkspace();
    fireEvent.click(screen.getByTestId('workspace-rename'));
    fireEvent.change(screen.getByTestId('workspace-dialog-input'), {
      target: { value: '项目资料' },
    });
    fireEvent.click(screen.getByTestId('workspace-dialog-confirm'));
    await waitFor(() => {
      const select = screen.getByTestId('workspace-select') as HTMLSelectElement;
      expect(select.selectedOptions[0]?.textContent).toBe('项目资料');
    });
  });

  it('places delete between rename and refresh, and keeps the last workspace', () => {
    renderWorkspace();
    const rename = screen.getByTestId('workspace-rename');
    const remove = screen.getByTestId('workspace-delete');
    const refresh = screen.getByTestId('workspace-refresh');
    expect(rename.compareDocumentPosition(remove) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(remove.compareDocumentPosition(refresh) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(remove).toBeDisabled();
  });

  it('deletes the current workspace after confirm', async () => {
    renderWorkspace();
    fireEvent.click(screen.getByTestId('workspace-create'));
    fireEvent.change(screen.getByTestId('workspace-dialog-input'), {
      target: { value: '研究笔记' },
    });
    fireEvent.click(screen.getByTestId('workspace-dialog-confirm'));
    await waitFor(() => {
      expect(screen.getByTestId('workspace-delete')).not.toBeDisabled();
    });
    fireEvent.click(screen.getByTestId('workspace-delete'));
    fireEvent.click(screen.getByTestId('workspace-dialog-confirm'));
    await waitFor(() => {
      const select = screen.getByTestId('workspace-select') as HTMLSelectElement;
      expect(select.selectedOptions[0]?.textContent).toBe('默认工作区');
      expect(screen.getByTestId('workspace-delete')).toBeDisabled();
    });
  });

  it('shows refresh feedback without a backend round-trip', async () => {
    renderWorkspace();
    fireEvent.click(screen.getByTestId('workspace-refresh'));
    await waitFor(() => {
      expect(screen.getByTestId('workspace-refresh').textContent).toMatch(/已刷新|刷新/);
    });
  });

  it('renders markdown files as compiled preview by default', async () => {
    renderWorkspace();
    fireEvent.click(screen.getByTestId('workspace-create-file'));
    fireEvent.click(screen.getByTestId('workspace-dialog-confirm'));
    expect(await screen.findByTestId('workspace-markdown-preview')).toBeTruthy();
    expect(screen.getByRole('heading', { name: '未命名' })).toBeTruthy();
  });

  it('detects markdown filenames', () => {
    expect(isMarkdownFile('notes.md')).toBe(true);
    expect(isMarkdownFile('README.markdown')).toBe(true);
    expect(isMarkdownFile('script.py')).toBe(false);
  });
});
