import {
  streamNdjsonAsSse,
  type FakeSseOptions,
} from '../../../mocks/fakeSse';
import type { Phase2SseScenario } from './state';
import directSuccessNdjson from './fixtures/direct-success.ndjson?raw';
import directFailureNdjson from './fixtures/direct-failure.ndjson?raw';
import orchestratedSuccessNdjson from './fixtures/orchestrated-success.ndjson?raw';
import orchestratedReplanNdjson from './fixtures/orchestrated-replan.ndjson?raw';
import orchestratedSummaryFallbackNdjson from './fixtures/orchestrated-summary-fallback.ndjson?raw';

const SCENARIO_RAW: Record<Phase2SseScenario, string> = {
  'direct-success': directSuccessNdjson,
  'direct-failure': directFailureNdjson,
  'orchestrated-success': orchestratedSuccessNdjson,
  'orchestrated-replan': orchestratedReplanNdjson,
  'orchestrated-summary-fallback': orchestratedSummaryFallbackNdjson,
};

export function getPhase2NdjsonFixture(scenario: Phase2SseScenario): string {
  return SCENARIO_RAW[scenario];
}

export function createFakePhase2SseResponse(
  scenario: Phase2SseScenario,
  options: FakeSseOptions = {},
): Response {
  const raw = getPhase2NdjsonFixture(scenario);
  return new Response(streamNdjsonAsSse(raw, options), {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    },
  });
}

export function extractFinalResponseContent(raw: string): string {
  const lines = raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  for (let i = lines.length - 1; i >= 0; i -= 1) {
    try {
      const parsed = JSON.parse(lines[i]) as {
        finished?: boolean;
        responseAll?: string;
        response?: string;
      };
      if (parsed.finished) {
        return parsed.responseAll || parsed.response || '';
      }
    } catch {
      // skip malformed trailing lines
    }
  }
  return '';
}
