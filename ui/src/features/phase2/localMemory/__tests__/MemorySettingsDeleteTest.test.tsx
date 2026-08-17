import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { FakeMemoryIndexStore } from '../FakeMemoryIndexStore';
import { FakePrivateFileSystem } from '../FakePrivateFileSystem';
import LocalMemoryProvider from '../LocalMemoryProvider';
import { emptyLongTermMemoryDoc } from '../markdownSerializer';
import MemorySettingsPage from '../MemorySettingsPage';
import { MemoryRepository } from '../memoryRepository';

vi.mock('@/features/conversation/api', () => ({
  listConversations: vi.fn().mockResolvedValue({ items: [], hasMore: false }),
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

async function renderPage() {
  const fs = new FakePrivateFileSystem();
  const store = new FakeMemoryIndexStore();
  const repository = new MemoryRepository('user-a', fs, store);
  const doc = emptyLongTermMemoryDoc('2026-08-06T00:00:00.000Z');
  doc.sections.基本信息.push({
    key: '姓名',
    value: '张三',
  });
  await repository.writeLongTermMemory(doc);

  render(
    <LocalMemoryProvider
      userId="user-a"
      fileSystem={fs}
      indexStore={store}
      autoStart={false}
    >
      <MemorySettingsPage />
    </LocalMemoryProvider>,
  );

  return { fs, repository };
}

describe('MemorySettingsPage deletion', () => {
  it('opens a visible confirmation before deleting an entry', async () => {
    await renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '删除 姓名' }));

    expect(await screen.findByText('删除这条记忆？')).toBeInTheDocument();
    expect(screen.getByText(/张三/)).toBeInTheDocument();
  });

  it('removes the entry after confirmation', async () => {
    const { repository } = await renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '删除 姓名' }));
    const deleteButtons = await screen.findAllByRole('button', {
      name: /删\s*除/,
    });
    fireEvent.click(deleteButtons[deleteButtons.length - 1]!);

    await waitFor(() => {
      expect(screen.queryByText(/张三/)).not.toBeInTheDocument();
    });

    const stored = await repository.readLongTermMemory();
    expect(stored.status).toBe('READY');
    if (stored.status === 'READY') {
      expect(stored.doc.sections.基本信息).toEqual([]);
    }
  });
});
