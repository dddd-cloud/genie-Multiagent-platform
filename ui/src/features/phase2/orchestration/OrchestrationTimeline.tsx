import type { ReactNode } from 'react';
import type {
  OrchestrationUiState,
  StepUiState,
  StepUiStatus,
  TraceLine,
} from './types';

export interface OrchestrationTimelineProps {
  state: OrchestrationUiState;
  onToggleMaster?: () => void;
  onToggleMain?: () => void;
  onToggleStep?: (attemptNo: number, stepId: string) => void;
}

const STATUS_DOT: Record<StepUiStatus, string> = {
  PLANNED: 'bg-text-tertiary',
  RUNNING: 'bg-brand',
  COMPLETED: 'bg-success',
  FAILED: 'bg-danger',
  SKIPPED: 'bg-text-tertiary',
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
  children,
  testId,
}: {
  title: string;
  open: boolean;
  onToggle?: () => void;
  status?: StepUiStatus;
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
        <Caret open={open} />
      </button>
      {open ? children : null}
    </div>
  );
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
      return { attemptNo, steps };
    }
  }
  return null;
}

/**
 * Collapsible live work panel above the final answer.
 * Default: all collapsed. Header: 思考中 / 已完成思考.
 */
export default function OrchestrationTimeline({
  state,
  onToggleMaster,
  onToggleMain,
  onToggleStep,
}: OrchestrationTimelineProps) {
  if (state.route !== null && state.route !== 'ORCHESTRATED') {
    return null;
  }

  const header =
    state.phaseLabel === 'done' || state.terminalStatus !== 'RUNNING'
      ? '已完成思考'
      : '思考中';
  const latest = latestAttemptSteps(state);

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
        <span className="text-[13px] text-text-secondary">{header}</span>
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
            <TraceBody lines={state.main.lines} />
          </CollapsibleBlock>

          {latest?.steps.map((step) => (
            <CollapsibleBlock
              key={`${latest.attemptNo}-${step.stepId}`}
              title={step.agentName || step.agentId}
              open={step.open}
              status={step.status}
              onToggle={() => onToggleStep?.(latest.attemptNo, step.stepId)}
              testId={`orchestration-step-${step.stepId}`}
            >
              {step.objective ? (
                <div className="px-8 pt-4 text-[12px] text-text-tertiary">
                  目标：{step.objective}
                </div>
              ) : null}
              <TraceBody lines={step.lines} />
            </CollapsibleBlock>
          ))}
        </div>
      ) : null}
    </div>
  );
}
