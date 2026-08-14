import type { BrowserSkillExecutionResult } from '@/contracts';
import { BROWSER_SKILL_EXECUTION_SCHEMA_VERSION } from '@/contracts';
import { applyCsrfHeaders } from '@/features/auth/csrf';
import { MvpApiError } from '@/services/apiError';
import axios from 'axios';
import { phase2Post } from './client';
import request from '@/utils/request';
import { assertZipMagic, BundleValidationError } from '@/features/phase2/skillRuntime/bundleGuard';

const EXECUTIONS_BASE = '/api/v2/skill-executions';

function toArrayBuffer(data: unknown): ArrayBuffer | null {
  if (data instanceof ArrayBuffer) return data;
  if (ArrayBuffer.isView(data)) {
    const view = data as ArrayBufferView;
    return view.buffer.slice(
      view.byteOffset,
      view.byteOffset + view.byteLength,
    );
  }
  return null;
}

function tryDecodeJsonError(buf: ArrayBuffer): string | null {
  try {
    const text = new TextDecoder('utf-8').decode(buf).trim();
    if (!text.startsWith('{')) return null;
    const parsed = JSON.parse(text) as { code?: string; message?: string };
    if (typeof parsed.code === 'string') {
      return parsed.message || parsed.code;
    }
  } catch {
    /* not json */
  }
  return null;
}

/**
 * GET immutable skill execution ZIP for the current login session.
 */
export async function fetchSkillExecutionBundle(
  executionId: string,
  signal?: AbortSignal,
): Promise<ArrayBuffer> {
  const headers: Record<string, string> = {Accept: 'application/zip,application/octet-stream,*/*',};
  applyCsrfHeaders(headers);

  try {
    const data = await request.request({
      method: 'GET',
      url: `${EXECUTIONS_BASE}/${encodeURIComponent(executionId)}/bundle`,
      responseType: 'arraybuffer',
      timeout: 120_000,
      headers,
      signal,
      transformRequest: [
        (body, reqHeaders) => {
          if (reqHeaders && typeof reqHeaders === 'object') {
            delete (reqHeaders as Record<string, unknown>)['Content-Type'];
          }
          return body;
        },
      ],
    });

    const buf = toArrayBuffer(data);
    if (!buf) {
      throw new MvpApiError(
        502,
        'SKILL_EXECUTION_FAILED',
        'bundle response is not binary',
        null,
      );
    }

    const jsonErr = tryDecodeJsonError(buf);
    if (jsonErr) {
      throw new MvpApiError(502, 'SKILL_EXECUTION_FAILED', jsonErr, null);
    }

    try {
      assertZipMagic(buf);
    } catch (error) {
      if (error instanceof BundleValidationError) {
        throw new MvpApiError(502, error.errorCode, error.message, null);
      }
      throw error;
    }
    return buf;
  } catch (error) {
    if (error instanceof MvpApiError) throw error;
    if (axios.isAxiosError(error)) {
      const status = error.response?.status ?? 0;
      const body = error.response?.data;
      const buf = toArrayBuffer(body);
      const msg =
        (buf && tryDecodeJsonError(buf)) ||
        error.message ||
        'bundle fetch failed';
      throw new MvpApiError(status, 'SKILL_EXECUTION_FAILED', msg, null);
    }
    throw new MvpApiError(
      0,
      'SKILL_EXECUTION_FAILED',
      error instanceof Error ? error.message : 'bundle fetch failed',
      null,
    );
  }
}

/**
 * POST BrowserSkillExecutionResult using the shared CSRF request layer.
 */
export async function postSkillExecutionResult(
  executionId: string,
  result: BrowserSkillExecutionResult,
  signal?: AbortSignal,
): Promise<void> {
  if (result.executionId !== executionId) {
    throw new MvpApiError(
      400,
      'VALIDATION_ERROR',
      'executionId mismatch',
      null,
    );
  }
  if (result.schemaVersion !== BROWSER_SKILL_EXECUTION_SCHEMA_VERSION) {
    throw new MvpApiError(
      400,
      'VALIDATION_ERROR',
      'invalid schemaVersion',
      null,
    );
  }
  await phase2Post<null>(
    `${EXECUTIONS_BASE}/${encodeURIComponent(executionId)}/result`,
    result,
    signal,
  );
}

export function buildFailureResult(
  executionId: string,
  errorCode: string,
  message: string,
): BrowserSkillExecutionResult {
  return {
    schemaVersion: BROWSER_SKILL_EXECUTION_SCHEMA_VERSION,
    executionId,
    success: false,
    outputJson: null,
    stdout: null,
    stderr: null,
    errorCode,
    message,
  };
}
