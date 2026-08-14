import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import GeneralInput from '../index';

describe('GeneralInput', () => {
  it('shows a stop control while running and does not send', () => {
    const send = vi.fn();
    const onStop = vi.fn();
    render(
      <GeneralInput
        placeholder="x"
        showBtn={false}
        disabled={false}
        size="medium"
        running
        onStop={onStop}
        send={send}
      />,
    );
    const stop = screen.getByTestId('chat-stop-button');
    expect(stop).toHaveAttribute('aria-label', '停止生成');
    fireEvent.click(stop);
    expect(onStop).toHaveBeenCalledTimes(1);
    expect(send).not.toHaveBeenCalled();
    expect(screen.queryByLabelText('发送')).toBeNull();
  });
});
