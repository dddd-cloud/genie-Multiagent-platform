import successReactNdjson from '../../docs/mvp-contract/fixtures/sse/success-react.ndjson?raw';
import successPlanNdjson from '../../docs/mvp-contract/fixtures/sse/success-plan.ndjson?raw';
import clientVisibleFailureNdjson from '../../docs/mvp-contract/fixtures/sse/client-visible-failure.ndjson?raw';
import slowStreamNdjson from '../../docs/mvp-contract/fixtures/sse/slow-stream.ndjson?raw';

export type FakeSseScenario =
  | 'success-react'
  | 'success-plan'
  | 'client-visible-failure'
  | 'slow-stream';

const SCENARIO_RAW: Record<FakeSseScenario, string> = {
  'success-react': successReactNdjson,
  'success-plan': successPlanNdjson,
  'client-visible-failure': clientVisibleFailureNdjson,
  'slow-stream': slowStreamNdjson,
};

export function parseNdjsonLines(raw: string): string[] {
  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

export function getNdjsonFixture(scenario: FakeSseScenario): string {
  return SCENARIO_RAW[scenario];
}

export type FakeSseOptions = {
  /** Delay between SSE data events (ms). */
  delayMs?: number;
  /** Abort signal to stop the stream early. */
  signal?: AbortSignal;
};

/**
 * Encode NDJSON lines as an SSE `text/event-stream` body:
 * each non-empty line becomes `data: <line>\n\n`.
 */
export function encodeNdjsonAsSse(raw: string): string {
  return parseNdjsonLines(raw)
    .map((line) => `data: ${line}\n\n`)
    .join('');
}

/**
 * Stream NDJSON fixture lines as SSE events (Node / MSW Response body).
 */
export function streamNdjsonAsSse(
  raw: string,
  options: FakeSseOptions = {},
): ReadableStream<Uint8Array> {
  const lines = parseNdjsonLines(raw);
  const delayMs = options.delayMs ?? 0;
  const encoder = new TextEncoder();

  return new ReadableStream<Uint8Array>({
    async start(controller) {
      try {
        for (const line of lines) {
          if (options.signal?.aborted) {
            break;
          }
          controller.enqueue(encoder.encode(`data: ${line}\n\n`));
          if (delayMs > 0) {
            await new Promise<void>((resolve) => {
              const timer = setTimeout(resolve, delayMs);
              options.signal?.addEventListener(
                'abort',
                () => {
                  clearTimeout(timer);
                  resolve();
                },
                { once: true },
              );
            });
          }
        }
      } finally {
        try {
          controller.close();
        } catch {
          // already closed
        }
      }
    },
  });
}

export function createFakeSseResponse(
  scenario: FakeSseScenario,
  options: FakeSseOptions = {},
): Response {
  const raw = getNdjsonFixture(scenario);
  const delayMs =
    options.delayMs ?? (scenario === 'slow-stream' ? 50 : 0);

  return new Response(streamNdjsonAsSse(raw, {
    ...options,
    delayMs
  }), {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    },
  });
}
