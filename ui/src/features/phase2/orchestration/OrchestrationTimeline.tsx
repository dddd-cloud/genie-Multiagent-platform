import { useEffect, useRef, type ReactNode } from 'react';
import classNames from 'classnames';
import ThoughtMarkdown from './ThoughtMarkdown';
import {
  displayAgentName,
  humanErrorMessage,
  looksLikeInternalStatus,
  looksLikeProtocolDump,
  stripProtocolTokens,
} from './orchestrationCopy';
import type {
  OrchestrationUiState,
  StepUiState,
  StepUiStatus,
  SubTaskUiStatus,
  TraceLine,
} from './types';

export interface OrchestrationTimelineProps {
  state: OrchestrationUiState;
  onToggleMaster?: () => void;
}

function Spinner({ size = 12 }: { size?: number }) {
  return (
    <span
      className="inline-block shrink-0 rounded-full border-[1.5px] border-black/[0.08] border-t-brand animate-spin"
      style={{ width: size, height: size }}
      aria-hidden
    />
  );
}

function Chevron({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 12 12"
      className={classNames(
        'size-12 shrink-0 text-text-tertiary transition-transform duration-200',
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

function isNoiseLine(line: TraceLine): boolean {
  const text = (line.text || '').trim();
  if (!text) {
    return true;
  }
  if (looksLikeProtocolDump(text) || looksLikeInternalStatus(text)) {
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
  const raw = (line.text || '').replace(
    /^\s*\[(STATUS|THOUGHT|OUTPUT|ERROR)\]\s*/i,
    '',
  );
  if (line.kind === 'ERROR') {
    return humanErrorMessage(stripProtocolTokens(raw));
  }
  if (line.kind === 'THOUGHT' || line.kind === 'OUTPUT') {
    return stripProtocolTokens(raw).replace(/[ \t]+\n/g, '\n').trim();
  }
  return stripProtocolTokens(raw.replace(/\s+/g, ' '));
}

function visibleLines(lines: TraceLine[]): TraceLine[] {
  return lines.filter((line) => !isNoiseLine(line) && visibleText(line));
}

type BubbleBlock =
  | { kind: 'STATUS'; text: string }
  | { kind: 'ERROR'; text: string }
  | { kind: 'MARKDOWN'; text: string };

function bubbleBlocks(lines: TraceLine[]): BubbleBlock[] {
  const blocks: BubbleBlock[] = [];
  for (const line of visibleLines(lines)) {
    const text = visibleText(line);
    if (line.kind === 'STATUS') {
      blocks.push({ kind: 'STATUS', text });
      continue;
    }
    if (line.kind === 'ERROR') {
      blocks.push({ kind: 'ERROR', text });
      continue;
    }
    const last = blocks[blocks.length - 1];
    if (last?.kind === 'MARKDOWN') {
      last.text = `${last.text}${last.text.endsWith('\n') ? '' : '\n\n'}${text}`;
    } else {
      blocks.push({ kind: 'MARKDOWN', text });
    }
  }
  return blocks;
}

function avatarInitial(name: string, tone: 'main' | 'expert'): string {
  const trimmed = name.trim();
  if (tone === 'main') {
    return '主';
  }
  return trimmed ? trimmed.slice(0, 1) : '专';
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

function headerCopy(
  thinking: boolean,
  terminal: OrchestrationUiState['terminalStatus'],
) {
  if (thinking) {
    return '查看思考过程';
  }
  if (terminal === 'FAILED') {
    return '思考过程 · 未完成';
  }
  if (terminal === 'INTERRUPTED') {
    return '思考过程 · 已中断';
  }
  return '思考过程';
}

function AgentAvatar({
  name,
  tone,
  thinking,
}: {
  name: string;
  tone?: 'main' | 'expert';
  thinking?: boolean;
}) {
  return (
    <span className="relative mt-2 inline-flex shrink-0 items-center">
      <span
        className={classNames(
          'inline-flex size-[30px] items-center justify-center rounded-full text-[12.5px] font-semibold shadow-[0_1px_2px_rgba(0,0,0,0.06)] ring-1',
          tone === 'main'
            ? 'bg-brand text-white ring-black/[0.06]'
            : 'bg-[#EEF2FF] text-[#4338CA] ring-[#4338CA]/[0.08]',
        )}
        aria-hidden
      >
        {avatarInitial(name, tone === 'main' ? 'main' : 'expert')}
      </span>
      {thinking ? (
        <span
          className="absolute -bottom-2 -right-2 flex size-[16px] items-center justify-center rounded-full bg-surface shadow-[0_0_0_2px_var(--color-surface)]"
          data-testid="agent-thinking-spinner"
        >
          <Spinner size={10} />
        </span>
      ) : null}
    </span>
  );
}

const STATUS_CHIP_STYLES: Record<ThoughtPhase, string> = {
  thinking: 'bg-[#EEF2FF] text-[#4338CA]',
  waiting: 'bg-black/[0.04] text-text-tertiary',
  done: 'bg-black/[0.04] text-text-tertiary',
  failed: 'bg-danger-soft text-danger',
  skipped: 'bg-black/[0.04] text-text-tertiary',
  degraded: 'bg-black/[0.04] text-warning',
};

function StatusChip({ phase }: { phase: ThoughtPhase }) {
  return (
    <span
      className={classNames(
        'inline-flex items-center gap-4 rounded-full px-6 py-[2px] text-[11px] font-medium leading-[16px]',
        STATUS_CHIP_STYLES[phase],
      )}
    >
      {phase === 'thinking' ? (
        <span className="size-4 shrink-0 rounded-full bg-[#4338CA] animate-pulse" aria-hidden />
      ) : null}
      {thoughtPhaseLabel(phase)}
    </span>
  );
}

type ThoughtPhase = 'thinking' | 'waiting' | 'done' | 'failed' | 'skipped' | 'degraded';

function thoughtPhase(
  status: StepUiStatus | SubTaskUiStatus,
  overallThinking: boolean,
): ThoughtPhase {
  if (status === 'RUNNING') {
    return 'thinking';
  }
  if (status === 'PLANNED') {
    return overallThinking ? 'waiting' : 'done';
  }
  if (status === 'FAILED') {
    return 'failed';
  }
  if (status === 'SKIPPED') {
    return 'skipped';
  }
  if (status === 'DEGRADED') {
    return 'degraded';
  }
  return 'done';
}

function thoughtPhaseLabel(phase: ThoughtPhase): string {
  switch (phase) {
    case 'thinking':
      return '思考中';
    case 'waiting':
      return '等待中';
    case 'failed':
      return '未完成';
    case 'skipped':
      return '已跳过';
    case 'degraded':
      return '部分完成';
    default:
      return '思考完成';
  }
}

function ChatMessage({
  name,
  tone,
  phase,
  lines,
  testId,
}: {
  name: string;
  tone: 'main' | 'expert';
  phase: ThoughtPhase;
  lines: TraceLine[];
  testId: string;
}) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const blocks = bubbleBlocks(lines);
  const thinking = phase === 'thinking';
  const waiting = phase === 'waiting';

  useEffect(() => {
    const node = scrollerRef.current;
    if (!node) {
      return;
    }
    node.scrollTop = node.scrollHeight;
  }, [blocks, thinking]);

  return (
    <div className="flex items-start gap-12 py-12" data-testid={testId}>
      <AgentAvatar name={name} tone={tone} thinking={thinking} />
      <div className="min-w-0 flex-1 pt-2">
        <div className="mb-6 flex flex-wrap items-center gap-8 text-[13px] leading-[18px]">
          <span className="font-medium text-text-primary">{name}</span>
          <span data-testid={`${testId}-status`}>
            <StatusChip phase={phase} />
          </span>
        </div>
        <div ref={scrollerRef} className="max-h-[320px] overflow-auto pr-4">
          {blocks.length === 0 ? (
            thinking ? (
              <ThinkingPlaceholder label="正在思考" />
            ) : waiting ? (
              <ThinkingPlaceholder label="排队等待" muted />
            ) : null
          ) : (
            blocks.map((block, index) => {
              const last = index === blocks.length - 1;
              if (block.kind === 'STATUS') {
                return (
                  <div
                    key={`status-${index}`}
                    className="mb-8 last:mb-0 flex items-start gap-6 text-[12px] leading-[20px] text-text-tertiary"
                  >
                    <span className="mt-[7px] size-4 shrink-0 rounded-full bg-black/15" aria-hidden />
                    <span>{block.text}</span>
                  </div>
                );
              }
              if (block.kind === 'ERROR') {
                return (
                  <div
                    key={`error-${index}`}
                    className="mb-8 last:mb-0 flex items-start gap-6 rounded-lg bg-black/[0.03] px-10 py-8 text-[13px] leading-[22px] text-text-secondary"
                  >
                    <span className="mt-[6px] size-6 shrink-0 rounded-full bg-warning/70" aria-hidden />
                    <span>{block.text}</span>
                  </div>
                );
              }
              return (
                <div key={`md-${index}`} className="mb-8 last:mb-0">
                  <ThoughtMarkdown text={block.text} />
                  {last && thinking ? (
                    <span className="ml-3 inline-block h-[12px] w-[1.5px] translate-y-[1px] bg-black/40 animate-pulse" />
                  ) : null}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}

function ThinkingPlaceholder({ label, muted }: { label: string; muted?: boolean }) {
  return (
    <div className="flex items-center gap-6 text-[13px] leading-[22px] text-text-tertiary">
      <span>{label}</span>
      {muted ? null : (
        <span className="inline-flex items-center gap-3" aria-hidden>
          <span className="size-4 rounded-full bg-text-tertiary/50 animate-bounce [animation-delay:-0.3s]" />
          <span className="size-4 rounded-full bg-text-tertiary/50 animate-bounce [animation-delay:-0.15s]" />
          <span className="size-4 rounded-full bg-text-tertiary/50 animate-bounce" />
        </span>
      )}
    </div>
  );
}

function Notice({ children, testId }: { children: ReactNode; testId: string }) {
  return (
    <div
      className="my-4 flex items-center gap-10 py-2"
      data-testid={testId}
    >
      <span className="h-[1px] flex-1 bg-border" aria-hidden />
      <span className="shrink-0 text-center text-[11.5px] leading-[16px] text-text-tertiary">
        {children}
      </span>
      <span className="h-[1px] flex-1 bg-border" aria-hidden />
    </div>
  );
}

function expertName(step: StepUiState, index: number): string {
  return displayAgentName({
    agentName: step.agentName,
    agentId: step.agentId,
    fallback: `专家${index + 1}`,
  });
}

/**
 * Group-chat collaboration view: main agent and experts talk in one thread.
 */
export default function OrchestrationTimeline({
  state,
  onToggleMaster,
}: OrchestrationTimelineProps) {
  if (state.route !== null && state.route !== 'ORCHESTRATED') {
    return null;
  }

  const thinking =
    state.phaseLabel !== 'done' && state.terminalStatus === 'RUNNING';
  const header = headerCopy(thinking, state.terminalStatus);
  const latest = latestAttemptSteps(state);
  const steps = latest?.steps ?? [];
  const summarizing = thinking && state.summaryStatus === 'RUNNING';
  const mainVisible = visibleLines(state.main.lines).length > 0;
  const mainThinking =
    thinking && (summarizing || (steps.length === 0 && mainVisible));
  const showMain = mainVisible || Boolean(mainThinking);
  const soloConversation =
    state.routeReasonCode === 'SOLO_AGENT' ||
    state.routeReasonCode === 'AUTO_SINGLE_AGENT' ||
    state.routeReasonCode === 'SINGLE_CAPABILITY' ||
    state.routeReasonCode === 'MATCHED_SPECIALIST' ||
    state.routeReasonCode === 'ONLY_ONE_CANDIDATE';

  return (
    <div
      className="phase2-orchestration-timeline w-full max-w-[720px]"
      data-testid="orchestration-timeline"
    >
      <button
        type="button"
        className="group flex w-full items-center gap-10 rounded-lg px-6 py-6 -mx-6 text-left transition-colors hover:bg-black/[0.025]"
        onClick={onToggleMaster}
        aria-expanded={state.masterOpen}
        data-testid="orchestration-master-toggle"
      >
        {thinking ? <Spinner /> : null}
        <span className="min-w-0 flex-1">
          <span
            className="block text-[13px] font-medium text-text-secondary group-hover:text-text-primary"
            data-testid="orchestration-completion-status"
          >
            {header}
          </span>
          {thinking && !state.masterOpen ? (
            <span className="mt-2 block text-[12px] leading-[18px] text-text-tertiary">
              协同进行中
            </span>
          ) : null}
        </span>
        <Chevron open={state.masterOpen} />
      </button>

      {state.masterOpen ? (
        <div className="mt-6" data-testid="orchestration-master-body-wrap">
          <div className="h-[1px] bg-border" aria-hidden />
          <div
            className="pt-10"
            data-testid="orchestration-master-body"
          >
          {showMain ? (
            <ChatMessage
              name="主规划"
              tone="main"
              phase={mainThinking ? 'thinking' : 'done'}
              lines={state.main.lines}
              testId="orchestration-message-main"
            />
          ) : null}

          {steps.map((step, index) => {
            const subTasks = Object.values(step.subTasks ?? {});
            if (subTasks.length > 0) {
              const names = subTasks.map((sub, subIndex) =>
                displayAgentName({
                  agentName: sub.agentName,
                  agentId: sub.agentId,
                  fallback: `专家${subIndex + 1}`,
                }),
              );
              const parallelLive =
                step.status === 'RUNNING' ||
                subTasks.some((sub) => sub.status !== 'PLANNED');
              return (
                <div key={`${latest?.attemptNo}-${step.stepId}`}>
                  {parallelLive ? (
                    <Notice testId={`orchestration-handoff-parallel-${step.stepId}`}>
                      主规划安排 {names.join('、')} 同时开始
                    </Notice>
                  ) : thinking ? (
                    <Notice testId={`orchestration-handoff-parallel-queued-${step.stepId}`}>
                      随后将同时邀请 {names.join('、')}
                    </Notice>
                  ) : null}
                  {subTasks.map((sub, subIndex) => {
                    const subName = names[subIndex];
                    const phase = thoughtPhase(sub.status, thinking);
                    return (
                      <div key={sub.subTaskId}>
                        <ChatMessage
                          name={subName}
                          tone="expert"
                          phase={phase}
                          lines={sub.lines}
                          testId={`orchestration-message-${sub.subTaskId}`}
                        />
                        {sub.status === 'COMPLETED' || sub.status === 'FAILED' ? (
                          <Notice testId={`orchestration-handoff-report-${sub.subTaskId}`}>
                            {subName} 已回报主规划
                          </Notice>
                        ) : null}
                      </div>
                    );
                  })}
                </div>
              );
            }

            const name = expertName(step, index);
            const phase = thoughtPhase(step.status, thinking);
            return (
              <div key={`${latest?.attemptNo}-${step.stepId}`}>
                {!soloConversation && step.status !== 'PLANNED' ? (
                  <Notice testId={`orchestration-handoff-assign-${step.stepId}`}>
                    主规划邀请 {name} 处理：{step.objective || '当前任务'}
                  </Notice>
                ) : null}
                <ChatMessage
                  name={name}
                  tone="expert"
                  phase={phase}
                  lines={step.lines}
                  testId={`orchestration-message-${step.stepId}`}
                />
                {!soloConversation &&
                (step.status === 'COMPLETED' ||
                  step.status === 'FAILED' ||
                  step.status === 'DEGRADED') ? (
                  <Notice testId={`orchestration-handoff-report-${step.stepId}`}>
                    {name} 已回报主规划
                  </Notice>
                ) : null}
              </div>
            );
          })}

          {state.summaryStatus !== 'IDLE' && !soloConversation ? (
            <Notice testId="orchestration-handoff-summary">
              {state.summaryStatus === 'RUNNING'
                ? '主规划正在汇总各位专家的结论'
                : '主规划已开始回复你'}
            </Notice>
          ) : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}
