import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  encodeNdjsonAsSse,
  getNdjsonFixture,
  parseNdjsonLines,
} from '../../../mocks/fakeSse';

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn(),
}));

vi.mock('@/features/auth/csrf', () => ({
  getCsrf: () => ({ headerName: 'X-XSRF-TOKEN', token: 'test-csrf' }),
}));

import { fetchEventSource } from '@microsoft/fetch-event-source';
import querySSE, { FatalSseError } from '@/utils/querySSE';

type FetchOptions = NonNullable<Parameters<typeof fetchEventSource>[1]>;

/** Run the mock body synchronously so onmessage side effects are visible immediately. */
function mockFetch(impl: (options: FetchOptions) => void) {
  vi.mocked(fetchEventSource).mockImplementation((_url, options) => {
    impl(options as FetchOptions);
    return Promise.resolve();
  });
}

describe('fake SSE fixtures', () => {
  it('parses success-react NDJSON fixture lines', () => {
    const lines = parseNdjsonLines(getNdjsonFixture('success-react'));
    expect(lines.length).toBeGreaterThanOrEqual(2);
    const last = JSON.parse(lines[lines.length - 1]);
    expect(last.finished).toBe(true);
    expect(last.responseAll).toContain('ReAct');
  });

  it('encodes NDJSON as SSE data frames', () => {
    const sse = encodeNdjsonAsSse(getNdjsonFixture('success-plan'));
    expect(sse).toContain('data: ');
    expect(sse.split('\n\n').filter(Boolean).length).toBeGreaterThanOrEqual(1);
  });
});

describe('querySSE (plan §11 / §13.8)', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('sends Cookie credentials + CSRF header and keeps openWhenHidden', async () => {
    mockFetch((options) => {
      void options.onopen?.(
        new Response(null, {
          status: 200,
          headers: { 'content-type': 'text/event-stream' },
        }),
      );
      options.onmessage?.({
        id: '',
        event: '',
        data: JSON.stringify({ finished: true, status: 'success' }),
      });
    });

    const handle = querySSE({
      body: { sessionId: 'c1', requestId: 'r1', query: 'hi' },
      handleMessage: () => undefined,
    });

    expect(fetchEventSource).toHaveBeenCalledTimes(1);
    const [, options] = vi.mocked(fetchEventSource).mock.calls[0];
    expect(options?.credentials).toBe('include');
    expect(options?.openWhenHidden).toBe(true);
    expect(options?.headers).toMatchObject({
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'X-XSRF-TOKEN': 'test-csrf',
    });
    await expect(handle.done).resolves.toEqual({ kind: 'COMPLETED' });
  });

  it('forwards non-heartbeat messages and settles COMPLETED on finished', async () => {
    const messages: unknown[] = [];
    mockFetch((options) => {
      void options.onopen?.(
        new Response(null, {
          status: 200,
          headers: { 'content-type': 'text/event-stream' },
        }),
      );
      const lines = parseNdjsonLines(getNdjsonFixture('success-react'));
      for (const line of lines) {
        options.onmessage?.({ id: '', event: '', data: line });
      }
      options.onclose?.();
    });

    const handle = querySSE({
      body: { query: 'test' },
      handleMessage: (data) => messages.push(data),
    });

    expect(messages.length).toBeGreaterThanOrEqual(2);
    await expect(handle.done).resolves.toEqual({ kind: 'COMPLETED' });
  });

  it('ignores heartbeat packages for UI callbacks', async () => {
    const messages: unknown[] = [];
    mockFetch((options) => {
      void options.onopen?.(
        new Response(null, {
          status: 200,
          headers: { 'content-type': 'text/event-stream' },
        }),
      );
      options.onmessage?.({
        id: '',
        event: '',
        data: JSON.stringify({ packageType: 'heartbeat', finished: false }),
      });
      options.onmessage?.({
        id: '',
        event: '',
        data: JSON.stringify({ finished: true, status: 'success' }),
      });
    });

    const handle = querySSE({
      body: {},
      handleMessage: (data) => messages.push(data),
    });

    expect(messages).toHaveLength(1);
    await expect(handle.done).resolves.toEqual({ kind: 'COMPLETED' });
  });

  it('settles FAILED when finished with status=failed', async () => {
    mockFetch((options) => {
      void options.onopen?.(
        new Response(null, {
          status: 200,
          headers: { 'content-type': 'text/event-stream' },
        }),
      );
      options.onmessage?.({
        id: '',
        event: '',
        data: JSON.stringify({
          finished: true,
          status: 'failed',
          errorMsg: 'boom',
        }),
      });
    });

    const handle = querySSE({
      body: {},
      handleMessage: () => undefined,
    });

    await expect(handle.done).resolves.toEqual({
      kind: 'FAILED',
      errorMsg: 'boom',
    });
  });

  it('settles HTTP_ERROR on non-SSE open (401 AUTH_REQUIRED)', async () => {
    mockFetch((options) => {
      const open = options.onopen?.(
        new Response(
          JSON.stringify({
            code: 'AUTH_REQUIRED',
            message: 'login required',
            data: null,
          }),
          {
            status: 401,
            headers: { 'content-type': 'application/json' },
          },
        ),
      );
      // onopen is async and throws FatalSseError after parsing body.
      void Promise.resolve(open).catch((error) => {
        try {
          options.onerror?.(error);
        } catch {
          // expected: onerror must throw to disable auto-retry
        }
      });
    });

    const handle = querySSE({
      body: {},
      handleMessage: () => undefined,
    });

    await expect(handle.done).resolves.toEqual({
      kind: 'HTTP_ERROR',
      httpStatus: 401,
      code: 'AUTH_REQUIRED',
      message: 'login required',
    });
  });

  it('settles HTTP_ERROR for 409 busy open failure', async () => {
    mockFetch((options) => {
      const open = options.onopen?.(
        new Response(
          JSON.stringify({
            code: 'CONVERSATION_BUSY',
            message: 'busy',
            data: null,
          }),
          {
            status: 409,
            headers: { 'content-type': 'application/json' },
          },
        ),
      );
      void Promise.resolve(open).catch((error) => {
        try {
          options.onerror?.(error);
        } catch {
          // expected
        }
      });
    });

    const handle = querySSE({
      body: {},
      handleMessage: () => undefined,
    });

    await expect(handle.done).resolves.toMatchObject({
      kind: 'HTTP_ERROR',
      httpStatus: 409,
      code: 'CONVERSATION_BUSY',
    });
  });

  it('settles INTERRUPTED/FATAL on malformed JSON and throws from onmessage', async () => {
    let thrown: unknown;
    mockFetch((options) => {
      void options.onopen?.(
        new Response(null, {
          status: 200,
          headers: { 'content-type': 'text/event-stream' },
        }),
      );
      try {
        options.onmessage?.({ id: '', event: '', data: '{not-json' });
      } catch (error) {
        thrown = error;
      }
    });

    const handle = querySSE({
      body: {},
      handleMessage: () => undefined,
    });

    expect(thrown).toBeInstanceOf(FatalSseError);
    await expect(handle.done).resolves.toMatchObject({
      kind: 'INTERRUPTED',
      reason: 'FATAL',
    });
  });

  it('settles INTERRUPTED/EOF when connection closes without terminal event', async () => {
    mockFetch((options) => {
      void options.onopen?.(
        new Response(null, {
          status: 200,
          headers: { 'content-type': 'text/event-stream' },
        }),
      );
      options.onmessage?.({
        id: '',
        event: '',
        data: JSON.stringify({ finished: false, response: 'partial' }),
      });
      options.onclose?.();
    });

    const handle = querySSE({
      body: {},
      handleMessage: () => undefined,
    });

    await expect(handle.done).resolves.toEqual({
      kind: 'INTERRUPTED',
      reason: 'EOF',
    });
  });

  it('abort() settles INTERRUPTED/ABORT', async () => {
    vi.mocked(fetchEventSource).mockImplementation(
      () => new Promise(() => undefined),
    );

    const handle = querySSE({
      body: {},
      handleMessage: () => undefined,
    });
    handle.abort();

    await expect(handle.done).resolves.toEqual({
      kind: 'INTERRUPTED',
      reason: 'ABORT',
    });
  });

  it('onerror always throws so fetch-event-source will not auto-retry POST', async () => {
    let onerrorThrew = false;
    mockFetch((options) => {
      try {
        options.onerror?.(new Error('network down'));
      } catch {
        onerrorThrew = true;
      }
    });

    const handle = querySSE({
      body: {},
      handleMessage: () => undefined,
    });

    expect(onerrorThrew).toBe(true);
    await expect(handle.done).resolves.toMatchObject({
      kind: 'INTERRUPTED',
      reason: 'FATAL',
    });
  });
});
