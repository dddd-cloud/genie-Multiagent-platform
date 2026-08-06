const FORBIDDEN_REQUEST_FIELDS = [
  'ownerId',
  'tenantId',
  'userId',
  'traceId',
  'steps',
  'inputRefs',
  'attemptNo',
  'credentialConfigured',
] as const;

function walkForbidden(
  value: unknown,
  path: string,
): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (Array.isArray(value)) {
    for (let i = 0; i < value.length; i += 1) {
      const hit = walkForbidden(value[i], `${path}[${i}]`);
      if (hit) return hit;
    }
    return null;
  }
  if (typeof value === 'object') {
    for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
      const childPath = path ? `${path}.${key}` : key;
      if ((FORBIDDEN_REQUEST_FIELDS as readonly string[]).includes(key)) {
        return childPath;
      }
      const hit = walkForbidden(child, childPath);
      if (hit) return hit;
    }
  }
  return null;
}

/** Returns the first forbidden field path, or null if body is clean. */
export function assertNoForbiddenRequestFields(body: unknown): string | null {
  return walkForbidden(body, '');
}

function containsPlaintext(value: unknown, plaintext: string): boolean {
  if (typeof value === 'string') {
    return value.includes(plaintext);
  }
  if (Array.isArray(value)) {
    return value.some((item) => containsPlaintext(item, plaintext));
  }
  if (value && typeof value === 'object') {
    return Object.values(value as Record<string, unknown>).some((child) =>
      containsPlaintext(child, plaintext),
    );
  }
  return false;
}

/**
 * Rejects responses that echo write-only credential plaintext.
 * Returns a path-like reason string, or null if safe.
 */
export function assertNoCredentialEcho(
  response: unknown,
  credentialPlaintext?: string | null,
): string | null {
  if (!credentialPlaintext || !credentialPlaintext.trim()) {
    return null;
  }
  if (containsPlaintext(response, credentialPlaintext)) {
    return 'credential plaintext echoed in response';
  }
  return null;
}
