import type { ExecutionMode, Phase2GptQueryRequest } from '@/contracts';
import {
  ALLOWED_AGENTS_MAX,
  LOCAL_CONTEXT_MAX_CODE_POINTS,
  LTM_MAX_CODE_POINTS,
  QUERY_MAX_CODE_POINTS,
  REQUEST_ID_MAX_LENGTH,
  SUMMARY_MAX_CODE_POINTS,
  codePointLength,
  dedupeAllowedAgentIds,
  isExecutionMode,
  isUuidString,
  validationError,
  type Phase2RequestValidationError,
} from './requestValidation';

export interface BuildPhase2RequestInput {
  sessionId: string;
  requestId: string;
  query: string;
  executionMode: ExecutionMode;
  deepThink: 0 | 1;
  outputStyle: string;
  allowedAgentIds: readonly string[];
  longTermMemory: string;
  conversationSummary: string;
  teamId?: string | null;
}

export type BuildPhase2RequestResult =
  | { ok: true; request: Phase2GptQueryRequest }
  | Phase2RequestValidationError;

/**
 * Build a frozen Phase2GptQueryRequest after client-side validation.
 * Length checks use Unicode code points via `[...text].length`.
 */
export function buildPhase2GptQueryRequest(
  input: BuildPhase2RequestInput,
): BuildPhase2RequestResult {
  if (!isUuidString(input.sessionId)) {
    return validationError('INVALID_SESSION_ID', 'sessionId must be a UUID');
  }

  if (
    typeof input.requestId !== 'string' ||
    input.requestId.length < 1 ||
    input.requestId.length > REQUEST_ID_MAX_LENGTH
  ) {
    return validationError(
      'INVALID_REQUEST_ID',
      `requestId must be 1..${REQUEST_ID_MAX_LENGTH} characters`,
    );
  }

  if (typeof input.query !== 'string' || input.query.length === 0) {
    return validationError('INVALID_QUERY', 'query must be non-empty');
  }
  const queryCp = codePointLength(input.query);
  if (queryCp < 1 || queryCp > QUERY_MAX_CODE_POINTS) {
    return validationError(
      'QUERY_TOO_LONG',
      `query must be 1..${QUERY_MAX_CODE_POINTS} code points`,
    );
  }

  if (!isExecutionMode(input.executionMode)) {
    return validationError(
      'INVALID_EXECUTION_MODE',
      'executionMode must be AUTO, DIRECT, or ORCHESTRATED',
    );
  }

  if (input.deepThink !== 0 && input.deepThink !== 1) {
    return validationError(
      'INVALID_DEEP_THINK',
      'deepThink must be 0 or 1',
    );
  }

  if (
    typeof input.outputStyle !== 'string' ||
    input.outputStyle.length < 1
  ) {
    return validationError(
      'INVALID_OUTPUT_STYLE',
      'outputStyle must be a non-empty string',
    );
  }

  const longTermMemory =
    typeof input.longTermMemory === 'string' ? input.longTermMemory : '';
  const conversationSummary =
    typeof input.conversationSummary === 'string'
      ? input.conversationSummary
      : '';

  const ltmCp = codePointLength(longTermMemory);
  if (ltmCp > LTM_MAX_CODE_POINTS) {
    return validationError(
      'LTM_TOO_LONG',
      `longTermMemory must be <= ${LTM_MAX_CODE_POINTS} code points`,
    );
  }

  const summaryCp = codePointLength(conversationSummary);
  if (summaryCp > SUMMARY_MAX_CODE_POINTS) {
    return validationError(
      'SUMMARY_TOO_LONG',
      `conversationSummary must be <= ${SUMMARY_MAX_CODE_POINTS} code points`,
    );
  }

  if (ltmCp + summaryCp > LOCAL_CONTEXT_MAX_CODE_POINTS) {
    return validationError(
      'LOCAL_CONTEXT_TOO_LARGE',
      `longTermMemory + conversationSummary must be <= ${LOCAL_CONTEXT_MAX_CODE_POINTS} code points`,
    );
  }

  let allowedAgentIds = dedupeAllowedAgentIds(input.allowedAgentIds);
  if (input.executionMode === 'DIRECT') {
    if (allowedAgentIds.length > 0) {
      return validationError(
        'DIRECT_ALLOWED_AGENTS_FORBIDDEN',
        'DIRECT mode requires empty allowedAgentIds',
      );
    }
    allowedAgentIds = [];
  }

  if (allowedAgentIds.length > ALLOWED_AGENTS_MAX) {
    return validationError(
      'TOO_MANY_ALLOWED_AGENTS',
      `allowedAgentIds must be <= ${ALLOWED_AGENTS_MAX} after dedupe`,
    );
  }

  const teamId =
    typeof input.teamId === 'string' && input.teamId.length > 0
      ? input.teamId
      : null;
  if (teamId !== null) {
    if (!isUuidString(teamId)) {
      return validationError('INVALID_TEAM_ID', 'teamId must be a UUID');
    }
    if (input.executionMode === 'DIRECT') {
      return validationError(
        'DIRECT_TEAM_FORBIDDEN',
        'DIRECT mode must not carry teamId',
      );
    }
    if (allowedAgentIds.length > 0) {
      return validationError(
        'TEAM_AND_ALLOWED_AGENTS_EXCLUSIVE',
        'teamId and allowedAgentIds are mutually exclusive',
      );
    }
  }

  const request: Phase2GptQueryRequest = {
    sessionId: input.sessionId,
    requestId: input.requestId,
    query: input.query,
    executionMode: input.executionMode,
    deepThink: input.deepThink,
    outputStyle: input.outputStyle,
    allowedAgentIds,
    localContext: {
      schemaVersion: 1,
      longTermMemory,
      conversationSummary,
    },
    ...(teamId === null ? {} : { teamId }),
  };

  return {
    ok: true,
    request
  };
}
