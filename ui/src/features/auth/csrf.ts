import type { CsrfTokenResponse } from '@/contracts';

type CsrfState = Pick<CsrfTokenResponse, 'headerName' | 'token'> | null;

let csrf: CsrfState = null;

export function getCsrf(): CsrfState {
  return csrf;
}

export function setCsrf(next: CsrfState): void {
  csrf = next;
}

export function clearCsrf(): void {
  csrf = null;
}

/** Mutates and returns `headers` so callers that ignore the return value still work. */
export function applyCsrfHeaders(
  headers: Record<string, string> = {},
): Record<string, string> {
  const current = getCsrf();
  if (current?.token) {
    headers[current.headerName] = current.token;
  }
  return headers;
}
