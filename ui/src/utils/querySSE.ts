import { fetchEventSource, type EventSourceMessage } from '@microsoft/fetch-event-source';
import type { ApiResponse } from '@/contracts';
import { getCsrf } from '@/features/auth/csrf';

const DEFAULT_SSE_URL = '/web/api/v1/gpt/queryAgentStreamIncr';

/** Active SSE abort hooks — AuthProvider calls abortAllActiveSse on AUTH_REQUIRED. */
const activeSseAborts = new Set<() => void>();

export function registerActiveSseAbort(abort: () => void): () => void {
  activeSseAborts.add(abort);
  return () => {
    activeSseAborts.delete(abort);
  };
}

export function abortAllActiveSse(): void {
  for (const abort of [...activeSseAborts]) {
    try {
      abort();
    } catch {
      // ignore individual abort failures
    }
  }
}

export type SseTerminalResult =
  | { kind: 'COMPLETED' }
  | { kind: 'FAILED'; errorMsg?: string | null }
  | { kind: 'INTERRUPTED'; reason: 'EOF' | 'ABORT' | 'FATAL'; message?: string }
  | { kind: 'HTTP_ERROR'; httpStatus: number; code?: string; message?: string };

export class FatalSseError extends Error {
  constructor(
    public httpStatus: number,
    public code: string | undefined,
    message: string,
  ) {
    super(message);
    this.name = 'FatalSseError';
  }
}

export interface SseHandle {
  abort: () => void;
  done: Promise<SseTerminalResult>;
}

export interface SSEConfig {
  body: Record<string, unknown>;
  handleMessage: (data: MESSAGE.Answer) => void;
  /** optional legacy callbacks */
  handleError?: (error: Error) => void;
  handleClose?: () => void;
  /** fired once when SSE response is accepted */
  onOpen?: () => void;
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof (value as ApiResponse<unknown>).code === 'string'
  );
}

async function parseFatalFromResponse(response: Response): Promise<FatalSseError> {
  let code: string | undefined;
  let message = `SSE open failed (${response.status})`;
  try {
    const body: unknown = await response.clone().json();
    if (isApiResponse(body)) {
      code = body.code;
      message = body.message || message;
    }
  } catch {
    // ignore parse failures; use status fallback
  }
  return new FatalSseError(response.status, code, message);
}

/**
 * Open an agent SSE stream. Resolves `done` exactly once with a terminal result.
 * `onerror` always throws so fetch-event-source will not auto-retry POST.
 */
export function querySSE(
  config: SSEConfig,
  url: string = DEFAULT_SSE_URL,
): SseHandle {
  const { body, handleMessage, handleError, handleClose, onOpen } = config;
  const controller = new AbortController();

  let settled = false;
  let terminalSeen = false;
  let abortReason: 'ABORT' | null = null;

  let resolveDone!: (result: SseTerminalResult) => void;
  const done = new Promise<SseTerminalResult>((resolve) => {
    resolveDone = resolve;
  });

  const settle = (result: SseTerminalResult) => {
    if (settled) {
      return;
    }
    settled = true;
    resolveDone(result);
    try {
      handleClose?.();
    } catch {
      // ignore legacy close errors
    }
  };

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  };
  const csrf = getCsrf();
  if (csrf?.headerName && csrf.token) {
    headers[csrf.headerName] = csrf.token;
  }

  const mapErrorToResult = (error: unknown): SseTerminalResult => {
    if (error instanceof FatalSseError) {
      if (error.httpStatus > 0) {
        return {
          kind: 'HTTP_ERROR',
          httpStatus: error.httpStatus,
          code: error.code,
          message: error.message,
        };
      }
      return {
        kind: 'INTERRUPTED',
        reason: 'FATAL',
        message: error.message,
      };
    }
    if (abortReason === 'ABORT' || controller.signal.aborted) {
      return {
        kind: 'INTERRUPTED',
        reason: 'ABORT'
      };
    }
    const message =
      error instanceof Error ? error.message : 'SSE connection error';
    return {
      kind: 'INTERRUPTED',
      reason: 'FATAL',
      message
    };
  };

  void fetchEventSource(url, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(body ?? {}),
    signal: controller.signal,
    openWhenHidden: true,
    async onopen(response: Response) {
      const contentType = response.headers.get('content-type') || '';
      if (
        response.ok &&
        contentType.toLowerCase().includes('text/event-stream')
      ) {
        try {
          onOpen?.();
        } catch {
          // ignore
        }
        return;
      }
      throw await parseFatalFromResponse(response);
    },
    onmessage(event: EventSourceMessage) {
      if (!event.data) {
        return;
      }

      let parsed: unknown;
      try {
        parsed = JSON.parse(event.data);
      } catch {
        const err = new FatalSseError(0, undefined, 'Failed to parse SSE message');
        settle(mapErrorToResult(err));
        handleError?.(err);
        throw err;
      }

      if (
        parsed === null ||
        typeof parsed !== 'object' ||
        Array.isArray(parsed)
      ) {
        const err = new FatalSseError(0, undefined, 'SSE message is not an object');
        settle(mapErrorToResult(err));
        handleError?.(err);
        throw err;
      }

      const data = parsed as MESSAGE.Answer;

      if (data.packageType === 'heartbeat') {
        return;
      }

      try {
        handleMessage(data);
      } catch (error) {
        // UI reduce/render bugs must not abort the SSE stream; otherwise the
        // backend sees Broken pipe and persists CLIENT_DISCONNECTED mid-run.
        console.error('SSE handleMessage failed; continuing stream', error);
      }

      if (data.finished === true) {
        terminalSeen = true;
        if (data.status === 'failed') {
          settle({
            kind: 'FAILED',
            errorMsg: data.errorMsg ?? null
          });
        } else {
          settle({ kind: 'COMPLETED' });
        }
      }
    },
    onerror(error: unknown) {
      const result = mapErrorToResult(error);
      settle(result);
      const err =
        error instanceof Error
          ? error
          : new Error(typeof error === 'string' ? error : 'SSE error');
      try {
        handleError?.(err);
      } catch {
        // ignore
      }
      // Throwing prevents fetch-event-source from auto-retrying the POST.
      throw err;
    },
    onclose() {
      if (settled) {
        return;
      }
      if (terminalSeen) {
        settle({ kind: 'COMPLETED' });
        return;
      }
      if (abortReason === 'ABORT' || controller.signal.aborted) {
        settle({
          kind: 'INTERRUPTED',
          reason: 'ABORT'
        });
        return;
      }
      settle({
        kind: 'INTERRUPTED',
        reason: 'EOF'
      });
    },
  }).catch((error: unknown) => {
    if (settled) {
      return;
    }
    if (abortReason === 'ABORT' || controller.signal.aborted) {
      settle({
        kind: 'INTERRUPTED',
        reason: 'ABORT'
      });
      return;
    }
    settle(mapErrorToResult(error));
  });

  const abort = () => {
    abortReason = 'ABORT';
    if (!settled) {
      settle({
        kind: 'INTERRUPTED',
        reason: 'ABORT'
      });
    }
    controller.abort();
  };

  const unregister = registerActiveSseAbort(abort);
  void done.finally(unregister);

  return {
    abort,
    done,
  };
}

export default querySSE;
