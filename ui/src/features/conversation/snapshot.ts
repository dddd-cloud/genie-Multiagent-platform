import type { StreamSnapshotEnvelope } from '@/contracts';

/** Plan §12.4 minimum envelope: payloadVersion===1, truncated boolean, events array. */
export function isSnapshotV1(parsed: unknown): parsed is StreamSnapshotEnvelope {
  if (!parsed || typeof parsed !== 'object') {
    return false;
  }
  const value = parsed as Record<string, unknown>;
  return (
    value.payloadVersion === 1 &&
    typeof value.truncated === 'boolean' &&
    Array.isArray(value.events)
  );
}

export function parseSnapshot(
  streamSnapshot: string | null,
): StreamSnapshotEnvelope | null {
  if (streamSnapshot == null || streamSnapshot === '') {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(streamSnapshot);
    if (!isSnapshotV1(parsed)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}
