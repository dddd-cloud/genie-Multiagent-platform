import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import ExecutionModeSelector from '../ExecutionModeSelector';

describe('ExecutionModeSelectorTest', () => {
  it('defaults to AUTO and renders all modes', () => {
    render(<ExecutionModeSelector />);
    const group = screen.getByTestId('execution-mode-selector');
    expect(group).toBeTruthy();
    expect(screen.getByRole('radio', { name: 'AUTO' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'DIRECT' })).not.toBeChecked();
    expect(
      screen.getByRole('radio', { name: 'ORCHESTRATED' }),
    ).not.toBeChecked();
  });

  it('notifies onChange when selecting DIRECT', () => {
    const onChange = vi.fn();
    render(<ExecutionModeSelector value="AUTO" onChange={onChange} />);
    // Ant Design button radios set pointer-events:none on the native input;
    // fire change on the input to exercise the controlled onChange path.
    fireEvent.click(screen.getByRole('radio', { name: 'DIRECT' }));
    expect(onChange).toHaveBeenCalledWith('DIRECT');
  });

  it('respects controlled ORCHESTRATED value', () => {
    render(<ExecutionModeSelector value="ORCHESTRATED" />);
    expect(screen.getByRole('radio', { name: 'ORCHESTRATED' })).toBeChecked();
  });
});
