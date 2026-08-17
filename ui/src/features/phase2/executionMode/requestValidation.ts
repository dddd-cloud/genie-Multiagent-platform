import { EXECUTION_MODES, type ExecutionMode } from '@/contracts';

export const PHASE2_REQUEST_ERROR_CODES = [
  'INVALID_SESSION_ID',
  'INVALID_REQUEST_ID',
  'INVALID_QUERY',
  'QUERY_TOO_LONG',
  'INVALID_EXECUTION_MODE',
  'INVALID_DEEP_THINK',
  'INVALID_OUTPUT_STYLE',
  'LTM_TOO_LONG',
  'SUMMARY_TOO_LONG',
  'LOCAL_CONTEXT_TOO_LARGE',
  'TOO_MANY_ALLOWED_AGENTS',
  'DIRECT_ALLOWED_AGENTS_FORBIDDEN',
  'INVALID_TEAM_ID',
  'DIRECT_TEAM_FORBIDDEN',
  'TEAM_AND_ALLOWED_AGENTS_EXCLUSIVE',
] as const;

export type Phase2RequestErrorCode =
  (typeof PHASE2_REQUEST_ERROR_CODES)[number];

export const QUERY_MAX_CODE_POINTS = 20_000;
export const LTM_MAX_CODE_POINTS = 12_000;
export const SUMMARY_MAX_CODE_POINTS = 20_000;
export const LOCAL_CONTEXT_MAX_CODE_POINTS = 30_000;
export const REQUEST_ID_MAX_LENGTH = 64;
export const ALLOWED_AGENTS_MAX = 20;

const UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

/** Unicode code-point length (not UTF-16 string.length). */
export function codePointLength(text: string): number {
  return [...text].length;
}

export function isUuidString(value: string): boolean {
  return value.length === 36 && UUID_RE.test(value);
}

export function isExecutionMode(value: unknown): value is ExecutionMode {
  return (
    typeof value === 'string' &&
    (EXECUTION_MODES as readonly string[]).includes(value)
  );
}

export function dedupeAllowedAgentIds(ids: readonly string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const id of ids) {
    if (typeof id !== 'string' || id.length === 0) continue;
    if (seen.has(id)) continue;
    seen.add(id);
    out.push(id);
  }
  return out;
}

export interface Phase2RequestValidationError {
  ok: false;
  code: Phase2RequestErrorCode;
  message: string;
}

export function validationError(
  code: Phase2RequestErrorCode,
  message: string,
): Phase2RequestValidationError {
  return {
    ok: false,
    code,
    message
  };
}
