import { describe, expect, it } from 'vitest';
import type { OrchestrationEvent } from '@/contracts';
import {
  createInitialOrchestrationState,
  reduceOrchestrationEvent,
} from '../orchestrationReducer';

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

function planCreated(sequence: number, attemptNo: number): OrchestrationEvent {
  return event({
    eventId: `req-1:${sequence}`,
    sequence,
    eventType: 'PLAN_CREATED',
    attemptNo,
    steps: [
      {
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
        objective: 'Do work',
        inputRefs: [],
      },
      {
        stepId: 's2',
        agentId: 'a2',
        agentName: 'Agent Two',
        objective: 'Follow up',
        inputRefs: ['s1'],
      },
    ],
  });
}

describe('OrchestrationReducerTest', () => {
  it('creates initial RUNNING state', () => {
    const state = createInitialOrchestrationState();
    expect(state.terminalStatus).toBe('RUNNING');
    expect(state.summaryStatus).toBe('IDLE');
    expect(state.lastSequence).toBe(0);
    expect(state.route).toBeNull();
  });

  it('applies happy-path orchestrated flow', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'req-1:1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_STEP',
      }),
    );
    state = reduceOrchestrationEvent(state, planCreated(2, 1));
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'req-1:3',
        sequence: 3,
        eventType: 'STEP_STARTED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );
    expect(state.attempts[1].steps.s1.status).toBe('RUNNING');
    expect(
      Object.values(state.attempts[1].steps).filter((s) => s.status === 'RUNNING')
        .length,
    ).toBe(1);

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'req-1:4',
        sequence: 4,
        eventType: 'STEP_COMPLETED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'req-1:5',
        sequence: 5,
        eventType: 'STEP_STARTED',
        attemptNo: 1,
        stepId: 's2',
        agentId: 'a2',
        agentName: 'Agent Two',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'req-1:6',
        sequence: 6,
        eventType: 'STEP_COMPLETED',
        attemptNo: 1,
        stepId: 's2',
        agentId: 'a2',
        agentName: 'Agent Two',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'req-1:7',
        sequence: 7,
        eventType: 'SUMMARY_STARTED',
        attemptNo: 1,
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'req-1:8',
        sequence: 8,
        eventType: 'SUMMARY_COMPLETED',
        attemptNo: 1,
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'req-1:9',
        sequence: 9,
        eventType: 'FINAL_RESPONSE',
        completionStatus: 'SUCCESS',
        route: 'ORCHESTRATED',
      }),
    );

    expect(state.route).toBe('ORCHESTRATED');
    expect(state.summaryStatus).toBe('COMPLETED');
    expect(state.terminalStatus).toBe('SUCCESS');
    expect(state.attempts[1].steps.s1.status).toBe('COMPLETED');
    expect(state.attempts[1].steps.s2.status).toBe('COMPLETED');
  });

  it('rejects second RUNNING step and STEP_STARTED after FAILED', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_STEP',
      }),
    );
    state = reduceOrchestrationEvent(state, planCreated(2, 1));
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'STEP_STARTED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );

    const beforeSecondStart = state;
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e4',
        sequence: 4,
        eventType: 'STEP_STARTED',
        attemptNo: 1,
        stepId: 's2',
        agentId: 'a2',
        agentName: 'Agent Two',
      }),
    );
    expect(state.attempts[1].steps.s2.status).toBe('PLANNED');
    expect(state.attempts[1].steps.s1.status).toBe('RUNNING');
    expect(state.recoveryWarnings.length).toBeGreaterThan(
      beforeSecondStart.recoveryWarnings.length,
    );

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e5',
        sequence: 5,
        eventType: 'STEP_FAILED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
        errorCode: 'EXECUTION_ERROR',
      }),
    );
    expect(state.attempts[1].steps.s1.status).toBe('FAILED');

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e6',
        sequence: 6,
        eventType: 'STEP_STARTED',
        attemptNo: 1,
        stepId: 's2',
        agentId: 'a2',
        agentName: 'Agent Two',
      }),
    );
    expect(state.attempts[1].steps.s2.status).toBe('PLANNED');
  });

  it('allows replan with strictly increasing attemptNo <= 3', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_STEP',
      }),
    );
    state = reduceOrchestrationEvent(state, planCreated(2, 1));
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'STEP_STARTED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e4',
        sequence: 4,
        eventType: 'STEP_FAILED',
        attemptNo: 1,
        stepId: 's1',
        agentId: 'a1',
        agentName: 'Agent One',
        errorCode: 'TOOL_TIMEOUT',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e5',
        sequence: 5,
        eventType: 'STEP_SKIPPED',
        attemptNo: 1,
        stepId: 's2',
        agentId: 'a2',
        agentName: 'Agent Two',
        reasonCode: 'PRIOR_FAILED',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e6',
        sequence: 6,
        eventType: 'REPLAN_STARTED',
        attemptNo: 2,
        reasonCode: 'RETRYABLE',
      }),
    );
    state = reduceOrchestrationEvent(state, planCreated(7, 2));
    expect(state.attempts[2].steps.s1.status).toBe('PLANNED');

    const beforeBadReplan = state.recoveryWarnings.length;
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e8',
        sequence: 8,
        eventType: 'REPLAN_STARTED',
        attemptNo: 2,
        reasonCode: 'RETRYABLE',
      }),
    );
    expect(state.recoveryWarnings.length).toBeGreaterThan(beforeBadReplan);
  });

  it('ignores events after terminal without corrupting state', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'DIRECT',
        reasonCode: 'SINGLE',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e2',
        sequence: 2,
        eventType: 'FINAL_RESPONSE',
        completionStatus: 'PARTIAL',
      }),
    );
    expect(state.terminalStatus).toBe('PARTIAL');

    const snapshot = structuredClone(state);
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'HACK',
      }),
    );
    expect(state.route).toBe('DIRECT');
    expect(state.terminalStatus).toBe('PARTIAL');
    expect(state.recoveryWarnings.length).toBeGreaterThan(
      snapshot.recoveryWarnings.length,
    );
  });

  it('accepts SUMMARY_FALLBACK only from RUNNING summary', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_STEP',
      }),
    );
    state = reduceOrchestrationEvent(state, planCreated(2, 1));

    const before = state.recoveryWarnings.length;
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e3',
        sequence: 3,
        eventType: 'SUMMARY_FALLBACK',
        attemptNo: 1,
        reasonCode: 'MODEL_FAILED',
      }),
    );
    expect(state.summaryStatus).toBe('IDLE');
    expect(state.recoveryWarnings.length).toBeGreaterThan(before);

    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e4',
        sequence: 4,
        eventType: 'SUMMARY_STARTED',
        attemptNo: 1,
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'e5',
        sequence: 5,
        eventType: 'SUMMARY_FALLBACK',
        attemptNo: 1,
        reasonCode: 'MODEL_FAILED',
      }),
    );
    expect(state.summaryStatus).toBe('FALLBACK');
  });
});
