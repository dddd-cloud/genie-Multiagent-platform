import {
  BROWSER_SKILL_EXECUTION_MANIFEST_PATH,
  BROWSER_SKILL_EXECUTION_SCHEMA_VERSION,
  type BrowserSkillExecutionManifest,
  type BrowserSkillExecutionSignal,
} from '@/contracts';
import { BROWSER_SKILL_EXECUTION_LIMITS as LIMITS } from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

/**
 * Validate a live SSE browserSkillExecution signal.
 */
export function parseBrowserSkillExecutionSignal(
  raw: unknown,
): BrowserSkillExecutionSignal | null {
  if (!isRecord(raw)) return null;
  if (raw.schemaVersion !== BROWSER_SKILL_EXECUTION_SCHEMA_VERSION) return null;
  if (!isNonEmptyString(raw.executionId)) return null;
  if (!isNonEmptyString(raw.skillId)) return null;
  if (!isNonEmptyString(raw.entrypointName)) return null;
  if (!isNonEmptyString(raw.packageHash)) return null;
  if (
    typeof raw.timeoutMs !== 'number' ||
    !Number.isFinite(raw.timeoutMs) ||
    raw.timeoutMs <= 0
  ) {
    return null;
  }
  return {
    schemaVersion: BROWSER_SKILL_EXECUTION_SCHEMA_VERSION,
    executionId: raw.executionId,
    skillId: raw.skillId,
    entrypointName: raw.entrypointName,
    packageHash: raw.packageHash,
    timeoutMs: raw.timeoutMs,
  };
}

export function extractBrowserSkillSignalFromResult(
  result: unknown,
): BrowserSkillExecutionSignal | null {
  if (!isRecord(result)) return null;
  if (result.packageType !== 'skill_execution') return null;
  if (result.finished === true) return null;
  const resultMap = result.resultMap;
  if (!isRecord(resultMap)) return null;
  return parseBrowserSkillExecutionSignal(resultMap.browserSkillExecution);
}

/** Reject http(s)/file/git/credential package specs. */
export function isAllowedPyodidePackageSpec(spec: string): boolean {
  const trimmed = spec.trim();
  if (!trimmed || trimmed.length > LIMITS.MAX_PACKAGE_SPEC_LENGTH) {
    return false;
  }
  if (/\s/.test(trimmed)) return false;
  const lower = trimmed.toLowerCase();
  if (
    lower.includes('://') ||
    lower.startsWith('git+') ||
    lower.includes('@git') ||
    lower.includes('file:') ||
    lower.includes('http:') ||
    lower.includes('https:')
  ) {
    return false;
  }
  // Basic name / name==version / name>=version
  return /^[A-Za-z0-9][A-Za-z0-9._+-]*(?:[<>=!~]=?[^@\s]+)?$/.test(trimmed);
}

export function isSafeRelativePath(path: string): boolean {
  if (!path || path.length > 512) return false;
  if (path.startsWith('/') || path.startsWith('\\')) return false;
  if (/^[A-Za-z]:[\\/]/.test(path)) return false;
  const parts = path.replace(/\\/g, '/').split('/');
  for (const part of parts) {
    if (!part || part === '.' || part === '..') return false;
  }
  return true;
}

export function parseExecutionManifest(
  raw: unknown,
  expectedExecutionId: string,
): BrowserSkillExecutionManifest | null {
  if (!isRecord(raw)) return null;
  if (raw.schemaVersion !== BROWSER_SKILL_EXECUTION_SCHEMA_VERSION) return null;
  if (!isNonEmptyString(raw.executionId)) return null;
  if (raw.executionId !== expectedExecutionId) return null;
  if (!isNonEmptyString(raw.entrypointName)) return null;
  if (!isNonEmptyString(raw.scriptRelativePath)) return null;
  if (!isSafeRelativePath(raw.scriptRelativePath)) return null;
  if (!Array.isArray(raw.packages)) return null;
  if (raw.packages.length > LIMITS.MAX_PACKAGES) return null;
  const packages: string[] = [];
  for (const item of raw.packages) {
    if (typeof item !== 'string' || !isAllowedPyodidePackageSpec(item)) {
      return null;
    }
    packages.push(item);
  }
  if (typeof raw.inputJson !== 'string') return null;
  if (raw.inputJson.length > LIMITS.MAX_INPUT_JSON_CHARS) return null;
  try {
    JSON.parse(raw.inputJson);
  } catch {
    return null;
  }
  return {
    schemaVersion: BROWSER_SKILL_EXECUTION_SCHEMA_VERSION,
    executionId: raw.executionId,
    entrypointName: raw.entrypointName,
    scriptRelativePath: raw.scriptRelativePath,
    packages,
    inputJson: raw.inputJson,
  };
}

export { BROWSER_SKILL_EXECUTION_MANIFEST_PATH };
