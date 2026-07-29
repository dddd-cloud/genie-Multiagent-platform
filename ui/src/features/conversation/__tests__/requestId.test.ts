import { describe, expect, it } from 'vitest';
import { createRequestId, isUuid } from '../requestId';

describe('requestId', () => {
  it('createRequestId returns UUID v4 format', () => {
    const id = createRequestId();
    expect(isUuid(id)).toBe(true);
    expect(id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
  });

  it('createRequestId yields unique values', () => {
    const ids = new Set(Array.from({ length: 20 }, () => createRequestId()));
    expect(ids.size).toBe(20);
  });

  it('isUuid rejects non-uuid strings', () => {
    expect(isUuid('not-a-uuid')).toBe(false);
    expect(isUuid('')).toBe(false);
    expect(isUuid('00000000-0000-0000-0000-000000000000')).toBe(false);
  });
});
