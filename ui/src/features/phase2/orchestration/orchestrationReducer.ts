import {
  ORCHESTRATION_EVENT_TYPES,
  type OrchestrationEvent,
  type OrchestrationEventType,
} from '@/contracts';
import type { OrchestrationTrace } from './parseOrchestrationTrace';
import type {
  AttemptUiState,
  OrchestrationUiState,
  StepUiState,
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
    main: { open: false, lines: [] },
    phaseLabel: 'thinking',
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

function acknowledgeEvent(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
  warning?: string,
): OrchestrationUiState {
  const next: OrchestrationUiState = {
    ...state,
    lastSequence: event.sequence,
    seenEventIds: {
      ...state.seenEventIds,
      [event.eventId]: true
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

function countRunningSteps(state: OrchestrationUiState): number {
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

function cloneAttempts(
  attempts: Record<number, AttemptUiState>,
): Record<number, AttemptUiState> {
  const out: Record<number, AttemptUiState> = {};
  for (const [key, attempt] of Object.entries(attempts)) {
    out[Number(key)] = {
      attemptNo: attempt.attemptNo,
      steps: { ...attempt.steps },
    };
  }
  return out;
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
        ...patch
      },
    },
  };
  return {
    ...state,
    attempts
  };
}

function validateEnvelope(
  event: OrchestrationEvent,
): string | null {
  if (event.schemaVersion !== 1) {
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

function applyPlanCreated(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
  const attemptNo = event.attemptNo;
  if (attemptNo === null || attemptNo < 1 || attemptNo > 3) {
    return acknowledgeEvent(
      state,
      event,
      `PLAN_CREATED invalid attemptNo ignored (${event.eventId})`,
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

  // Merge when traces already created placeholder steps (empty objective).
  // Ignoring as "duplicate" left 任务安排 blank until snapshot reload.
  if (existing && Object.keys(existing.steps).length > 0) {
    const steps: Record<string, StepUiState> = { ...existing.steps };
    for (const step of event.steps) {
      const prev = steps[step.stepId];
      if (!prev) {
        steps[step.stepId] = {
          stepId: step.stepId,
          agentId: step.agentId,
          agentName: step.agentName,
          objective: step.objective,
          status: 'PLANNED',
          errorCode: null,
          lines: [],
          open: false,
        };
        continue;
      }
      steps[step.stepId] = {
        ...prev,
        agentId: step.agentId || prev.agentId,
        agentName: preferReadableAgentName(
          step.agentName,
          prev.agentName,
          step.agentId || prev.agentId,
        ),
        objective:
          step.objective && step.objective.trim().length > 0
            ? step.objective
            : prev.objective,
      };
    }
    attempts[attemptNo] = { attemptNo, steps };
    return {
      ...acknowledgeEvent(state, event),
      attempts,
    };
  }

  const steps: Record<string, StepUiState> = {};
  for (const step of event.steps) {
    steps[step.stepId] = {
      stepId: step.stepId,
      agentId: step.agentId,
      agentName: step.agentName,
      objective: step.objective,
      status: 'PLANNED',
      errorCode: null,
      lines: [],
      open: false,
    };
  }

  attempts[attemptNo] = {
    attemptNo,
    steps
  };

  return {
    ...acknowledgeEvent(state, event),
    attempts,
  };
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
  if (countRunningSteps(state) > 0) {
    return acknowledgeEvent(
      state,
      event,
      `STEP_STARTED while another step RUNNING ignored (${event.eventId})`,
    );
  }

  const next = updateStepStatus(state, attemptNo, stepId, {
    status: 'RUNNING',
    agentId: event.agentId ?? step.agentId,
    agentName: preferReadableAgentName(event.agentName, step.agentName, step.agentId),
  });
  return acknowledgeEvent(next, event);
}

function preferReadableAgentName(
  incoming: string | null | undefined,
  current: string | null | undefined,
  agentId: string,
): string {
  const pick = (value: string | null | undefined): string | null => {
    if (!value || value.trim().length === 0) return null;
    const trimmed = value.trim();
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(trimmed)) {
      return null;
    }
    if (trimmed === agentId) return null;
    return trimmed;
  };
  return pick(incoming) ?? pick(current) ?? agentId;
}

function applyStepTerminal(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
  nextStatus: 'COMPLETED' | 'FAILED' | 'SKIPPED',
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

  // RUNNING is the live path; PLANNED is accepted for pruned Snapshot hydrate.
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

  const next = updateStepStatus(state, attemptNo, stepId, {
    status: nextStatus,
    agentId: event.agentId ?? step.agentId,
    agentName: preferReadableAgentName(event.agentName, step.agentName, step.agentId),
    errorCode: nextStatus === 'FAILED' ? event.errorCode : step.errorCode,
  });
  return acknowledgeEvent(next, event);
}

function applyReplanStarted(
  state: OrchestrationUiState,
  event: OrchestrationEvent,
): OrchestrationUiState {
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
  // Reserve the attempt slot so subsequent REPLAN_STARTED cannot reuse it.
  // PLAN_CREATED fills in the step list.
  const attempts = cloneAttempts(state.attempts);
  attempts[attemptNo] = {
    attemptNo,
    steps: {}
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

  if (event.sequence <= state.lastSequence) {
    return withWarning(
      {
        ...state,
        seenEventIds: {
          ...state.seenEventIds,
          [event.eventId]: true
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
 * Reduce a parallel orchestration_trace packet into UI work-panel state.
 * Does not write answer body; fold defaults stay collapsed.
 */
export function reduceOrchestrationTrace(
  state: OrchestrationUiState,
  trace: OrchestrationTrace,
): OrchestrationUiState {
  if (trace.sequence <= state.lastTraceSequence) {
    return state;
  }

  if (trace.scope === 'MAIN') {
    return {
      ...state,
      lastTraceSequence: trace.sequence,
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
      lastTraceSequence: trace.sequence,
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
    agentName: trace.agentName ?? trace.agentId ?? stepId,
    objective: '',
    status: 'RUNNING' as const,
    errorCode: null,
    lines: [],
    open: false,
  };
  const objectiveFromStatus =
    !step.objective &&
    trace.kind === 'STATUS' &&
    typeof trace.text === 'string'
      ? trace.text.match(/^开始执行[：:](.+)$/)?.[1]?.trim() ?? ''
      : '';
  const patched: StepUiState = {
    ...step,
    agentId: trace.agentId ?? step.agentId,
    agentName: trace.agentName ?? step.agentName,
    objective: step.objective || objectiveFromStatus,
    lines: appendTraceLine(step.lines, trace),
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
    lastTraceSequence: trace.sequence,
    attempts,
  };
}

export function toggleMasterOpen(state: OrchestrationUiState): OrchestrationUiState {
  return { ...state, masterOpen: !state.masterOpen };
}

export function toggleMainOpen(state: OrchestrationUiState): OrchestrationUiState {
  return {
    ...state,
    main: { ...state.main, open: !state.main.open },
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

/**
 * Keep user fold choices when SSE reapplies orchestration from a stale working copy.
 * New steps keep their incoming default (collapsed).
 */
export function preserveOrchestrationFold(
  incoming: OrchestrationUiState,
  existing: OrchestrationUiState | undefined,
): OrchestrationUiState {
  if (!existing) {
    return incoming;
  }
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
        steps[stepId] = { ...steps[stepId], open: existingStep.open };
      }
    }
    attempts[attemptNo] = { ...incomingAttempt, steps };
  }
  return {
    ...incoming,
    masterOpen: existing.masterOpen,
    main: { ...incoming.main, open: existing.main.open },
    attempts,
  };
}

export function markOrchestrationDone(
  state: OrchestrationUiState,
): OrchestrationUiState {
  if (state.phaseLabel === 'done') return state;
  return { ...state, phaseLabel: 'done' };
}
