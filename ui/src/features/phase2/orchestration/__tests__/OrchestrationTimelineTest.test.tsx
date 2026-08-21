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
        text: '**假设**：\n- **技术栈**：Spring AI',
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
      '查看思考过程',
    );
    expect(screen.queryByTestId('orchestration-master-body')).toBeNull();
    expect(screen.queryByText('STATUS')).toBeNull();
    expect(screen.queryByText(/ORCHESTRATED|SUCCESS|MAIN_ONLY/)).toBeNull();
  });

  it('renders a group chat without boxed process cards', () => {
    const onToggleMaster = vi.fn();
    const { container } = render(
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
    expect(screen.getAllByText('主规划').length).toBeGreaterThan(0);
    expect(screen.getAllByText('市场研究员').length).toBeGreaterThan(0);
    expect(screen.getByText('需要市场和竞品两边一起看。')).toBeTruthy();
    expect(screen.queryByText(/ORCHESTRATED|RESOURCE_CREATION_REQUEST|MAIN_ONLY/)).toBeNull();
    expect(container.querySelector('[class*="border-border"]')).toBeNull();
    fireEvent.click(screen.getByTestId('orchestration-master-toggle'));
    expect(onToggleMaster).toHaveBeenCalled();
  });

  it('renders markdown, thinking status, and hides raw error codes', () => {
    render(
      <OrchestrationTimeline
        state={state({
          masterOpen: true,
          routeReasonCode: 'RESOURCE_CREATION_REQUEST',
          main: {
            open: true,
            lines: [
              {
                sequence: 1,
                kind: 'STATUS',
                text: '路由决策：ORCHESTRATED（RESOURCE_CREATION_REQUEST）',
              },
            ],
          },
          attempts: {
            1: {
              attemptNo: 1,
              steps: {
                s1: step({
                  stepId: 's1',
                  status: 'FAILED',
                  agentName: '4ec620f8-9449-4bc1-9f4c-44f423d56914',
                  agentId: '4ec620f8-9449-4bc1-9f4c-44f423d56914',
                  lines: [
                    {
                      sequence: 4,
                      kind: 'THOUGHT',
                      text: '**假设**：\n- **技术栈**：Spring AI',
                    },
                    {
                      sequence: 5,
                      kind: 'ERROR',
                      text: 'AGENT_INVALID_RESULT',
                    },
                  ],
                }),
                s2: step({
                  stepId: 's2',
                  agentName: '竞品分析师',
                  objective: '对比主要玩家',
                  status: 'RUNNING',
                  open: true,
                  lines: [
                    {
                      sequence: 6,
                      kind: 'THOUGHT',
                      text: '先看对手定价。',
                    },
                  ],
                }),
              },
            },
          },
        })}
      />,
    );
    expect(screen.queryByText('**假设**')).toBeNull();
    expect(screen.getByText('假设')).toBeTruthy();
    expect(screen.getByText('技术栈')).toBeTruthy();
    expect(screen.queryByText('AGENT_INVALID_RESULT')).toBeNull();
    expect(screen.getByText(/没能形成可用结论/)).toBeTruthy();
    expect(screen.queryByText(/4ec620f8-9449-4bc1-9f4c-44f423d56914/)).toBeNull();
    expect(screen.getByTestId('orchestration-message-s2-status')).toHaveTextContent(
      '思考中',
    );
    expect(screen.getByTestId('orchestration-message-s1-status')).toHaveTextContent(
      '未完成',
    );
    expect(screen.getByTestId('agent-thinking-spinner')).toBeTruthy();
    expect(screen.getByTestId('orchestration-handoff-assign-s1')).toHaveTextContent(
      '主规划邀请',
    );
    expect(screen.getByTestId('orchestration-handoff-report-s1')).toHaveTextContent(
      '已回报主规划',
    );
    expect(screen.queryByText(/ORCHESTRATED/)).toBeNull();
    expect(screen.queryByText(/RESOURCE_CREATION_REQUEST/)).toBeNull();
  });

  it('shows waiting experts instead of a ghost parent for parallel work', () => {
    render(
      <OrchestrationTimeline
        state={state({
          masterOpen: true,
          terminalStatus: 'RUNNING',
          phaseLabel: 'thinking',
          attempts: {
            1: {
              attemptNo: 1,
              steps: {
                s1: step({
                  stepId: 's1',
                  agentId: '',
                  agentName: '',
                  objective: '并行开发贪吃蛇',
                  status: 'RUNNING',
                  lines: [
                    {
                      sequence: 3,
                      kind: 'THOUGHT',
                      text: 'ghost parent should not render',
                    },
                  ],
                  subTasks: {
                    'st-front': {
                      subTaskId: 'st-front',
                      agentId: 'frontend',
                      agentName: '前端',
                      objective: '写页面',
                      status: 'RUNNING',
                      retryNo: 0,
                      errorCode: null,
                      lines: [
                        {
                          sequence: 4,
                          kind: 'THOUGHT',
                          text: '先搭画布。',
                        },
                      ],
                      open: true,
                    },
                    'st-back': {
                      subTaskId: 'st-back',
                      agentId: 'backend',
                      agentName: '后端',
                      objective: '写接口',
                      status: 'RUNNING',
                      retryNo: 0,
                      errorCode: null,
                      lines: [],
                      open: true,
                    },
                  },
                }),
              },
            },
          },
        })}
      />,
    );
    expect(screen.queryByTestId('orchestration-message-s1')).toBeNull();
    expect(screen.queryByText('ghost parent should not render')).toBeNull();
    expect(screen.getByTestId('orchestration-handoff-parallel-s1')).toHaveTextContent(
      '主规划安排 前端、后端 同时开始',
    );
    expect(screen.getByTestId('orchestration-message-st-front-status')).toHaveTextContent(
      '思考中',
    );
    expect(screen.getByTestId('orchestration-message-st-back-status')).toHaveTextContent(
      '思考中',
    );
    expect(screen.getByText('正在思考')).toBeTruthy();
    expect(screen.queryByText('排队等待')).toBeNull();
  });

  it('does not claim a later parallel group has started', () => {
    render(
      <OrchestrationTimeline
        state={state({
          masterOpen: true,
          terminalStatus: 'RUNNING',
          phaseLabel: 'thinking',
          attempts: {
            1: {
              attemptNo: 1,
              steps: {
                s1: step({
                  stepId: 's1',
                  agentName: '前端',
                  objective: '写页面',
                  status: 'RUNNING',
                  subTasks: {
                    'st-front': {
                      subTaskId: 'st-front',
                      agentId: 'frontend',
                      agentName: '代码团队·前端',
                      objective: '写页面',
                      status: 'RUNNING',
                      retryNo: 0,
                      errorCode: null,
                      lines: [],
                      open: true,
                    },
                    'st-back': {
                      subTaskId: 'st-back',
                      agentId: 'backend',
                      agentName: '代码团队·后端',
                      objective: '写接口',
                      status: 'RUNNING',
                      retryNo: 0,
                      errorCode: null,
                      lines: [],
                      open: true,
                    },
                  },
                }),
                s2: step({
                  stepId: 's2',
                  agentName: '',
                  objective: '审查测试',
                  status: 'PLANNED',
                  lines: [],
                  subTasks: {
                    'st-review': {
                      subTaskId: 'st-review',
                      agentId: 'review',
                      agentName: '代码团队·审查官',
                      objective: '审查',
                      status: 'PLANNED',
                      retryNo: 0,
                      errorCode: null,
                      lines: [],
                      open: true,
                    },
                    'st-qa': {
                      subTaskId: 'st-qa',
                      agentId: 'qa',
                      agentName: '代码团队·测试排爆',
                      objective: '测试',
                      status: 'PLANNED',
                      retryNo: 0,
                      errorCode: null,
                      lines: [],
                      open: true,
                    },
                  },
                }),
              },
            },
          },
        })}
      />,
    );
    expect(screen.getByTestId('orchestration-handoff-parallel-s1')).toHaveTextContent(
      '主规划安排 代码团队·前端、代码团队·后端 同时开始',
    );
    expect(
      screen.getByTestId('orchestration-handoff-parallel-queued-s2'),
    ).toHaveTextContent('随后将同时邀请 代码团队·审查官、代码团队·测试排爆');
    expect(screen.queryByTestId('orchestration-handoff-parallel-s2')).toBeNull();
  });

  it('keeps later serial steps in waiting while the first is running', () => {
    render(
      <OrchestrationTimeline
        state={state({
          masterOpen: true,
          terminalStatus: 'RUNNING',
          phaseLabel: 'thinking',
          attempts: {
            1: {
              attemptNo: 1,
              steps: {
                s1: step({
                  stepId: 's1',
                  agentName: '前端',
                  objective: '写页面',
                  status: 'RUNNING',
                }),
                s2: step({
                  stepId: 's2',
                  agentName: '后端',
                  objective: '写接口',
                  status: 'PLANNED',
                  lines: [],
                }),
                s3: step({
                  stepId: 's3',
                  agentName: '联调',
                  objective: '串起来',
                  status: 'PLANNED',
                  lines: [],
                }),
              },
            },
          },
        })}
      />,
    );
    expect(screen.getByTestId('orchestration-message-s1-status')).toHaveTextContent(
      '思考中',
    );
    expect(screen.getByTestId('orchestration-message-s2-status')).toHaveTextContent(
      '等待中',
    );
    expect(screen.getByTestId('orchestration-message-s3-status')).toHaveTextContent(
      '等待中',
    );
    expect(screen.queryByTestId('orchestration-handoff-assign-s2')).toBeNull();
  });
});
