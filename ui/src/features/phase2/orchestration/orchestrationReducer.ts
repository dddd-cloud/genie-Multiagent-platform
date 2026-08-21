import {
  ORCHESTRATION_EVENT_TYPES,
  type OrchestrationEvent,
  type OrchestrationEventType,
  type OrchestrationPlanStepView,
  type StepMode,
} from '@/contracts';
import { looksLikeUuid } from './orchestrationCopy';
import type { OrchestrationTrace } from './parseOrchestrationTrace';
import type {
  AttemptUiState,
  OrchestrationUiState,
  StepUiState,
  SubTaskUiState,
  TraceLine,
} from './types';

const EVENT_TYPE_SET = new Set<string>(ORCHESTRATION_EVENT_TYPES);

export function createInitialOrchestrationState(): OrchestrationUiState {
  return {
    route: null,
    routeReasonCode: null,
    attempts: {},
    summaryStatus: 'IDLE',
    terminalStatus: 'RUNNING',
    lastSequence: 0,
    lastTraceSequence: 0,
    seenEventIds: {},
    recoveryWarnings: [],
    masterOpen: false,
    main: {
      open: false,
      lines: []
    },
    phaseLabel: 'thinking',
    schemaVersion: null,
  };
}

function withWarning(
  state: OrchestrationUiState,
  warning: string,
): OrchestrationUiState {
  return {
    ...state,
    recoveryWarnings: [...state.recoveryWarnings, warning],
  };
}

const PARALLEL_LIVE_EVENT_TYPES = new Set([
  'PARALLEL_STARTED',
  'SUBTASK_STARTED',
  'SUBTASK_COMPLETED',
  'SUBTASK_FAILED',
]);

function acknowledgeEvent(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
  warning?: string,
): OrchestrationUiState {
  const next: OrchestrationUiState = {
    ...state,
    schemaVersion: state.schemaVersion ?? event.schemaVersion,
    lastSequence: Math.max(state.lastSequence, event.sequence),
    seenEventIds: {
      ...state.seenEventIds,
      [event.eventId]: true,
    },
  };
  if (warning) {
    next.recoveryWarnings = [...state.recoveryWarnings, warning];
  }
  return next;
}

function isTerminal(state: OrchestrationUiState): boolean {
  return state.terminalStatus !== 'RUNNING';
}

function maxAttemptNo(state: OrchestrationUiState): number {
  let max = 0;
  for (const key of Object.keys(state.attempts)) {
    const n = Number(key);
    if (Number.isFinite(n) && n > max) max = n;
  }
  return max;
}

function countRunningTopLevelSteps(state: OrchestrationUiState): number {
  let count = 0;
  for (const attempt of Object.values(state.attempts)) {
    for (const step of Object.values(attempt.steps)) {
      if (step.status === 'RUNNING') count += 1;
    }
  }
  return count;
}

function attemptHasFailedStep(attempt: AttemptUiState): boolean {
  return Object.values(attempt.steps).some((s) => s.status === 'FAILED');
}

function emptySubTasks(): Record<string, SubTaskUiState> {
  return {};
}

function planStepToUi(
  step: OrchestrationPlanStepView,
  open = false,
): StepUiState {
  const subTasks: Record<string, SubTaskUiState> = {};
  for (const st of step.subTasks ?? []) {
    subTasks[st.subTaskId] = {
      subTaskId: st.subTaskId,
      agentId: st.agentId,
      agentName: st.agentName,
      objective: st.objective,
      status: 'PLANNED',
      retryNo: 0,
      errorCode: null,
      lines: [],
      open,
    };
  }
  return {
    stepId: step.stepId,
    agentId: step.agentId ?? '',
    agentName: step.agentName ?? '',
    objective: step.objective,
    status: 'PLANNED',
    stepMode: step.mode ?? null,
    errorCode: null,
    retryNo: null,
    reviewing: false,
    fallbackActive: false,
    lines: [],
    open,
    subTasks,
  };
}

function cloneSubTasks(
  subTasks: Record<string, SubTaskUiState>,
): Record<string, SubTaskUiState> {
  const out: Record<string, SubTaskUiState> = {};
  for (const [id, st] of Object.entries(subTasks)) {
    out[id] = { ...st };
  }
  return out;
}

function cloneAttempts(
  attempts: Record<number, AttemptUiState>,
): Record<number, AttemptUiState> {
  const out: Record<number, AttemptUiState> = {};
  for (const [key, attempt] of Object.entries(attempts)) {
    const steps: Record<string, StepUiState> = {};
    for (const [stepId, step] of Object.entries(attempt.steps)) {
      steps[stepId] = {
        ...step,
        subTasks: cloneSubTasks(step.subTasks ?? emptySubTasks()),
      };
    }
    out[Number(key)] = {
      attemptNo: attempt.attemptNo,
      steps,
    };
  }
  return out;
}

function lastLineSequence(lines: TraceLine[] | undefined): number {
  if (!lines || lines.length === 0) {
    return 0;
  }
  return lines[lines.length - 1].sequence;
}

function lastSequenceForTrace(
  state: OrchestrationUiState,
  trace: OrchestrationTrace,
): number {
  if (trace.scope === 'MAIN') {
    return lastLineSequence(state.main.lines);
  }
  const stepId = trace.stepId;
  if (!stepId) {
    return 0;
  }
  const attemptNo =
    typeof trace.attemptNo === 'number' && state.attempts[trace.attemptNo]
      ? trace.attemptNo
      : maxAttemptNo(state);
  const step = state.attempts[attemptNo]?.steps[stepId];
  if (!step) {
    return 0;
  }
  if (trace.scope === 'SUBTASK' && trace.subTaskId) {
    return lastLineSequence(step.subTasks?.[trace.subTaskId]?.lines);
  }
  return lastLineSequence(step.lines);
}

function nextTraceSequence(state: OrchestrationUiState, sequence: number): number {
  return Math.max(state.lastTraceSequence, sequence);
}

function appendTraceLine(
  lines: TraceLine[],
  trace: OrchestrationTrace,
): TraceLine[] {
  const nextLine: TraceLine = {
    sequence: trace.sequence,
    kind: trace.kind,
    text: trace.text,
    truncated: trace.truncated || undefined,
  };
  if (trace.append && lines.length > 0) {
    const last = lines[lines.length - 1];
    if (last.kind === trace.kind) {
      return [
        ...lines.slice(0, -1),
        {
          ...last,
          text: last.text + trace.text,
          truncated: last.truncated || trace.truncated || undefined,
          sequence: trace.sequence,
        },
      ];
    }
  }
  return [...lines, nextLine];
}

function updateStepStatus(
  state: OrchestrationUiState,
  attemptNo: number,
  stepId: string,
  patch: Partial<StepUiState>,
): OrchestrationUiState {
  const attempt = state.attempts[attemptNo];
  if (!attempt) return state;
  const step = attempt.steps[stepId];
  if (!step) return state;
  const attempts = cloneAttempts(state.attempts);
  attempts[attemptNo] = {
    ...attempt,
    steps: {
      ...attempt.steps,
      [stepId]: {
        ...step,
        ...patch,
        subTasks:
          patch.subTasks ??
          cloneSubTasks(step.subTasks ?? emptySubTasks()),
      },
    },
  };
  return {
    ...state,
    attempts,
  };
}

function updateSubTask(
  state: OrchestrationUiState,
  attemptNo: number,
  stepId: string,
  subTaskId: string,
  patch: Partial<SubTaskUiState>,
): OrchestrationUiState {
  const attempt = state.attempts[attemptNo];
  const step = attempt?.steps[stepId];
  if (!attempt || !step) return state;
  const existing = step.subTasks?.[subTaskId];
  if (!existing) return state;
  const subTasks = cloneSubTasks(step.subTasks);
  subTasks[subTaskId] = {
    ...existing,
    ...patch
  };
  return updateStepStatus(state, attemptNo, stepId, { subTasks });
}

function validateEnvelope(event: OrchestrationEvent): string | null {
  if (event.schemaVersion !== 1 && event.schemaVersion !== 2) {
    return 'invalid schemaVersion';
  }
  if (typeof event.eventId !== 'string' || event.eventId.length === 0) {
    return 'empty eventId';
  }
  if (typeof event.sequence !== 'number' || event.sequence < 1) {
    return 'invalid sequence';
  }
  if (!EVENT_TYPE_SET.has(event.eventType)) {
    return 'unknown eventType';
  }
  if (typeof event.requestId !== 'string' || event.requestId.length === 0) {
    return 'empty requestId';
  }
  if (typeof event.runId !== 'string' || event.runId.length === 0) {
    return 'empty runId';
  }
  return null;
}

function preferReadableAgentName(
  incoming: string | null | undefined,
  current: string | null | undefined,
  agentId: string,
): string {
  const pick = (value: string | null | undefined): string | null => {
    if (!value || value.trim().length === 0) return null;
    const trimmed = value.trim();
    if (looksLikeUuid(trimmed)) {
      return null;
    }
    if (trimmed === agentId) return null;
    return trimmed;
  };
  return pick(incoming) ?? pick(current) ?? agentId;
}

function activatePlanned<T extends string>(status: T): T | 'RUNNING' {
  return status === 'PLANNED' ? 'RUNNING' : status;
}

function applyRouteSelected(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  if (state.route !== null) {
    return acknowledgeEvent(
      state,
      event,
      `duplicate ROUTE_SELECTED ignored (${event.eventId})`,
    );
  }
  if (!event.route) {
    return acknowledgeEvent(
      state,
      event,
      `ROUTE_SELECTED missing route ignored (${event.eventId})`,
    );
  }
  return {
    ...acknowledgeEvent(state, event),
    route: event.route,
    routeReasonCode: event.reasonCode,
  };
}

function mergePlanSteps(
  existing: Record<string, StepUiState>,
  planSteps: OrchestrationPlanStepView[],
  defaultOpen = false,
): Record<string, StepUiState> {
  const steps: Record<string, StepUiState> = { ...existing };
  for (const step of planSteps) {
    const prev = steps[step.stepId];
    if (!prev) {
      steps[step.stepId] = planStepToUi(step, defaultOpen);
      continue;
    }
    const mergedSubTasks = cloneSubTasks(prev.subTasks ?? emptySubTasks());
    for (const st of step.subTasks ?? []) {
      if (!mergedSubTasks[st.subTaskId]) {
        mergedSubTasks[st.subTaskId] = {
          subTaskId: st.subTaskId,
          agentId: st.agentId,
          agentName: st.agentName,
          objective: st.objective,
          status: 'PLANNED',
          retryNo: 0,
          errorCode: null,
          lines: [],
          open: defaultOpen,
        };
      } else {
        const cur = mergedSubTasks[st.subTaskId];
        mergedSubTasks[st.subTaskId] = {
          ...cur,
          agentId: st.agentId || cur.agentId,
          agentName: preferReadableAgentName(
            st.agentName,
            cur.agentName,
            st.agentId || cur.agentId,
          ),
          objective:
            st.objective && st.objective.trim().length > 0
              ? st.objective
              : cur.objective,
        };
      }
    }
    steps[step.stepId] = {
      ...prev,
      agentId: step.agentId || prev.agentId,
      agentName: preferReadableAgentName(
        step.agentName,
        prev.agentName,
        step.agentId || prev.agentId || '',
      ),
      objective:
        step.objective && step.objective.trim().length > 0
          ? step.objective
          : prev.objective,
      stepMode: step.mode ?? prev.stepMode ?? null,
      subTasks: mergedSubTasks,
    };
  }
  return steps;
}

function applyPlanCreated(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  if (attemptNo === null) {
    return acknowledgeEvent(
      state,
      event,
      `PLAN_CREATED invalid attemptNo ignored (${event.eventId})`,
    );
  }
  if (event.schemaVersion === 1) {
    if (attemptNo < 1 || attemptNo > 3) {
      return acknowledgeEvent(
        state,
        event,
        `PLAN_CREATED invalid attemptNo ignored (${event.eventId})`,
      );
    }
  } else if (attemptNo !== 1) {
    return acknowledgeEvent(
      state,
      event,
      `PLAN_CREATED v2 attemptNo must be 1 ignored (${event.eventId})`,
    );
  }
  if (!event.steps || event.steps.length === 0) {
    return acknowledgeEvent(
      state,
      event,
      `PLAN_CREATED empty steps ignored (${event.eventId})`,
    );
  }
  const existing = state.attempts[attemptNo];
  const attempts = cloneAttempts(state.attempts);

  if (existing && Object.keys(existing.steps).length > 0) {
    attempts[attemptNo] = {
      attemptNo,
      steps: mergePlanSteps(existing.steps, event.steps, state.masterOpen),
    };
    return {
      ...acknowledgeEvent(state, event),
      attempts,
    };
  }

  const steps: Record<string, StepUiState> = {};
  const reveal = state.terminalStatus === 'RUNNING' && Object.keys(existing?.steps ?? {}).length === 0;
  for (const step of event.steps) {
    steps[step.stepId] = planStepToUi(step, reveal || state.masterOpen);
  }

  attempts[attemptNo] = {
    attemptNo,
    steps
  };
  return {
    ...acknowledgeEvent(state, event),
    attempts,
    masterOpen: reveal ? true : state.masterOpen,
    main: {
      ...state.main,
      open: reveal ? true : state.main.open,
    },
  };
}

function resolveStepMode(
  step: StepUiState,
  event: OrchestrationEvent,
): StepMode | null | undefined {
  return event.stepMode ?? step.stepMode ?? null;
}

function applyStepStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  const stepId = event.stepId;
  if (attemptNo === null || !stepId) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_STARTED missing attempt/step ignored (${event.eventId})`,
    );
  }
  const attempt = state.attempts[attemptNo];
  if (!attempt) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_STARTED unknown attempt ignored (${event.eventId})`,
    );
  }
  if (attemptHasFailedStep(attempt)) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_STARTED after STEP_FAILED ignored (${event.eventId})`,
    );
  }
  const step = attempt.steps[stepId];
  if (!step) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_STARTED unknown step ignored (${event.eventId})`,
    );
  }
  if (step.status !== 'PLANNED') {
    return acknowledgeEvent(
      state,
      event,
      `STEP_STARTED from illegal status ${step.status} ignored (${event.eventId})`,
    );
  }
  // Top-level steps remain strictly serial even in v2.
  if (countRunningTopLevelSteps(state) > 0) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_STARTED while another step RUNNING ignored (${event.eventId})`,
    );
  }

  const agentName = preferReadableAgentName(
    event.agentName,
    step.agentName,
    step.agentId,
  );
  const next = updateStepStatus(state, attemptNo, stepId, {
    status: 'RUNNING',
    agentId: event.agentId ?? step.agentId,
    agentName,
    stepMode: resolveStepMode(step, event),
    retryNo: event.retryNo ?? step.retryNo ?? 0,
    reviewing: false,
    fallbackActive: false,
  });
  return acknowledgeEvent(next, event);
}

function applyStepTerminal(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
  nextStatus: 'COMPLETED' | 'FAILED' | 'SKIPPED' | 'DEGRADED',
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  const stepId = event.stepId;
  if (attemptNo === null || !stepId) {
    return acknowledgeEvent(
      state,
      event,
      `${event.eventType} missing attempt/step ignored (${event.eventId})`,
    );
  }
  const attempt = state.attempts[attemptNo];
  if (!attempt) {
    return acknowledgeEvent(
      state,
      event,
      `${event.eventType} unknown attempt ignored (${event.eventId})`,
    );
  }
  const step = attempt.steps[stepId];
  if (!step) {
    return acknowledgeEvent(
      state,
      event,
      `${event.eventType} unknown step ignored (${event.eventId})`,
    );
  }

  if (step.status !== 'PLANNED' && step.status !== 'RUNNING') {
    return acknowledgeEvent(
      state,
      event,
      `${event.eventType} from illegal status ${step.status} ignored (${event.eventId})`,
    );
  }

  if (nextStatus === 'FAILED' && !event.errorCode) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_FAILED missing errorCode ignored (${event.eventId})`,
    );
  }

  const agentName = preferReadableAgentName(
    event.agentName,
    step.agentName,
    step.agentId,
  );
  const next = updateStepStatus(state, attemptNo, stepId, {
    status: nextStatus,
    agentId: event.agentId ?? step.agentId,
    agentName,
    errorCode: nextStatus === 'FAILED' ? event.errorCode : step.errorCode,
    reviewing: false,
    fallbackActive: nextStatus === 'DEGRADED' ? false : step.fallbackActive,
    stepMode: resolveStepMode(step, event),
  });
  return acknowledgeEvent(next, event);
}

function applyReplanStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  // v2 never starts REPLAN; still accept for historical snapshot hydrate.
  if (event.schemaVersion === 2) {
    return acknowledgeEvent(
      state,
      event,
      `REPLAN_STARTED ignored on v2 (${event.eventId})`,
    );
  }
  const attemptNo = event.attemptNo;
  if (attemptNo === null || attemptNo < 1 || attemptNo > 3) {
    return acknowledgeEvent(
      state,
      event,
      `REPLAN_STARTED invalid attemptNo ignored (${event.eventId})`,
    );
  }
  const currentMax = maxAttemptNo(state);
  if (currentMax === 0) {
    return acknowledgeEvent(
      state,
      event,
      `REPLAN_STARTED before any plan ignored (${event.eventId})`,
    );
  }
  if (attemptNo <= currentMax) {
    return acknowledgeEvent(
      state,
      event,
      `REPLAN_STARTED attemptNo must strictly increase ignored (${event.eventId})`,
    );
  }
  const attempts = cloneAttempts(state.attempts);
  attempts[attemptNo] = {
    attemptNo,
    steps: {},
  };
  return {
    ...acknowledgeEvent(state, event),
    attempts,
  };
}

function applySummaryStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  if (state.summaryStatus !== 'IDLE') {
    return acknowledgeEvent(
      state,
      event,
      `SUMMARY_STARTED from ${state.summaryStatus} ignored (${event.eventId})`,
    );
  }
  return {
    ...acknowledgeEvent(state, event),
    summaryStatus: 'RUNNING',
  };
}

function applySummaryCompleted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  if (state.summaryStatus !== 'RUNNING') {
    return acknowledgeEvent(
      state,
      event,
      `SUMMARY_COMPLETED from ${state.summaryStatus} ignored (${event.eventId})`,
    );
  }
  return {
    ...acknowledgeEvent(state, event),
    summaryStatus: 'COMPLETED',
  };
}

function applySummaryFallback(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  if (state.summaryStatus !== 'RUNNING') {
    return acknowledgeEvent(
      state,
      event,
      `SUMMARY_FALLBACK from ${state.summaryStatus} ignored (${event.eventId})`,
    );
  }
  return {
    ...acknowledgeEvent(state, event),
    summaryStatus: 'FALLBACK',
  };
}

function applyFinalResponse(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  if (isTerminal(state)) {
    return acknowledgeEvent(
      state,
      event,
      `duplicate FINAL_RESPONSE ignored (${event.eventId})`,
    );
  }
  if (
    event.completionStatus !== 'SUCCESS' &&
    event.completionStatus !== 'PARTIAL'
  ) {
    return acknowledgeEvent(
      state,
      event,
      `FINAL_RESPONSE missing completionStatus ignored (${event.eventId})`,
    );
  }
  return {
    ...acknowledgeEvent(state, event),
    terminalStatus: event.completionStatus,
    phaseLabel: 'done',
  };
}

function ensureSubTaskSlot(
  step: StepUiState,
  event: OrchestrationEvent,
  defaultOpen = false,
): SubTaskUiState | null {
  const subTaskId = event.subTaskId;
  if (!subTaskId) return null;
  const existing = step.subTasks?.[subTaskId];
  if (existing) return existing;
  return {
    subTaskId,
    agentId: event.agentId ?? '',
    agentName: event.agentName ?? event.agentId ?? subTaskId,
    objective: '',
    status: 'PLANNED',
    retryNo: event.retryNo ?? 0,
    errorCode: null,
    lines: [],
    open: defaultOpen,
  };
}

function applyParallelStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  const stepId = event.stepId;
  if (attemptNo === null || !stepId) {
    return acknowledgeEvent(
      state,
      event,
      `PARALLEL_STARTED missing attempt/step ignored (${event.eventId})`,
    );
  }
  const step = state.attempts[attemptNo]?.steps[stepId];
  if (!step) {
    return acknowledgeEvent(
      state,
      event,
      `PARALLEL_STARTED unknown step ignored (${event.eventId})`,
    );
  }
  const subTasks = cloneSubTasks(step.subTasks ?? emptySubTasks());
  for (const id of Object.keys(subTasks)) {
    subTasks[id] = {
      ...subTasks[id],
      status: activatePlanned(subTasks[id].status),
    };
  }
  const next = updateStepStatus(state, attemptNo, stepId, {
    status: step.status === 'PLANNED' ? 'RUNNING' : step.status,
    stepMode: 'PARALLEL_AGENTS',
    subTasks,
  });
  return acknowledgeEvent(next, event);
}

function applySubTaskStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  const stepId = event.stepId;
  const subTaskId = event.subTaskId;
  if (attemptNo === null || !stepId || !subTaskId) {
    return acknowledgeEvent(
      state,
      event,
      `SUBTASK_STARTED missing ids ignored (${event.eventId})`,
    );
  }
  const step = state.attempts[attemptNo]?.steps[stepId];
  if (!step) {
    return acknowledgeEvent(
      state,
      event,
      `SUBTASK_STARTED unknown step ignored (${event.eventId})`,
    );
  }
  const slot = ensureSubTaskSlot(step, event, state.masterOpen);
  if (!slot) {
    return acknowledgeEvent(
      state,
      event,
      `SUBTASK_STARTED missing subTaskId ignored (${event.eventId})`,
    );
  }
  const subTasks = cloneSubTasks(step.subTasks ?? emptySubTasks());
  subTasks[subTaskId] = {
    ...slot,
    status: 'RUNNING',
    agentId: event.agentId ?? slot.agentId,
    agentName: preferReadableAgentName(
      event.agentName,
      slot.agentName,
      event.agentId ?? slot.agentId,
    ),
    retryNo: event.retryNo ?? slot.retryNo ?? 0,
  };
  const next = updateStepStatus(state, attemptNo, stepId, {
    status: step.status === 'PLANNED' ? 'RUNNING' : step.status,
    stepMode: resolveStepMode(step, event) ?? 'PARALLEL_AGENTS',
    subTasks,
  });
  return acknowledgeEvent(next, event);
}

function applySubTaskTerminal(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
  nextStatus: 'COMPLETED' | 'FAILED',
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  const stepId = event.stepId;
  const subTaskId = event.subTaskId;
  if (attemptNo === null || !stepId || !subTaskId) {
    return acknowledgeEvent(
      state,
      event,
      `${event.eventType} missing ids ignored (${event.eventId})`,
    );
  }
  const step = state.attempts[attemptNo]?.steps[stepId];
  if (!step) {
    return acknowledgeEvent(
      state,
      event,
      `${event.eventType} unknown step ignored (${event.eventId})`,
    );
  }
  const existing = step.subTasks?.[subTaskId] ?? ensureSubTaskSlot(step, event, state.masterOpen);
  if (!existing) {
    return acknowledgeEvent(
      state,
      event,
      `${event.eventType} unknown subTask ignored (${event.eventId})`,
    );
  }
  if (nextStatus === 'FAILED' && !event.errorCode) {
    return acknowledgeEvent(
      state,
      event,
      `SUBTASK_FAILED missing errorCode ignored (${event.eventId})`,
    );
  }
  const subTasks = cloneSubTasks(step.subTasks ?? emptySubTasks());
  subTasks[subTaskId] = {
    ...existing,
    status: nextStatus,
    agentId: event.agentId ?? existing.agentId,
    agentName: preferReadableAgentName(
      event.agentName,
      existing.agentName,
      event.agentId ?? existing.agentId,
    ),
    errorCode: nextStatus === 'FAILED' ? event.errorCode : existing.errorCode,
    retryNo: event.retryNo ?? existing.retryNo,
  };
  return acknowledgeEvent(
    updateStepStatus(state, attemptNo, stepId, { subTasks }),
    event,
  );
}

function applyStepReviewStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  const stepId = event.stepId;
  if (attemptNo === null || !stepId) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_REVIEW_STARTED missing attempt/step ignored (${event.eventId})`,
    );
  }
  const step = state.attempts[attemptNo]?.steps[stepId];
  if (!step) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_REVIEW_STARTED unknown step ignored (${event.eventId})`,
    );
  }
  return acknowledgeEvent(
    updateStepStatus(state, attemptNo, stepId, { reviewing: true }),
    event,
  );
}

function applyStepRetryStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  const stepId = event.stepId;
  if (attemptNo === null || !stepId) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_RETRY_STARTED missing attempt/step ignored (${event.eventId})`,
    );
  }
  const step = state.attempts[attemptNo]?.steps[stepId];
  if (!step) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_RETRY_STARTED unknown step ignored (${event.eventId})`,
    );
  }
  const retryNo = event.retryNo ?? 1;
  let next = updateStepStatus(state, attemptNo, stepId, {
    status: 'RUNNING',
    reviewing: false,
    retryNo,
    fallbackActive: false,
  });
  if (event.subTaskId && step.subTasks?.[event.subTaskId]) {
    next = updateSubTask(next, attemptNo, stepId, event.subTaskId, {
      status: 'RUNNING',
      retryNo,
      errorCode: null,
    });
  }
  return acknowledgeEvent(next, event);
}

function applyStepFallbackStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  const stepId = event.stepId;
  if (attemptNo === null || !stepId) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_FALLBACK_STARTED missing attempt/step ignored (${event.eventId})`,
    );
  }
  const step = state.attempts[attemptNo]?.steps[stepId];
  if (!step) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_FALLBACK_STARTED unknown step ignored (${event.eventId})`,
    );
  }
  return acknowledgeEvent(
    updateStepStatus(state, attemptNo, stepId, {
      status: 'RUNNING',
      reviewing: false,
      fallbackActive: true,
    }),
    event,
  );
}

const HANDLERS: Record<
  OrchestrationEventType,
  (state: OrchestrationUiState, event: OrchestrationEvent) => OrchestrationUiState
> = {
  ROUTE_SELECTED: applyRouteSelected,
  PLAN_CREATED: applyPlanCreated,
  STEP_STARTED: applyStepStarted,
  STEP_COMPLETED: (s, e) => applyStepTerminal(s, e, 'COMPLETED'),
  STEP_FAILED: (s, e) => applyStepTerminal(s, e, 'FAILED'),
  STEP_SKIPPED: (s, e) => applyStepTerminal(s, e, 'SKIPPED'),
  REPLAN_STARTED: applyReplanStarted,
  SUMMARY_STARTED: applySummaryStarted,
  SUMMARY_COMPLETED: applySummaryCompleted,
  SUMMARY_FALLBACK: applySummaryFallback,
  FINAL_RESPONSE: applyFinalResponse,
  PARALLEL_STARTED: applyParallelStarted,
  SUBTASK_STARTED: applySubTaskStarted,
  SUBTASK_COMPLETED: (s, e) => applySubTaskTerminal(s, e, 'COMPLETED'),
  SUBTASK_FAILED: (s, e) => applySubTaskTerminal(s, e, 'FAILED'),
  STEP_REVIEW_STARTED: applyStepReviewStarted,
  STEP_RETRY_STARTED: applyStepRetryStarted,
  STEP_FALLBACK_STARTED: applyStepFallbackStarted,
  STEP_DEGRADED: (s, e) => applyStepTerminal(s, e, 'DEGRADED'),
};

/**
 * Reduce a single orchestration event into UI state.
 * Illegal / duplicate / out-of-order events add recovery warnings and do not
 * corrupt previously applied good state.
 */
export function reduceOrchestrationEvent(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const envelopeError = validateEnvelope(event);
  if (envelopeError) {
    return withWarning(state, `invalid event ignored: ${envelopeError}`);
  }

  if (state.seenEventIds[event.eventId]) {
    return withWarning(
      state,
      `duplicate eventId ignored (${event.eventId})`,
    );
  }

  const parallelLive =
    PARALLEL_LIVE_EVENT_TYPES.has(event.eventType) && !isTerminal(state);
  if (event.sequence < state.lastSequence && parallelLive) {
    // Parallel workers share one sequence counter; a later packet from the
    // faster expert can arrive before an earlier packet from the other.
  } else if (event.sequence <= state.lastSequence) {
    return withWarning(
      {
        ...state,
        seenEventIds: {
          ...state.seenEventIds,
          [event.eventId]: true,
        },
      },
      `out-of-order sequence ignored (${event.eventId}, seq=${event.sequence})`,
    );
  }

  if (isTerminal(state) && event.eventType !== 'FINAL_RESPONSE') {
    return acknowledgeEvent(
      state,
      event,
      `event after terminal ignored (${event.eventId})`,
    );
  }

  const handler = HANDLERS[event.eventType];
  if (!handler) {
    return acknowledgeEvent(
      state,
      event,
      `unhandled eventType ignored (${event.eventId})`,
    );
  }

  return handler(state, event);
}

/**
 * Live thoughts from parallel workers used to land on the parent STEP.
 * Route them onto the matching subtask, or drop them so the parent card
 * does not flicker between expert names.
 */
function rerouteParallelParentTrace(
  trace: OrchestrationTrace,
  step: StepUiState,
): OrchestrationTrace | 'drop' | null {
  if (trace.scope !== 'STEP') {
    return null;
  }
  const subTasks = step.subTasks ?? emptySubTasks();
  const ids = Object.keys(subTasks);
  if (ids.length === 0) {
    return null;
  }
  const byId =
    trace.subTaskId && subTasks[trace.subTaskId] ? trace.subTaskId : null;
  const agentMatches = trace.agentId
    ? ids.filter((id) => subTasks[id].agentId === trace.agentId)
    : [];
  const targetId = byId ?? (agentMatches.length === 1 ? agentMatches[0] : null);
  if (targetId) {
    return {
      ...trace,
      scope: 'SUBTASK',
      subTaskId: targetId,
    };
  }
  return 'drop';
}

/**
 * Reduce a parallel orchestration_trace packet into UI work-panel state.
 * Does not write answer body; fold defaults stay collapsed.
 */
export function reduceOrchestrationTrace(
  state: OrchestrationUiState,
  trace: OrchestrationTrace,
): OrchestrationUiState {
  if (trace.scope === 'MAIN') {
    if (trace.sequence <= lastSequenceForTrace(state, trace)) {
      return state;
    }
    return {
      ...state,
      lastTraceSequence: nextTraceSequence(state, trace.sequence),
      main: {
        ...state.main,
        lines: appendTraceLine(state.main.lines, trace),
      },
    };
  }

  const stepId = trace.stepId;
  if (!stepId) {
    return {
      ...state,
      lastTraceSequence: nextTraceSequence(state, trace.sequence),
    };
  }

  let attemptNo =
    typeof trace.attemptNo === 'number' && state.attempts[trace.attemptNo]
      ? trace.attemptNo
      : maxAttemptNo(state);
  if (attemptNo < 1) {
    attemptNo = typeof trace.attemptNo === 'number' ? trace.attemptNo : 1;
  }

  const attempts = cloneAttempts(state.attempts);
  const existingAttempt = attempts[attemptNo] ?? {
    attemptNo,
    steps: {},
  };
  const step = existingAttempt.steps[stepId] ?? {
    stepId,
    agentId: trace.agentId ?? stepId,
    agentName: preferReadableAgentName(
      trace.agentName,
      undefined,
      trace.agentId ?? stepId,
    ),
    objective: '',
    status: 'RUNNING' as const,
    errorCode: null,
    lines: [],
    open: state.masterOpen,
    subTasks: emptySubTasks(),
  };

  const parallelTrace = rerouteParallelParentTrace(trace, step);
  if (parallelTrace === 'drop') {
    return {
      ...state,
      lastTraceSequence: nextTraceSequence(state, trace.sequence),
    };
  }
  if (parallelTrace) {
    trace = parallelTrace;
  }

  if (trace.sequence <= lastSequenceForTrace(state, trace)) {
    return state;
  }

  if (trace.scope === 'SUBTASK' && trace.subTaskId) {
    const subTasks = cloneSubTasks(step.subTasks ?? emptySubTasks());
    const existing =
      subTasks[trace.subTaskId] ??
      ({
        subTaskId: trace.subTaskId,
        agentId: trace.agentId ?? '',
        agentName: preferReadableAgentName(
          trace.agentName,
          undefined,
          trace.agentId ?? trace.subTaskId,
        ),
        objective: '',
        status: 'RUNNING' as const,
        retryNo: trace.retryNo ?? 0,
        errorCode: null,
        lines: [],
        open: state.masterOpen,
      } satisfies SubTaskUiState);
    const patchedSub: SubTaskUiState = {
      ...existing,
      agentId: trace.agentId ?? existing.agentId,
      agentName: preferReadableAgentName(
        trace.agentName,
        existing.agentName,
        trace.agentId ?? existing.agentId,
      ),
      retryNo: trace.retryNo ?? existing.retryNo,
      lines: appendTraceLine(existing.lines, trace),
      status: activatePlanned(existing.status),
    };
    if (trace.kind === 'ERROR' && !existing.errorCode) {
      patchedSub.errorCode = trace.text;
    }
    subTasks[trace.subTaskId] = patchedSub;
    attempts[attemptNo] = {
      ...existingAttempt,
      steps: {
        ...existingAttempt.steps,
        [stepId]: {
          ...step,
          subTasks,
          status: step.status === 'PLANNED' ? 'RUNNING' : step.status,
        },
      },
    };
    return {
      ...state,
      lastTraceSequence: nextTraceSequence(state, trace.sequence),
      attempts,
    };
  }

  const objectiveFromStatus =
    !step.objective &&
    trace.kind === 'STATUS' &&
    typeof trace.text === 'string'
      ? (trace.text.match(/^开始执行[：:](.+)$/)?.[1]?.trim() ?? '')
      : '';
  const patched: StepUiState = {
    ...step,
    agentId: trace.agentId ?? step.agentId,
    agentName: preferReadableAgentName(
      trace.agentName,
      step.agentName,
      trace.agentId ?? step.agentId,
    ),
    objective: step.objective || objectiveFromStatus,
    lines: appendTraceLine(step.lines, trace),
    subTasks: cloneSubTasks(step.subTasks ?? emptySubTasks()),
    status: activatePlanned(step.status),
  };
  if (trace.kind === 'OUTPUT') {
    patched.output =
      trace.append && step.output ? step.output + trace.text : trace.text;
  }
  if (trace.kind === 'ERROR' && !step.errorCode) {
    patched.errorCode = trace.text;
  }
  attempts[attemptNo] = {
    ...existingAttempt,
    steps: {
      ...existingAttempt.steps,
      [stepId]: patched,
    },
  };
  return {
    ...state,
    lastTraceSequence: nextTraceSequence(state, trace.sequence),
    attempts,
  };
}

export function toggleMasterOpen(state: OrchestrationUiState): OrchestrationUiState {
  if (state.masterOpen) {
    return {
      ...state,
      masterOpen: false,
    };
  }
  const attempts = cloneAttempts(state.attempts);
  for (const key of Object.keys(attempts)) {
    const attemptNo = Number(key);
    const attempt = attempts[attemptNo];
    const steps: Record<string, StepUiState> = {};
    for (const [stepId, step] of Object.entries(attempt.steps)) {
      const subTasks = cloneSubTasks(step.subTasks ?? emptySubTasks());
      for (const subId of Object.keys(subTasks)) {
        subTasks[subId] = {
          ...subTasks[subId],
          open: true,
        };
      }
      steps[stepId] = {
        ...step,
        open: true,
        subTasks,
      };
    }
    attempts[attemptNo] = {
      ...attempt,
      steps,
    };
  }
  return {
    ...state,
    masterOpen: true,
    main: {
      ...state.main,
      open: true,
    },
    attempts,
  };
}

export function toggleMainOpen(state: OrchestrationUiState): OrchestrationUiState {
  return {
    ...state,
    main: {
      ...state.main,
      open: !state.main.open
    },
  };
}

export function toggleStepOpen(
  state: OrchestrationUiState,
  attemptNo: number,
  stepId: string,
): OrchestrationUiState {
  const attempt = state.attempts[attemptNo];
  const step = attempt?.steps[stepId];
  if (!step) return state;
  return updateStepStatus(state, attemptNo, stepId, { open: !step.open });
}

export function toggleSubTaskOpen(
  state: OrchestrationUiState,
  attemptNo: number,
  stepId: string,
  subTaskId: string,
): OrchestrationUiState {
  const step = state.attempts[attemptNo]?.steps[stepId];
  const sub = step?.subTasks?.[subTaskId];
  if (!sub) return state;
  return updateSubTask(state, attemptNo, stepId, subTaskId, {open: !sub.open,});
}

export function collapseOrchestrationFolds(
  state: OrchestrationUiState,
): OrchestrationUiState {
  const attempts = cloneAttempts(state.attempts);
  for (const key of Object.keys(attempts)) {
    const attemptNo = Number(key);
    const attempt = attempts[attemptNo];
    const steps: Record<string, StepUiState> = {};
    for (const [stepId, step] of Object.entries(attempt.steps)) {
      const subTasks = cloneSubTasks(step.subTasks ?? emptySubTasks());
      for (const subId of Object.keys(subTasks)) {
        subTasks[subId] = {
          ...subTasks[subId],
          open: false,
        };
      }
      steps[stepId] = {
        ...step,
        open: false,
        subTasks,
      };
    }
    attempts[attemptNo] = {
      ...attempt,
      steps,
    };
  }
  return {
    ...state,
    masterOpen: false,
    main: {
      ...state.main,
      open: false,
    },
    attempts,
  };
}

export function markOrchestrationInterrupted(
  state: OrchestrationUiState,
): OrchestrationUiState {
  return {
    ...collapseOrchestrationFolds(state),
    phaseLabel: 'done',
    terminalStatus: 'INTERRUPTED',
  };
}

/**
 * Keep user fold choices when SSE reapplies orchestration from a stale working copy.
 * New steps keep their incoming default (collapsed).
 */
function countSteps(state: OrchestrationUiState): number {
  let count = 0;
  for (const attempt of Object.values(state.attempts)) {
    count += Object.keys(attempt.steps).length;
  }
  return count;
}

export function preserveOrchestrationFold(
  incoming: OrchestrationUiState,
  existing: OrchestrationUiState | undefined,
): OrchestrationUiState {
  if (!existing) {
    return incoming;
  }
  // First plan/work should stay visible so users can follow the collaboration.
  const autoRevealed =
    incoming.masterOpen &&
    !existing.masterOpen &&
    countSteps(existing) === 0 &&
    countSteps(incoming) > 0;
  const attempts = cloneAttempts(incoming.attempts);
  for (const key of Object.keys(attempts)) {
    const attemptNo = Number(key);
    const incomingAttempt = attempts[attemptNo];
    const existingAttempt = existing.attempts[attemptNo];
    if (!incomingAttempt || !existingAttempt) {
      continue;
    }
    const steps: Record<string, StepUiState> = { ...incomingAttempt.steps };
    for (const stepId of Object.keys(steps)) {
      const existingStep = existingAttempt.steps[stepId];
      if (existingStep) {
        const subTasks = cloneSubTasks(steps[stepId].subTasks ?? emptySubTasks());
        for (const subId of Object.keys(subTasks)) {
          const existingSub = existingStep.subTasks?.[subId];
          if (existingSub) {
            subTasks[subId] = {
              ...subTasks[subId],
              open: existingSub.open
            };
          } else if (existing.masterOpen || autoRevealed) {
            subTasks[subId] = {
              ...subTasks[subId],
              open: true,
            };
          }
        }
        steps[stepId] = {
          ...steps[stepId],
          open: existingStep.open || autoRevealed,
          subTasks,
        };
      } else if (existing.masterOpen || autoRevealed) {
        const subTasks = cloneSubTasks(steps[stepId].subTasks ?? emptySubTasks());
        for (const subId of Object.keys(subTasks)) {
          subTasks[subId] = {
            ...subTasks[subId],
            open: true,
          };
        }
        steps[stepId] = {
          ...steps[stepId],
          open: true,
          subTasks,
        };
      }
    }
    attempts[attemptNo] = {
      ...incomingAttempt,
      steps
    };
  }
  return {
    ...incoming,
    masterOpen: autoRevealed ? true : existing.masterOpen,
    main: {
      ...incoming.main,
      open: autoRevealed ? true : existing.main.open
    },
    attempts,
  };
}

export function markOrchestrationDone(
  state: OrchestrationUiState,
): OrchestrationUiState {
  if (state.phaseLabel === 'done') return state;
  return {
    ...state,
    phaseLabel: 'done'
  };
}
