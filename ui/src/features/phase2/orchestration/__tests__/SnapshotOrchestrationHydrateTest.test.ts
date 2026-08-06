import { describe, expect, it } from 'vitest';
import type { OrchestrationEvent } from '@/contracts';
import snapshotSuccess from '@/mocks/phase2/fixtures/snapshot-orchestrated-success.json';
import { extractOrchestrationEventFromResult } from '../parseOrchestrationEvent';
import { replayOrchestrationEvents } from '../replayOrchestrationEvents';

describe('SnapshotOrchestrationHydrateTest', () => {
  it('replays pruned snapshot orchestration events to SUCCESS', () => {
    const events: OrchestrationEvent[] = [];
    for (const result of snapshotSuccess.events) {
      const parsed = extractOrchestrationEventFromResult(result);
      if (parsed) events.push(parsed);
    }

    expect(events.length).toBeGreaterThanOrEqual(3);
    expect(events.map((e) => e.eventType)).toEqual([
      'ROUTE_SELECTED',
      'PLAN_CREATED',
      'STEP_COMPLETED',
      'FINAL_RESPONSE',
    ]);

    const state = replayOrchestrationEvents(events);

    expect(state.route).toBe('ORCHESTRATED');
    expect(state.routeReasonCode).toBe('MULTI_STEP');
    expect(state.attempts[1]).toBeDefined();
    expect(state.attempts[1].steps['step-1']).toMatchObject({
      stepId: 'step-1',
      agentId: 'agent-research-001',
      agentName: 'Research Agent',
      objective: 'Gather sources',
      status: 'COMPLETED',
    });
    expect(state.terminalStatus).toBe('SUCCESS');
    expect(state.lastSequence).toBe(4);
    expect(state.seenEventIds['request-fixture:1']).toBe(true);
    expect(state.seenEventIds['request-fixture:4']).toBe(true);
    // Pruned snapshot skips STEP_STARTED; COMPLETED from PLANNED must not warn.
    expect(state.recoveryWarnings).toEqual([]);
  });

  it('ignores malformed orchestration payloads in snapshot events', () => {
    const events: OrchestrationEvent[] = [];
    for (const result of snapshotSuccess.events) {
      const parsed = extractOrchestrationEventFromResult(result);
      if (parsed) events.push(parsed);
    }
    // Inject a garbage envelope between valid events via replay helper path:
    // extract returns null for missing orchestrationEvent.
    expect(extractOrchestrationEventFromResult({ resultMap: {} })).toBeNull();
    expect(
      extractOrchestrationEventFromResult({resultMap: { orchestrationEvent: { schemaVersion: 2 } },}),
    ).toBeNull();

    const state = replayOrchestrationEvents(events);
    expect(state.terminalStatus).toBe('SUCCESS');
  });
});
