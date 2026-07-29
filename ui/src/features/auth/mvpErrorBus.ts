import type { MvpApiError } from '@/services/apiError';

/**
 * Minimal AUTH/CSRF callback slot for the shared request/SSE layer.
 * Not a global event-bus framework — AuthProvider registers one handler.
 */
type MvpErrorHandler = (error: MvpApiError) => void;

let handler: MvpErrorHandler | undefined;

export function setMvpErrorHandler(next: MvpErrorHandler | undefined): void {
  handler = next;
}

export function notifyMvpError(error: MvpApiError): void {
  handler?.(error);
}
