import { useEffect, useRef, type ReactNode } from 'react';
import classNames from 'classnames';
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

function Spinner() {
  return (
    <span
      className="inline-block size-[12px] shrink-0 rounded-full border-[1.5px] border-black/10 border-t-black/65 animate-spin"
      aria-hidden
    />
  );
}

function DoneMark() {
  return (
    <span
      className="inline-flex size-[12px] shrink-0 items-center justify-center text-[10px] text-text-tertiary"
      aria-hidden
    >
      ✓
    </span>
  );
}

function Chevron({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 12 12"
      className={classNames(
        'size-12 shrink-0 text-[#C7C7CC] transition-transform duration-200',
        open ? 'rotate-180' : 'rotate-0',
      )}
      aria-hidden
    >
      <path
        d="M3 4.25 L6 7.25 L9 4.25"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function StatusGlyph({
  status,
  live,
}: {
  status?: StepUiStatus | SubTaskUiStatus;
  live?: boolean;
}) {
  if (live || status === 'RUNNING') {
    return <Spinner />;
  }
  if (status === 'FAILED') {
    return (
      <span className="inline-block size-[6px] shrink-0 rounded-full bg-[#FF3B30]" />
    );
  }
  if (status === 'DEGRADED') {
    return (
      <span className="inline-block size-[6px] shrink-0 rounded-full bg-[#FF9F0A]" />
    );
  }
  if (status === 'COMPLETED') {
    return <DoneMark />;
  }
  return (
    <span className="inline-block size-[6px] shrink-0 rounded-full bg-black/15" />
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
  return name || id || '专家';
}

function isNoiseLine(line: TraceLine): boolean {
  const text = (line.text || '').trim();
  if (!text) {
    return true;
  }
  if (
    /^(STATUS|THOUGHT|OUTPUT|ERROR|RUNNING|COMPLETED|FAILED|PLANNED|SKIPPED|DEGRADED|SUCCESS|PARTIAL|INTERRUPTED|IDLE)$/i.test(
      text,
    )
  ) {
    return true;
  }
  if (
    text.startsWith('{') &&
    (text.includes('"status"') ||
      text.includes('"eventType"') ||
      text.includes('"output"'))
  ) {
    return true;
  }
  return false;
}

function visibleText(line: TraceLine): string {
  return (line.text || '')
    .replace(/^\s*\[(STATUS|THOUGHT|OUTPUT|ERROR)\]\s*/i, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function visibleLines(lines: TraceLine[]): TraceLine[] {
  return lines.filter((line) => !isNoiseLine(line) && visibleText(line));
}

function humanStepNote(step: StepUiState): string | null {
  const parts: string[] = [];
  if (step.stepMode === 'PARALLEL_AGENTS') {
    parts.push('并行');
  }
  if (step.reviewing) {
    parts.push('复核中');
  }
  if (step.fallbackActive) {
    parts.push('换人接手');
  }
  if (step.status === 'DEGRADED') {
    parts.push('部分完成');
  }
  if (typeof step.retryNo === 'number' && step.retryNo > 0) {
    parts.push('再次尝试');
  }
  return parts.length > 0 ? parts.join(' · ') : null;
}

function humanSubNote(sub: SubTaskUiState): string | null {
  if (sub.retryNo > 0) {
    return '再次尝试';
  }
  return null;
}

function liveProgressHint(
  state: OrchestrationUiState,
  steps: StepUiState[],
): string {
  for (const step of steps) {
    if (step.status !== 'RUNNING') continue;
    const runningSubs = Object.values(step.subTasks ?? {}).filter(
      (item) => item.status === 'RUNNING',
    );
    if (runningSubs.length > 0) {
      const names = runningSubs.map((item) => displayAgentName(item)).join('、');
      return `${names} 正在协作`;
    }
    if (step.fallbackActive) {
      return `${displayAgentName(step)} 正在接手`;
    }
    if (step.reviewing) {
      return `主规划正在复核 ${displayAgentName(step)} 的结果`;
    }
    const thought = [...visibleLines(step.lines)].reverse().find((line) => {
      return line.kind === 'THOUGHT' || line.kind === 'STATUS';
    });
    if (thought) {
      const text = visibleText(thought);
      return `${displayAgentName(step)} · ${text.slice(0, 72)}${text.length > 72 ? '…' : ''}`;
    }
    return `${displayAgentName(step)} 进行中`;
  }
  const lastMain = [...visibleLines(state.main.lines)].reverse()[0];
  if (lastMain) {
    const text = visibleText(lastMain);
    return `${text.slice(0, 56)}${text.length > 56 ? '…' : ''}`;
  }
  if (steps.length > 0) {
    return `已邀请 ${steps.map(displayAgentName).join('、')}`;
  }
  return '正在安排专家…';
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
        steps,
      };
    }
  }
  return null;
}

function headerCopy(thinking: boolean, terminal: OrchestrationUiState['terminalStatus']) {
  if (thinking) {
    return '正在协同';
  }
  if (terminal === 'FAILED') {
    return '协同未完成';
  }
  if (terminal === 'INTERRUPTED') {
    return '协同已中断';
  }
  return '协同完成';
}

function TraceStream({
  lines,
  live,
}: {
  lines: TraceLine[];
  live?: boolean;
}) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const visible = visibleLines(lines);

  useEffect(() => {
    const node = scrollerRef.current;
    if (!node) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [visible, live]);

  if (visible.length === 0) {
    return live ? (
      <div className="px-2 py-4 text-[12px] leading-[18px] text-text-tertiary">
        正在思考
        <span className="ml-4 inline-block h-[12px] w-[2px] translate-y-[1px] bg-black/35 animate-pulse" />
      </div>
    ) : null;
  }

  return (
    <div
      ref={scrollerRef}
      className="max-h-[240px] overflow-auto py-4 pr-4"
    >
      {visible.map((line, index) => {
        const last = index === visible.length - 1;
        const text = visibleText(line);
        const tone =
          line.kind === 'ERROR'
            ? 'text-[#FF3B30]'
            : line.kind === 'OUTPUT'
              ? 'text-text-primary'
              : line.kind === 'STATUS'
                ? 'text-text-tertiary'
                : 'text-text-secondary';
        return (
          <div
            key={line.sequence}
            className={classNames(
              'mb-6 last:mb-0 text-[13px] leading-[20px] whitespace-pre-wrap break-words',
              tone,
            )}
          >
            {text}
            {line.truncated ? '…' : ''}
            {live && last ? (
              <span className="ml-3 inline-block h-[12px] w-[1.5px] translate-y-[1px] bg-black/40 animate-pulse" />
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

function FoldRow({
  title,
  note,
  open,
  onToggle,
  status,
  live,
  testId,
  children,
}: {
  title: string;
  note?: string | null;
  open: boolean;
  onToggle?: () => void;
  status?: StepUiStatus | SubTaskUiStatus;
  live?: boolean;
  testId: string;
  children: ReactNode;
}) {
  return (
    <div data-testid={testId}>
      <button
        type="button"
        className="flex w-full items-center gap-8 py-6 text-left"
        onClick={onToggle}
        aria-expanded={open}
      >
        <StatusGlyph status={status} live={live} />
        <span className="min-w-0 flex-1 truncate text-[13px] font-medium text-text-primary">
          {title}
        </span>
        {note ? (
          <span className="shrink-0 text-[11px] text-text-tertiary">{note}</span>
        ) : null}
        <Chevron open={open} />
      </button>
      {open ? <div className="pb-4 pl-[20px]">{children}</div> : null}
    </div>
  );
}

/**
 * ChatGPT / Cursor style collapsible work panel.
 * Default: master collapsed. Opening master expands every nested fold.
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
  const header = headerCopy(thinking, state.terminalStatus);
  const latest = latestAttemptSteps(state);
  const liveHint = thinking ? liveProgressHint(state, latest?.steps ?? []) : '';
  const inviteNames = (latest?.steps ?? [])
    .map(displayAgentName)
    .filter(Boolean);

  return (
    <div
      className="phase2-orchestration-timeline w-full max-w-[720px]"
      data-testid="orchestration-timeline"
    >
      <button
        type="button"
        className="flex w-full items-center gap-8 py-2 text-left"
        onClick={onToggleMaster}
        aria-expanded={state.masterOpen}
        data-testid="orchestration-master-toggle"
      >
        {thinking ? <Spinner /> : <DoneMark />}
        <span className="min-w-0 flex-1">
          <span
            className="block text-[13px] text-text-secondary"
            data-testid="orchestration-completion-status"
          >
            {header}
          </span>
          {liveHint ? (
            <span
              className="mt-2 block truncate text-[12px] leading-[18px] text-text-tertiary"
              data-testid="orchestration-live-hint"
            >
              {liveHint}
            </span>
          ) : null}
        </span>
        <Chevron open={state.masterOpen} />
      </button>

      {state.masterOpen ? (
        <div
          className="mt-4 ml-[6px] border-l border-black/[0.08] pl-14"
          data-testid="orchestration-master-body"
        >
          {inviteNames.length > 0 ? (
            <div
              className="mb-8 text-[12px] leading-[18px] text-text-tertiary"
              data-testid="orchestration-plan-steps"
            >
              主规划邀请 {inviteNames.join('、')} 一起完成
            </div>
          ) : null}

          <FoldRow
            title="主规划"
            open={state.main.open}
            onToggle={onToggleMain}
            live={thinking && (latest?.steps.length ?? 0) === 0}
            testId="orchestration-main"
          >
            <TraceStream
              lines={state.main.lines}
              live={thinking && (latest?.steps.length ?? 0) === 0}
            />
          </FoldRow>

          {latest?.steps.map((step) => {
            const subTasks = Object.values(step.subTasks ?? {});
            const stepLive = thinking && step.status === 'RUNNING';
            return (
              <FoldRow
                key={`${latest.attemptNo}-${step.stepId}`}
                title={displayAgentName(step)}
                note={humanStepNote(step)}
                open={step.open}
                status={step.status}
                live={stepLive}
                onToggle={() => onToggleStep?.(latest.attemptNo, step.stepId)}
                testId={`orchestration-step-${step.stepId}`}
              >
                {step.objective ? (
                  <div className="mb-4 text-[12px] leading-[18px] text-text-tertiary">
                    {step.objective}
                  </div>
                ) : null}
                {subTasks.length > 1 ? (
                  <div className="mb-4 text-[12px] leading-[18px] text-text-tertiary">
                    同时与{' '}
                    {subTasks.map((sub) => displayAgentName(sub)).join('、')}{' '}
                    协作
                  </div>
                ) : null}
                <TraceStream lines={step.lines} live={stepLive} />
                {subTasks.map((sub) => {
                  const subLive = thinking && sub.status === 'RUNNING';
                  return (
                    <FoldRow
                      key={sub.subTaskId}
                      title={displayAgentName(sub)}
                      note={humanSubNote(sub)}
                      open={sub.open}
                      status={sub.status}
                      live={subLive}
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
                        <div className="mb-4 text-[12px] leading-[18px] text-text-tertiary">
                          {sub.objective}
                        </div>
                      ) : null}
                      <TraceStream lines={sub.lines} live={subLive} />
                    </FoldRow>
                  );
                })}
              </FoldRow>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
