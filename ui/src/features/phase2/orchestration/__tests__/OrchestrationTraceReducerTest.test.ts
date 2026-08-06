import { describe, expect, it } from 'vitest';
import {
  createInitialOrchestrationState,
  preserveOrchestrationFold,
  reduceOrchestrationEvent,
  reduceOrchestrationTrace,
  toggleMasterOpen,
  toggleStepOpen,
} from '../orchestrationReducer';
import type { OrchestrationTrace } from '../parseOrchestrationTrace';
import type { OrchestrationEvent } from '@/contracts';

function event(
  partial: Partial<OrchestrationEvent> &
    Pick<OrchestrationEvent, 'eventId' | 'sequence' | 'eventType'>,
): OrchestrationEvent {
  return {
    schemaVersion: 1,
    requestId: 'req-1',
    runId: 'run-1',
    attemptNo: null,
    stepId: null,
    agentId: null,
    agentName: null,
    route: null,
    reasonCode: null,
    errorCode: null,
    steps: [],
    completionStatus: null,
    ...partial,
  };
}

function trace(
  partial: Partial<OrchestrationTrace> &
    Pick<OrchestrationTrace, 'sequence' | 'scope' | 'kind' | 'text'>,
): OrchestrationTrace {
  return {
    schemaVersion: 1,
    requestId: 'req-1',
    runId: 'run-1',
    attemptNo: 1,
    stepId: null,
    agentId: null,
    agentName: null,
    append: false,
    truncated: false,
    ...partial,
  };
}

describe('OrchestrationTraceReducerTest', () => {
  it('defaults to collapsed thinking work panel', () => {
    const state = createInitialOrchestrationState();
    expect(state.masterOpen).toBe(false);
    expect(state.main.open).toBe(false);
    expect(state.phaseLabel).toBe('thinking');
    expect(state.main.lines).toEqual([]);
  });

  it('appends MAIN and STEP traces without opening panels', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_AGENT',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e2',
        sequence: 3,
        eventType: 'PLAN_CREATED',
        attemptNo: 1,
        steps: [
          {
            stepId: 's1',
            agentId: 'a1',
            agentName: 'Agent A',
            objective: 'Work',
            inputRefs: [],
          },
        ],
      }),
    );
    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 2,
        scope: 'MAIN',
        kind: 'STATUS',
        text: '规划中',
      }),
    );
    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 4,
        scope: 'STEP',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent A',
        kind: 'THOUGHT',
        text: 'thinking…',
        append: true,
      }),
    );
    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 5,
        scope: 'STEP',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent A',
        kind: 'THOUGHT',
        text: ' more',
        append: true,
      }),
    );
    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 6,
        scope: 'STEP',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent A',
        kind: 'OUTPUT',
        text: 'done',
      }),
    );

    expect(state.masterOpen).toBe(false);
    expect(state.main.lines.some((l) => l.text.includes('规划中'))).toBe(true);
    expect(state.attempts[1].steps.s1.lines[0].text).toBe('thinking… more');
    expect(state.attempts[1].steps.s1.output).toBe('done');
    expect(state.attempts[1].steps.s1.open).toBe(false);

    state = toggleMasterOpen(state);
    expect(state.masterOpen).toBe(true);
  });

  it('merges PLAN_CREATED objectives into trace placeholder steps', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationTrace(
      state,
      trace({
        sequence: 1,
        scope: 'STEP',
        stepId: 's1',
        agentId: 'a1',
        agentName: 'b',
        kind: 'STATUS',
        text: '开始执行：用 Agent b 写一句话描述夏天。',
      }),
    );
    expect(state.attempts[1].steps.s1.objective).toBe(
      '用 Agent b 写一句话描述夏天。',
    );

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'plan',
        sequence: 2,
        eventType: 'PLAN_CREATED',
        attemptNo: 1,
        steps: [
          {
            stepId: 's1',
            agentId: 'a1',
            agentName: 'b',
            objective: '用 Agent b 写一句话描述夏天。',
            inputRefs: [],
          },
          {
            stepId: 's2',
            agentId: 'a2',
            agentName: 'a',
            objective: '用 Agent a 写一句话描述夏天。',
            inputRefs: [],
          },
        ],
      }),
    );
    expect(state.attempts[1].steps.s1.objective).toBe(
      '用 Agent b 写一句话描述夏天。',
    );
    expect(state.attempts[1].steps.s2.objective).toBe(
      '用 Agent a 写一句话描述夏天。',
    );
    expect(state.attempts[1].steps.s1.lines.length).toBeGreaterThan(0);
  });

  it('preserves user fold choices across SSE re-reduce', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_AGENT',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e2',
        sequence: 2,
        eventType: 'PLAN_CREATED',
        attemptNo: 1,
        steps: [
          {
            stepId: 's1',
            agentId: 'a1',
            agentName: 'Agent A',
            objective: 'do a',
            inputRefs: [],
          },
        ],
      }),
    );
    state = toggleMasterOpen(state);
    state = toggleStepOpen(state, 1, 's1');
    expect(state.masterOpen).toBe(true);
    expect(state.attempts[1].steps.s1.open).toBe(true);

    // Simulate SSE working-copy that never saw the toggle (still collapsed).
    let stale = createInitialOrchestrationState();
    stale = reduceOrchestrationEvent(
      stale,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_AGENT',
      }),
    );
    stale = reduceOrchestrationEvent(
      stale,
      event({
        eventId: 'e2',
        sequence: 2,
        eventType: 'PLAN_CREATED',
        attemptNo: 1,
        steps: [
          {
            stepId: 's1',
            agentId: 'a1',
            agentName: 'Agent A',
            objective: 'do a',
            inputRefs: [],
          },
        ],
      }),
    );
    stale = reduceOrchestrationEvent(
      stale,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'STEP_STARTED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent A',
      }),
    );
    stale = reduceOrchestrationEvent(
      stale,
      event({
        eventId: 'e4',
        sequence: 4,
        eventType: 'STEP_COMPLETED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent A',
      }),
    );
    expect(stale.masterOpen).toBe(false);

    const merged = preserveOrchestrationFold(stale, state);
    expect(merged.masterOpen).toBe(true);
    expect(merged.attempts[1].steps.s1.open).toBe(true);
    expect(merged.attempts[1].steps.s1.status).toBe('COMPLETED');
  });

  it('marks phase done on FINAL_RESPONSE', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_AGENT',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'final',
        sequence: 10,
        eventType: 'FINAL_RESPONSE',
        completionStatus: 'SUCCESS',
      }),
    );
    expect(state.phaseLabel).toBe('done');
    expect(state.terminalStatus).toBe('SUCCESS');
  });
});
