import { fetchEventSource, type EventSourceMessage } from '@microsoft/fetch-event-source';
import type { ApiResponse } from '@/contracts';
import { getCsrf } from '@/features/auth/csrf';
import {
  FatalSseError,
  registerActiveSseAbort,
  type SseHandle,
  type SseTerminalResult,
} from './querySSE';

const DEFAULT_DATA_AGENT_SSE_URL = '/data/chatQuery';

export type DataAgentEvent = {
  eventType: string;
  data: unknown;
};

export interface DataAgentSseConfig {
  body: Record<string, unknown>;
  handleMessage: (data: DataAgentEvent) => void;
  handleError?: (error: Error) => void;
  handleClose?: () => void;
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
    // ignore
  }
  return new FatalSseError(response.status, code, message);
}

/**
 * DataAgent SSE helper. Preserves THINK / CHART_DATA / ERROR / READY for callers.
 * READY → COMPLETED, ERROR → FAILED; same cookie + CSRF + abort + onerror-throw rules.
 */
export function dataAgentSSE(
  config: DataAgentSseConfig,
  url: string = DEFAULT_DATA_AGENT_SSE_URL,
): SseHandle {
  const { body, handleMessage, handleError, handleClose, onOpen } = config;
  const controller = new AbortController();

  let settled = false;
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
      // ignore
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

      const data = parsed as DataAgentEvent;
      const eventType = data.eventType;

      try {
        handleMessage(data);
      } catch (error) {
        const err =
          error instanceof Error ? error : new Error(String(error));
        settle({
          kind: 'INTERRUPTED',
          reason: 'FATAL',
          message: err.message,
        });
        handleError?.(err);
        throw err;
      }

      if (eventType === 'READY') {
        settle({ kind: 'COMPLETED' });
      } else if (eventType === 'ERROR') {
        const errorMsg =
          typeof data.data === 'string'
            ? data.data
            : data.data != null
              ? String(data.data)
              : null;
        settle({
          kind: 'FAILED',
          errorMsg
        });
      }
    },
    onerror(error: unknown) {
      settle(mapErrorToResult(error));
      const err =
        error instanceof Error
          ? error
          : new Error(typeof error === 'string' ? error : 'SSE error');
      try {
        handleError?.(err);
      } catch {
        // ignore
      }
      throw err;
    },
    onclose() {
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

export default dataAgentSSE;
