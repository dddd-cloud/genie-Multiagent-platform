import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import GeneralInput from '../index';

vi.mock('@/services/conversation/attachments', () => ({
  uploadConversationAttachment: vi.fn(async (_conversationId: string, file: File) => ({
    id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    fileName: file.name,
    fileType: 'md',
    sizeBytes: file.size,
    extractedChars: 5,
    truncated: false,
  })),
  deleteConversationAttachment: vi.fn(async () => undefined),
}));

describe('GeneralInput attachments', () => {
  it('shows the attach control when a conversation can be created', () => {
    render(
      <GeneralInput
        placeholder="x"
        showBtn={false}
        disabled={false}
        size="medium"
        send={vi.fn()}
        ensureConversation={async () => 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'}
      />,
    );
    expect(screen.getByTestId('composer-attach-button')).toBeInTheDocument();
  });

  it('hides the attach control when no conversation target exists', () => {
    render(
      <GeneralInput
        placeholder="x"
        showBtn={false}
        disabled={false}
        size="medium"
        send={vi.fn()}
      />,
    );
    expect(screen.queryByTestId('composer-attach-button')).toBeNull();
  });

  it('accepts files dropped onto the composer', async () => {
    const file = new File(['hello'], 'notes.md', { type: 'text/markdown' });
    render(
      <GeneralInput
        placeholder="x"
        showBtn={false}
        disabled={false}
        size="medium"
        send={vi.fn()}
        ensureConversation={async () => 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'}
      />,
    );
    const zone = screen.getByTestId('composer-dropzone');
    const dataTransfer = {
      files: [file],
      types: ['Files'],
      dropEffect: 'copy',
    };
    fireEvent.dragEnter(zone, { dataTransfer });
    expect(screen.getByTestId('composer-drop-overlay')).toHaveTextContent('松开以上传文件');
    fireEvent.drop(zone, { dataTransfer });
    const chip = await screen.findByTestId('composer-attachment-chip');
    expect(chip).toBeInTheDocument();
    expect(screen.getByTestId('composer-shell').contains(chip)).toBe(false);
  });
});
