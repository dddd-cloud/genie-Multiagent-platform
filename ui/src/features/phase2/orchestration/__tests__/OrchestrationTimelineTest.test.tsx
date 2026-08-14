import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import OrchestrationTimeline from '../OrchestrationTimeline';
import { createInitialOrchestrationState } from '../orchestrationReducer';
import type { OrchestrationUiState, StepUiState } from '../types';

function step(partial: Partial<StepUiState> & Pick<StepUiState, 'stepId'>): StepUiState {
  return {
    agentId: 'a1',
    agentName: '市场研究员',
    objective: '梳理市场规模',
    status: 'RUNNING',
    lines: [
      {
        sequence: 4,
        kind: 'THOUGHT',
        text: '先看近三年的增速。',
      },
      {
        sequence: 5,
        kind: 'STATUS',
        text: 'STATUS',
      },
    ],
    open: true,
    subTasks: {},
    ...partial,
  };
}

function state(partial: Partial<OrchestrationUiState> = {}): OrchestrationUiState {
  const base = createInitialOrchestrationState();
  return {
    ...base,
    route: 'ORCHESTRATED',
    masterOpen: false,
    main: {
      open: false,
      lines: [
        {
          sequence: 1,
          kind: 'STATUS',
          text: '规划中',
        },
      ],
    },
    attempts: {
      1: {
        attemptNo: 1,
        steps: {
          s1: step({ stepId: 's1' }),
        },
      },
    },
    ...partial,
  };
}

describe('OrchestrationTimeline', () => {
  it('keeps the master fold collapsed and hides system tokens', () => {
    render(<OrchestrationTimeline state={state()} />);
    expect(screen.getByTestId('orchestration-completion-status')).toHaveTextContent(
      '正在协同',
    );
    expect(screen.queryByTestId('orchestration-master-body')).toBeNull();
    expect(screen.queryByText('STATUS')).toBeNull();
    expect(screen.queryByText(/ORCHESTRATED|SUCCESS|MAIN_ONLY/)).toBeNull();
  });

  it('expands nested work when the master fold is opened', () => {
    const onToggleMaster = vi.fn();
    render(
      <OrchestrationTimeline
        state={state({
          masterOpen: true,
          main: {
            open: true,
            lines: [
              {
                sequence: 1,
                kind: 'THOUGHT',
                text: '需要市场和竞品两边一起看。',
              },
            ],
          },
        })}
        onToggleMaster={onToggleMaster}
      />,
    );
    expect(screen.getByTestId('orchestration-master-body')).toBeTruthy();
    expect(screen.getByText('主规划')).toBeTruthy();
    expect(screen.getByText('市场研究员')).toBeTruthy();
    expect(screen.getByText('先看近三年的增速。')).toBeTruthy();
    expect(screen.getByText('需要市场和竞品两边一起看。')).toBeTruthy();
    expect(screen.getByText(/主规划邀请 市场研究员 一起完成/)).toBeTruthy();
    expect(screen.queryByText('[STATUS]')).toBeNull();
    fireEvent.click(screen.getByTestId('orchestration-master-toggle'));
    expect(onToggleMaster).toHaveBeenCalled();
  });
});
