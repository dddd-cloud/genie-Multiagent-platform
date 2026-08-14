import { describe, expect, it } from 'vitest';
import { buildPhase2GptQueryRequest } from '@/features/phase2/executionMode/phase2RequestBuilder';
import {
  LOCAL_CONTEXT_MAX_CODE_POINTS,
  codePointLength,
} from '@/features/phase2/executionMode/requestValidation';

const SESSION = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

describe('LocalContextLimitRegressionTest', () => {
  it('rejects combined local context above LOCAL_CONTEXT_MAX with LOCAL_CONTEXT_TOO_LARGE', () => {
    const longTermMemory = 'a'.repeat(12_000);
    const conversationSummary = 'b'.repeat(18_001);
    expect(
      codePointLength(longTermMemory) + codePointLength(conversationSummary),
    ).toBeGreaterThan(LOCAL_CONTEXT_MAX_CODE_POINTS);

    const result = buildPhase2GptQueryRequest({
      sessionId: SESSION,
      requestId: 'req-local-ctx',
      query: 'hello',
      executionMode: 'AUTO',
      deepThink: 0,
      outputStyle: 'docs',
      allowedAgentIds: [],
      longTermMemory,
      conversationSummary,
    });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.code).toBe('LOCAL_CONTEXT_TOO_LARGE');
    }
  });

  it('accepts combined local context at LOCAL_CONTEXT_MAX', () => {
    const longTermMemory = 'a'.repeat(12_000);
    const conversationSummary = 'b'.repeat(18_000);
    expect(
      codePointLength(longTermMemory) + codePointLength(conversationSummary),
    ).toBe(LOCAL_CONTEXT_MAX_CODE_POINTS);

    const result = buildPhase2GptQueryRequest({
      sessionId: SESSION,
      requestId: 'req-local-ok',
      query: 'hello',
      executionMode: 'AUTO',
      deepThink: 0,
      outputStyle: 'docs',
      allowedAgentIds: [],
      longTermMemory,
      conversationSummary,
    });
    expect(result.ok).toBe(true);
  });
});
