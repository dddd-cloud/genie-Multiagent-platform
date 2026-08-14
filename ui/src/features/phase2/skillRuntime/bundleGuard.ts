import {
  BROWSER_SKILL_EXECUTION_MANIFEST_PATH,
  type BrowserSkillExecutionManifest,
  type BrowserSkillExecutionSignal,
} from '@/contracts';
import { unzipSync } from 'fflate';
import { parseExecutionManifest, isSafeRelativePath } from './signal';
import { BROWSER_SKILL_EXECUTION_LIMITS as LIMITS } from './types';

export class BundleValidationError extends Error {
  readonly errorCode: string;

  constructor(errorCode: string, message: string) {
    super(message);
    this.name = 'BundleValidationError';
    this.errorCode = errorCode;
  }
}

/** ZIP local-file magic `PK\x03\x04`. */
export function assertZipMagic(zipBytes: ArrayBuffer): void {
  if (zipBytes.byteLength < 4) {
    throw new BundleValidationError('SKILL_PACKAGE_INVALID', 'zip too small');
  }
  const view = new Uint8Array(zipBytes);
  if (
    view[0] !== 0x50 ||
    view[1] !== 0x4b ||
    view[2] !== 0x03 ||
    view[3] !== 0x04
  ) {
    throw new BundleValidationError(
      'SKILL_PACKAGE_INVALID',
      'response is not a zip archive',
    );
  }
}

/**
 * Defensively unpack a skill execution ZIP on the main thread.
 * Rejects traversal, absolute paths, oversized archives / entries.
 */
export function unpackSkillBundle(
  zipBytes: ArrayBuffer,
): Record<string, Uint8Array> {
  if (zipBytes.byteLength <= 0 || zipBytes.byteLength > LIMITS.MAX_ZIP_BYTES) {
    throw new BundleValidationError(
      'SKILL_PACKAGE_INVALID',
      `zip size out of bounds: ${zipBytes.byteLength}`,
    );
  }
  assertZipMagic(zipBytes);

  let entries: Record<string, Uint8Array>;
  try {
    entries = unzipSync(new Uint8Array(zipBytes), {
      filter(file) {
        return !file.name.endsWith('/');
      },
    });
  } catch (error) {
    throw new BundleValidationError(
      'SKILL_PACKAGE_INVALID',
      error instanceof Error ? error.message : 'invalid zip',
    );
  }

  const names = Object.keys(entries);
  if (names.length === 0 || names.length > LIMITS.MAX_ZIP_ENTRIES) {
    throw new BundleValidationError(
      'SKILL_PACKAGE_INVALID',
      `entry count out of bounds: ${names.length}`,
    );
  }

  const out: Record<string, Uint8Array> = {};
  for (const name of names) {
    const normalized = name.replace(/\\/g, '/');
    if (!isSafeRelativePath(normalized)) {
      throw new BundleValidationError(
        'SKILL_PACKAGE_INVALID',
        `unsafe zip entry path: ${name}`,
      );
    }
    const data = entries[name];
    if (!data || data.byteLength > LIMITS.MAX_ENTRY_BYTES) {
      throw new BundleValidationError(
        'SKILL_PACKAGE_INVALID',
        `entry too large: ${name}`,
      );
    }
    out[normalized] = data;
  }
  return out;
}

export function readManifestBytes(
  files: Record<string, Uint8Array>,
  manifestPath: string = BROWSER_SKILL_EXECUTION_MANIFEST_PATH,
): string {
  const bytes = files[manifestPath];
  if (!bytes) {
    throw new BundleValidationError(
      'SKILL_PACKAGE_INVALID',
      `missing ${manifestPath}`,
    );
  }
  return new TextDecoder('utf-8').decode(bytes);
}

/**
 * Unpack + parse + bind manifest to the live signal (executionId / entrypoint).
 */
export function validateSkillBundleAgainstSignal(
  zipBytes: ArrayBuffer,
  signal: BrowserSkillExecutionSignal,
): {
  files: Record<string, Uint8Array>;
  manifest: BrowserSkillExecutionManifest;
} {
  const files = unpackSkillBundle(zipBytes);
  let parsed: unknown;
  try {
    parsed = JSON.parse(readManifestBytes(files));
  } catch (error) {
    throw new BundleValidationError(
      'SKILL_PACKAGE_INVALID',
      error instanceof Error ? error.message : 'manifest json invalid',
    );
  }
  const manifest = parseExecutionManifest(parsed, signal.executionId);
  if (!manifest) {
    throw new BundleValidationError(
      'SKILL_PACKAGE_INVALID',
      'invalid execution manifest',
    );
  }
  if (manifest.entrypointName !== signal.entrypointName) {
    throw new BundleValidationError(
      'SKILL_PACKAGE_INVALID',
      'entrypointName mismatch vs signal',
    );
  }
  if (!files[manifest.scriptRelativePath]) {
    throw new BundleValidationError(
      'SKILL_RESOURCE_NOT_FOUND',
      `missing script ${manifest.scriptRelativePath}`,
    );
  }
  return {
    files,
    manifest
  };
}
