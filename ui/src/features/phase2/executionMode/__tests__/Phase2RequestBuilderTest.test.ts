import { describe, expect, it } from 'vitest';
import { buildPhase2GptQueryRequest } from '../phase2RequestBuilder';
import {
  LOCAL_CONTEXT_MAX_CODE_POINTS,
  LTM_MAX_CODE_POINTS,
  QUERY_MAX_CODE_POINTS,
  SUMMARY_MAX_CODE_POINTS,
  codePointLength,
} from '../requestValidation';

const SESSION = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
const TEAM = '11111111-2222-3333-4444-555555555555';

function baseInput(
  overrides: Partial<Parameters<typeof buildPhase2GptQueryRequest>[0]> = {},
) {
  return {
    sessionId: SESSION,
    requestId: 'req-1',
    query: 'hello',
    executionMode: 'AUTO' as const,
    deepThink: 0 as const,
    outputStyle: 'docs',
    allowedAgentIds: [] as string[],
    longTermMemory: '',
    conversationSummary: '',
    ...overrides,
  };
}

describe('Phase2RequestBuilderTest', () => {
  it('builds a valid AUTO request', () => {
    const result = buildPhase2GptQueryRequest(baseInput());
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.request).toEqual({
      sessionId: SESSION,
      requestId: 'req-1',
      query: 'hello',
      executionMode: 'AUTO',
      deepThink: 0,
      outputStyle: 'docs',
      allowedAgentIds: [],
      localContext: {
        schemaVersion: 1,
        longTermMemory: '',
        conversationSummary: '',
      },
    });
  });

  it('rejects invalid sessionId', () => {
    const result = buildPhase2GptQueryRequest(
      baseInput({ sessionId: 'not-a-uuid' }),
    );
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.code).toBe('INVALID_SESSION_ID');
  });

  it('rejects requestId outside 1..64', () => {
    expect(
      buildPhase2GptQueryRequest(baseInput({ requestId: '' })).ok,
    ).toBe(false);
    expect(
      buildPhase2GptQueryRequest(baseInput({ requestId: 'x'.repeat(65) })).ok,
    ).toBe(false);
  });

  it('uses Unicode code points for query length, not string.length', () => {
    // One emoji is 1 code point but 2 UTF-16 code units.
    const emoji = '🙂';
    expect(emoji.length).toBe(2);
    expect(codePointLength(emoji)).toBe(1);

    const almostMax = emoji.repeat(QUERY_MAX_CODE_POINTS);
    const ok = buildPhase2GptQueryRequest(baseInput({ query: almostMax }));
    expect(ok.ok).toBe(true);

    const tooLong = emoji.repeat(QUERY_MAX_CODE_POINTS + 1);
    // string.length would be 2*(MAX+1) which is also over, but more importantly
    // a string of MAX+1 code points must fail even when each is 1 unit.
    const asciiTooLong = 'a'.repeat(QUERY_MAX_CODE_POINTS + 1);
    const bad = buildPhase2GptQueryRequest(baseInput({ query: asciiTooLong }));
    expect(bad.ok).toBe(false);
    if (bad.ok) return;
    expect(bad.code).toBe('QUERY_TOO_LONG');
    expect(tooLong.length).toBeGreaterThan(QUERY_MAX_CODE_POINTS);
  });

  it('enforces LTM, summary, and combined localContext budgets', () => {
    const ltmBad = buildPhase2GptQueryRequest(
      baseInput({ longTermMemory: 'a'.repeat(LTM_MAX_CODE_POINTS + 1) }),
    );
    expect(ltmBad.ok).toBe(false);
    if (!ltmBad.ok) expect(ltmBad.code).toBe('LTM_TOO_LONG');

    const summaryBad = buildPhase2GptQueryRequest(
      baseInput({conversationSummary: 'a'.repeat(SUMMARY_MAX_CODE_POINTS + 1),}),
    );
    expect(summaryBad.ok).toBe(false);
    if (!summaryBad.ok) expect(summaryBad.code).toBe('SUMMARY_TOO_LONG');

    const combinedBad = buildPhase2GptQueryRequest(
      baseInput({
        longTermMemory: 'a'.repeat(12_000),
        conversationSummary: 'b'.repeat(18_001),
      }),
    );
    expect(
      codePointLength('a'.repeat(12_000)) + codePointLength('b'.repeat(18_001)),
    ).toBeGreaterThan(LOCAL_CONTEXT_MAX_CODE_POINTS);
    expect(combinedBad.ok).toBe(false);
    if (!combinedBad.ok) {
      expect(combinedBad.code).toBe('LOCAL_CONTEXT_TOO_LARGE');
    }
  });

  it('dedupes allowedAgentIds and caps at 20', () => {
    const ids = Array.from({ length: 21 }, (_, i) => `agent-${i}`);
    const tooMany = buildPhase2GptQueryRequest(
      baseInput({
        executionMode: 'ORCHESTRATED',
        allowedAgentIds: ids,
      }),
    );
    expect(tooMany.ok).toBe(false);
    if (!tooMany.ok) expect(tooMany.code).toBe('TOO_MANY_ALLOWED_AGENTS');

    const deduped = buildPhase2GptQueryRequest(
      baseInput({
        executionMode: 'ORCHESTRATED',
        allowedAgentIds: ['a1', 'a1', 'a2'],
      }),
    );
    expect(deduped.ok).toBe(true);
    if (!deduped.ok) return;
    expect(deduped.request.allowedAgentIds).toEqual(['a1', 'a2']);
  });

  it('forces DIRECT allowedAgentIds to be empty', () => {
    const bad = buildPhase2GptQueryRequest(
      baseInput({
        executionMode: 'DIRECT',
        allowedAgentIds: ['agent-1'],
      }),
    );
    expect(bad.ok).toBe(false);
    if (!bad.ok) expect(bad.code).toBe('DIRECT_ALLOWED_AGENTS_FORBIDDEN');

    const ok = buildPhase2GptQueryRequest(
      baseInput({
        executionMode: 'DIRECT',
        deepThink: 1,
        allowedAgentIds: [],
      }),
    );
    expect(ok.ok).toBe(true);
    if (!ok.ok) return;
    expect(ok.request.allowedAgentIds).toEqual([]);
    expect(ok.request.deepThink).toBe(1);
  });

  it('omits teamId unless a team is selected', () => {
    const withoutTeam = buildPhase2GptQueryRequest(baseInput({ teamId: null }));
    expect(withoutTeam.ok).toBe(true);
    if (!withoutTeam.ok) return;
    expect('teamId' in withoutTeam.request).toBe(false);

    const withTeam = buildPhase2GptQueryRequest(
      baseInput({ executionMode: 'ORCHESTRATED', teamId: TEAM }),
    );
    expect(withTeam.ok).toBe(true);
    if (!withTeam.ok) return;
    expect(withTeam.request.teamId).toBe(TEAM);
  });

  it('rejects a non-UUID teamId', () => {
    const result = buildPhase2GptQueryRequest(baseInput({ teamId: 'team-1' }));
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.code).toBe('INVALID_TEAM_ID');
  });

  it('rejects teamId in DIRECT mode', () => {
    const result = buildPhase2GptQueryRequest(
      baseInput({ executionMode: 'DIRECT', teamId: TEAM }),
    );
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.code).toBe('DIRECT_TEAM_FORBIDDEN');
  });

  it('rejects teamId together with allowedAgentIds', () => {
    const result = buildPhase2GptQueryRequest(
      baseInput({
        executionMode: 'ORCHESTRATED',
        allowedAgentIds: ['a1'],
        teamId: TEAM,
      }),
    );
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.code).toBe('TEAM_AND_ALLOWED_AGENTS_EXCLUSIVE');
  });
});
