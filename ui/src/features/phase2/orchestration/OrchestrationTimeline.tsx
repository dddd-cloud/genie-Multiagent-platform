import type { ReactNode } from 'react';
import type { StepMode } from '@/contracts';
import type {
  OrchestrationUiState,
  StepUiState,
  StepUiStatus,
  SubTaskUiState,
  SubTaskUiStatus,
  TraceLine,
} from './types';

export interface OrchestrationTimelineProps {
  state: OrchestrationUiState;
  onToggleMaster?: () => void;
  onToggleMain?: () => void;
  onToggleStep?: (attemptNo: number, stepId: string) => void;
  onToggleSubTask?: (
    attemptNo: number,
    stepId: string,
    subTaskId: string,
  ) => void;
}

const STATUS_DOT: Record<StepUiStatus | SubTaskUiStatus, string> = {
  PLANNED: 'bg-text-tertiary',
  RUNNING: 'bg-brand',
  COMPLETED: 'bg-success',
  FAILED: 'bg-danger',
  SKIPPED: 'bg-text-tertiary',
  DEGRADED: 'bg-warning',
};

function Caret({ open }: { open: boolean }) {
  return (
    <span
      className="inline-block text-[12px] text-text-tertiary transition-transform duration-150"
      style={{ transform: open ? 'rotate(0deg)' : 'rotate(180deg)' }}
      aria-hidden
    >
      ^
    </span>
  );
}

function TraceBody({ lines }: { lines: TraceLine[] }) {
  if (lines.length === 0) {
    return (
      <div className="text-[12px] text-text-tertiary px-8 py-6">暂无进展</div>
    );
  }
  return (
    <div className="max-h-[220px] overflow-auto px-8 py-6 font-mono text-[12px] leading-[18px] text-text-secondary whitespace-pre-wrap break-words">
      {lines.map((line) => (
        <div key={line.sequence} className="mb-4 last:mb-0">
          {line.kind !== 'THOUGHT' ? (
            <span className="text-text-tertiary mr-6">[{line.kind}]</span>
          ) : null}
          {line.text}
          {line.truncated ? (
            <span className="text-text-tertiary">…</span>
          ) : null}
        </div>
      ))}
    </div>
  );
}

function CollapsibleBlock({
  title,
  open,
  onToggle,
  status,
  badge,
  children,
  testId,
}: {
  title: string;
  open: boolean;
  onToggle?: () => void;
  status?: StepUiStatus | SubTaskUiStatus;
  badge?: string | null;
  children: ReactNode;
  testId: string;
}) {
  return (
    <div
      className="border-t border-border first:border-t-0"
      data-testid={testId}
    >
      <button
        type="button"
        className="w-full flex items-center gap-8 px-10 py-8 text-left hover:bg-surface-subtle/80"
        onClick={onToggle}
        aria-expanded={open}
      >
        {status ? (
          <span
            className={`w-[6px] h-[6px] rounded-full shrink-0 ${STATUS_DOT[status]}`}
          />
        ) : null}
        <span className="flex-1 text-[13px] text-text-primary truncate">
          {title}
        </span>
        {badge ? (
          <span className="text-[11px] text-text-tertiary shrink-0">{badge}</span>
        ) : null}
        <Caret open={open} />
      </button>
      {open ? children : null}
    </div>
  );
}

function looksLikeId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    value,
  );
}

function displayAgentName(step: {
  agentName?: string;
  agentId?: string;
  stepId?: string;
  subTaskId?: string;
}): string {
  const name = (step.agentName || '').trim();
  if (name && !looksLikeId(name)) {
    return name;
  }
  const id = (step.agentId || '').trim();
  if (id && !looksLikeId(id)) {
    return id;
  }
  return name || id || step.subTaskId || step.stepId || '';
}

function stepModeLabel(mode: StepMode | null | undefined): string | null {
  if (!mode) return null;
  switch (mode) {
    case 'MAIN_ONLY':
      return 'MAIN_ONLY';
    case 'SINGLE_AGENT':
      return 'SINGLE_AGENT';
    case 'PARALLEL_AGENTS':
      return 'PARALLEL_AGENTS';
    default:
      return null;
  }
}

function stepBadge(step: StepUiState): string | null {
  const parts: string[] = [];
  const mode = stepModeLabel(step.stepMode);
  if (mode) parts.push(mode);
  if (step.reviewing) parts.push('review');
  if (step.fallbackActive) parts.push('fallback');
  if (step.status === 'DEGRADED') parts.push('degraded');
  if (typeof step.retryNo === 'number' && step.retryNo > 0) {
    parts.push(`retry#${step.retryNo}`);
  }
  return parts.length > 0 ? parts.join(' · ') : null;
}

function subTaskBadge(sub: SubTaskUiState): string | null {
  const parts: string[] = [];
  if (sub.retryNo > 0) parts.push(`retry#${sub.retryNo}`);
  if (sub.status === 'FAILED' && sub.errorCode) parts.push(sub.errorCode);
  return parts.length > 0 ? parts.join(' · ') : null;
}

function liveProgressHint(
  state: OrchestrationUiState,
  steps: StepUiState[],
): string {
  for (const step of steps) {
    if (step.status !== 'RUNNING') continue;
    const runningSubs = Object.values(step.subTasks ?? {}).filter(
      (s) => s.status === 'RUNNING',
    );
    if (runningSubs.length > 0) {
      const names = runningSubs.map((s) => displayAgentName(s)).join(' / ');
      return `并行执行中：${names}`;
    }
    if (step.fallbackActive) {
      return `${displayAgentName(step)} fallback 执行中…`;
    }
    if (step.reviewing) {
      return `${displayAgentName(step)} 评审中…`;
    }
    const thought = [...step.lines]
      .reverse()
      .find((line) => {
        if (line.kind !== 'THOUGHT' && line.kind !== 'STATUS') return false;
        const t = (line.text || '').trim();
        if (
          t.startsWith('{') &&
          t.includes('"status"') &&
          t.includes('"output"')
        ) {
          return false;
        }
        return t.length > 0;
      });
    if (thought?.text) {
      const text = thought.text.replace(/\s+/g, ' ').trim();
      return `${displayAgentName(step)}：${text.slice(0, 48)}${text.length > 48 ? '…' : ''}`;
    }
    return `${displayAgentName(step)} 执行中…`;
  }
  const lastMain = [...state.main.lines].reverse().find((line) => line.text);
  if (lastMain?.text) {
    const text = lastMain.text.replace(/\s+/g, ' ').trim();
    return `${text.slice(0, 56)}${text.length > 56 ? '…' : ''}`;
  }
  if (steps.length > 0) {
    return `已安排 ${steps.length} 个步骤`;
  }
  return '正在编排…';
}

function latestAttemptSteps(
  state: OrchestrationUiState,
): { attemptNo: number; steps: StepUiState[] } | null {
  const attemptNos = Object.keys(state.attempts)
    .map(Number)
    .filter((n) => Number.isFinite(n))
    .sort((a, b) => b - a);
  for (const attemptNo of attemptNos) {
    const steps = Object.values(state.attempts[attemptNo].steps);
    if (steps.length > 0) {
      return {
        attemptNo,
        steps
      };
    }
  }
  return null;
}

/**
 * Collapsible live work panel above the final answer.
 * Default: all collapsed. Header: 思考中 / 已完成思考.
 * Does not render system prompts, full CoT, credentials, raw tool I/O, or Memory body.
 */
export default function OrchestrationTimeline({
  state,
  onToggleMaster,
  onToggleMain,
  onToggleStep,
  onToggleSubTask,
}: OrchestrationTimelineProps) {
  if (state.route !== null && state.route !== 'ORCHESTRATED') {
    return null;
  }

  const thinking =
    state.phaseLabel !== 'done' && state.terminalStatus === 'RUNNING';
  const completionLabel =
    state.terminalStatus === 'SUCCESS' || state.terminalStatus === 'PARTIAL'
      ? state.terminalStatus
      : null;
  const header = thinking
    ? '思考中'
    : completionLabel
      ? `已完成思考 · ${completionLabel}`
      : '已完成思考';
  const latest = latestAttemptSteps(state);
  const liveHint = thinking ? liveProgressHint(state, latest?.steps ?? []) : '';
  const stepsHaveObjectives = (latest?.steps ?? []).some(
    (step) => (step.objective || '').trim().length > 0,
  );

  return (
    <div
      className="phase2-orchestration-timeline w-full rounded-md border border-border bg-surface-subtle/40"
      data-testid="orchestration-timeline"
    >
      <button
        type="button"
        className="w-full flex items-center justify-between gap-8 px-12 py-10 text-left"
        onClick={onToggleMaster}
        aria-expanded={state.masterOpen}
        data-testid="orchestration-master-toggle"
      >
        <span className="flex-1 min-w-0 flex items-center gap-8">
          <span
            className="text-[13px] text-text-secondary shrink-0"
            data-testid="orchestration-completion-status"
          >
            {header}
          </span>
          {liveHint ? (
            <span
              className="text-[12px] text-text-tertiary truncate"
              data-testid="orchestration-live-hint"
            >
              {liveHint}
            </span>
          ) : null}
        </span>
        <Caret open={state.masterOpen} />
      </button>

      {state.masterOpen ? (
        <div data-testid="orchestration-master-body">
          <CollapsibleBlock
            title="主 Agent"
            open={state.main.open}
            onToggle={onToggleMain}
            testId="orchestration-main"
          >
            {latest && latest.steps.length > 0 ? (
              <div
                className="px-8 pt-6 pb-2 text-[12px] leading-[18px] text-text-secondary"
                data-testid="orchestration-plan-steps"
              >
                <div className="text-text-tertiary mb-4">任务安排</div>
                {latest.steps.map((step) => (
                  <div key={step.stepId} className="mb-2">
                    - [{step.stepId}]
                    {step.stepMode ? ` (${step.stepMode})` : ''}{' '}
                    {displayAgentName(step)}：{step.objective}
                  </div>
                ))}
              </div>
            ) : null}
            <TraceBody
              lines={
                stepsHaveObjectives
                  ? state.main.lines.filter(
                    (line) =>
                      !(
                        line.kind === 'OUTPUT' &&
                          line.text.trimStart().startsWith('任务安排')
                      ),
                  )
                  : state.main.lines
              }
            />
          </CollapsibleBlock>

          {latest?.steps.map((step) => {
            const subTasks = Object.values(step.subTasks ?? {});
            return (
              <CollapsibleBlock
                key={`${latest.attemptNo}-${step.stepId}`}
                title={displayAgentName(step) || step.stepId}
                open={step.open}
                status={step.status}
                badge={stepBadge(step)}
                onToggle={() => onToggleStep?.(latest.attemptNo, step.stepId)}
                testId={`orchestration-step-${step.stepId}`}
              >
                {step.objective ? (
                  <div className="px-8 pt-4 text-[12px] text-text-tertiary">
                    目标：{step.objective}
                  </div>
                ) : null}
                <TraceBody lines={step.lines} />
                {subTasks.map((sub) => (
                  <CollapsibleBlock
                    key={sub.subTaskId}
                    title={`${displayAgentName(sub)} · ${sub.subTaskId}`}
                    open={sub.open}
                    status={sub.status}
                    badge={subTaskBadge(sub)}
                    onToggle={() =>
                      onToggleSubTask?.(
                        latest.attemptNo,
                        step.stepId,
                        sub.subTaskId,
                      )
                    }
                    testId={`orchestration-subtask-${sub.subTaskId}`}
                  >
                    {sub.objective ? (
                      <div className="px-8 pt-4 text-[12px] text-text-tertiary">
                        目标：{sub.objective}
                      </div>
                    ) : null}
                    <TraceBody lines={sub.lines} />
                  </CollapsibleBlock>
                ))}
              </CollapsibleBlock>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
