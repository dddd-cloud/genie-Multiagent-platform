import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ComposerAttachments from '../ComposerAttachments';
import type { ComposerAttachment } from '../useComposerAttachments';

function file(partial: Partial<ComposerAttachment>): ComposerAttachment {
  return {
    clientId: partial.clientId ?? 'c1',
    name: partial.name ?? 'notes.md',
    type: partial.type ?? 'md',
    size: partial.size ?? 12,
    status: partial.status ?? 'uploading',
    progress: partial.progress ?? 40,
    attachmentId: partial.attachmentId,
    errorMessage: partial.errorMessage,
  };
}

describe('ComposerAttachments', () => {
  it('renders a gray uploading chip with a remove control', () => {
    const onRemove = vi.fn();
    render(
      <ComposerAttachments
        attachments={[file({ status: 'uploading', progress: 40 })]}
        onRemove={onRemove}
      />,
    );
    const chip = screen.getByTestId('composer-attachment-chip');
    expect(chip).toHaveAttribute('data-status', 'uploading');
    expect(screen.getByTestId('composer-attachment-progress')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('composer-attachment-remove'));
    expect(onRemove).toHaveBeenCalledWith('c1');
  });

  it('renders a ready chip in normal color', () => {
    render(
      <ComposerAttachments
        attachments={[
          file({
            status: 'ready',
            progress: 100,
            attachmentId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
          }),
        ]}
        onRemove={vi.fn()}
      />,
    );
    expect(screen.getByTestId('composer-attachment-chip')).toHaveAttribute(
      'data-status',
      'ready',
    );
    expect(screen.queryByTestId('composer-attachment-progress')).toBeNull();
  });

  it('shows a remove control after upload failure', () => {
    const onRemove = vi.fn();
    render(
      <ComposerAttachments
        attachments={[file({ status: 'error', progress: 0, errorMessage: 'failed to read PDF file' })]}
        onRemove={onRemove}
      />,
    );
    expect(screen.getByTestId('composer-attachment-chip')).toHaveAttribute(
      'data-status',
      'error',
    );
    expect(screen.getByText('failed to read PDF file')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('composer-attachment-remove'));
    expect(onRemove).toHaveBeenCalledWith('c1');
  });
});
