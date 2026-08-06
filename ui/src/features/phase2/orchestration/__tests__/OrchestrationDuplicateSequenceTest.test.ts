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
    requestId: 'req-dup',
    runId: 'run-dup',
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

describe('OrchestrationDuplicateSequenceTest', () => {
  it('ignores duplicate eventId and keeps prior good state', () => {
    let state = createInitialOrchestrationState();
    const first = event({
      eventId: 'dup-1',
      sequence: 1,
      eventType: 'ROUTE_SELECTED',
      route: 'ORCHESTRATED',
      reasonCode: 'MULTI_STEP',
    });
    state = reduceOrchestrationEvent(state, first);
    expect(state.route).toBe('ORCHESTRATED');
    expect(state.lastSequence).toBe(1);

    state = reduceOrchestrationEvent(state, first);
    expect(state.route).toBe('ORCHESTRATED');
    expect(state.lastSequence).toBe(1);
    expect(state.recoveryWarnings.some((w) => w.includes('duplicate'))).toBe(
      true,
    );
  });

  it('ignores sequence rollback / out-of-order events', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'seq-1',
        sequence: 1,
        eventType: 'ROUTE_SELECTED',
        route: 'DIRECT',
        reasonCode: 'SINGLE',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'seq-3',
        sequence: 3,
        eventType: 'FINAL_RESPONSE',
        completionStatus: 'SUCCESS',
      }),
    );
    expect(state.terminalStatus).toBe('SUCCESS');
    expect(state.lastSequence).toBe(3);

    // Late sequence=2 must not rewrite terminal or route.
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'seq-2-late',
        sequence: 2,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'LATE',
      }),
    );
    expect(state.route).toBe('DIRECT');
    expect(state.terminalStatus).toBe('SUCCESS');
    expect(state.lastSequence).toBe(3);
    expect(
      state.recoveryWarnings.some((w) => w.includes('out-of-order')),
    ).toBe(true);
  });

  it('does not apply equal sequence twice', () => {
    let state = createInitialOrchestrationState();
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'eq-1',
        sequence: 5,
        eventType: 'ROUTE_SELECTED',
        route: 'ORCHESTRATED',
        reasonCode: 'MULTI_STEP',
      }),
    );
    state = reduceOrchestrationEvent(
      state,
      event({
        eventId: 'eq-2',
        sequence: 5,
        eventType: 'PLAN_CREATED',
        attemptNo: 1,
        steps: [
          {
            stepId: 's1',
            agentId: 'a1',
            agentName: 'A',
            objective: 'x',
            inputRefs: [],
          },
        ],
      }),
    );
    expect(state.attempts[1]).toBeUndefined();
    expect(state.lastSequence).toBe(5);
    expect(
      state.recoveryWarnings.some((w) => w.includes('out-of-order')),
    ).toBe(true);
  });
});
