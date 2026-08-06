import {
  LONG_TERM_MEMORY_SECTIONS,
  MEMORY_PATCH_OPERATIONS,
  type MemoryPatchItem,
} from '@/contracts/phase2';
import {
  MEMORY_LIMITS,
  MemoryError,
  codePointLength,
} from './types';

const SECRET_KEYWORD_RE =
  /(password|密码|token|api\s*key|authorization|bearer|private\s*key|secret|cookie)/i;
const SK_PATTERN_RE = /\bsk-[A-Za-z0-9_-]{8,}\b/;
function hasBadKeyChars(key: string): boolean {
  for (let i = 0; i < key.length; i += 1) {
    const ch = key[i];
    const code = key.charCodeAt(i);
    if (ch === '\r' || ch === '\n' || ch === '#' || ch === '`') {
      return true;
    }
    if (code <= 0x1f || code === 0x7f) {
      return true;
    }
  }
  return false;
}

export type PatchValidationResult =
  | { ok: true; patches: MemoryPatchItem[] }
  | { ok: false; errorCode: MemoryError['errorCode']; reason: string };

function containsSecret(text: string): boolean {
  return SECRET_KEYWORD_RE.test(text) || SK_PATTERN_RE.test(text);
}

function isValidSection(section: string): boolean {
  return (LONG_TERM_MEMORY_SECTIONS as readonly string[]).includes(section);
}

function isValidOperation(operation: string): boolean {
  return (MEMORY_PATCH_OPERATIONS as readonly string[]).includes(operation);
}

export function validateMemoryPatchItem(
  item: unknown,
): PatchValidationResult {
  if (typeof item !== 'object' || item === null || Array.isArray(item)) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'patch is not an object',
    };
  }

  const patch = item as Record<string, unknown>;
  if (
    typeof patch.operation !== 'string' ||
    !isValidOperation(patch.operation)
  ) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'invalid operation',
    };
  }
  if (typeof patch.section !== 'string' || !isValidSection(patch.section)) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'invalid section',
    };
  }
  if (typeof patch.key !== 'string' || patch.key.length === 0) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'invalid key',
    };
  }
  if (codePointLength(patch.key) > MEMORY_LIMITS.KEY_MAX_CODEPOINTS) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'key too long',
    };
  }
  if (hasBadKeyChars(patch.key)) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'key contains forbidden characters',
    };
  }
  if (containsSecret(patch.key)) {
    return {
      ok: false,
      errorCode: 'MEMORY_SECRET_REJECTED',
      reason: 'secret in key',
    };
  }

  if (patch.operation === 'DELETE') {
    if (patch.value !== null) {
      return {
        ok: false,
        errorCode: 'MEMORY_VALIDATION_FAILED',
        reason: 'DELETE value must be null',
      };
    }
    return {
      ok: true,
      patches: [
        {
          operation: 'DELETE',
          section: patch.section as MemoryPatchItem['section'],
          key: patch.key,
          value: null,
        },
      ],
    };
  }

  if (typeof patch.value !== 'string' || patch.value.length === 0) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'UPSERT value must be non-empty string',
    };
  }
  if (codePointLength(patch.value) > MEMORY_LIMITS.VALUE_MAX_CODEPOINTS) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'value too long',
    };
  }
  if (containsSecret(patch.value)) {
    return {
      ok: false,
      errorCode: 'MEMORY_SECRET_REJECTED',
      reason: 'secret in value',
    };
  }

  return {
    ok: true,
    patches: [
      {
        operation: 'UPSERT',
        section: patch.section as MemoryPatchItem['section'],
        key: patch.key,
        value: patch.value,
      },
    ],
  };
}

export function validateMemoryPatches(
  patches: unknown,
): PatchValidationResult {
  if (!Array.isArray(patches)) {
    return {
      ok: false,
      errorCode: 'MEMORY_VALIDATION_FAILED',
      reason: 'patches must be an array',
    };
  }
  const validated: MemoryPatchItem[] = [];
  for (const item of patches) {
    const result = validateMemoryPatchItem(item);
    if (!result.ok) {
      return result;
    }
    validated.push(...result.patches);
  }
  return {
    ok: true,
    patches: validated
  };
}

export function assertValidMemoryPatches(patches: unknown): MemoryPatchItem[] {
  const result = validateMemoryPatches(patches);
  if (!result.ok) {
    throw new MemoryError(result.errorCode, result.reason, false);
  }
  return result.patches;
}
