import axios, { type AxiosRequestConfig } from 'axios';
import type { ApiResponse } from '@/contracts';
import request from '@/utils/request';
import { MvpApiError } from './apiError';
import { applyCsrfHeaders } from '@/features/auth/csrf';
import { notifyMvpError } from '@/features/auth/mvpErrorBus';

export type RequestMvpOptions = {
  /** Skip attaching CSRF header (e.g. anonymous GET csrf). */
  skipCsrf?: boolean;
};

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof (value as ApiResponse<unknown>).code === 'string'
  );
}

function toMvpError(httpStatus: number, body: unknown, fallbackMessage: string): MvpApiError {
  if (isApiResponse(body)) {
    return new MvpApiError(
      httpStatus,
      body.code,
      body.message || fallbackMessage,
      body.data ?? null,
    );
  }
  return new MvpApiError(httpStatus, 'INTERNAL_ERROR', fallbackMessage, null);
}

/**
 * MVP contract client: HTTP 2xx + `code === "OK"` → `data` (may be null).
 * Otherwise throws `MvpApiError`. Uses the shared same-origin Axios instance.
 */
export async function requestMvp<T>(
  config: AxiosRequestConfig,
  options?: RequestMvpOptions,
): Promise<T | null> {
  const headers: Record<string, string> = {...(config.headers as Record<string, string> | undefined),};
  if (!options?.skipCsrf) {
    applyCsrfHeaders(headers);
  }

  try {
    // Success interceptor returns raw response.data
    const body = (await request.request({
      ...config,
      headers,
    })) as ApiResponse<T>;

    if (isApiResponse(body) && body.code === 'OK') {
      return body.data;
    }

    const error = toMvpError(200, body, 'Request failed');
    notifyMvpError(error);
    throw error;
  } catch (error) {
    if (error instanceof MvpApiError) {
      throw error;
    }

    if (axios.isAxiosError(error)) {
      const status = error.response?.status ?? 0;
      const mvpError = toMvpError(
        status,
        error.response?.data,
        error.message || 'Request failed',
      );
      notifyMvpError(mvpError);
      throw mvpError;
    }

    throw error;
  }
}

export { MvpApiError } from './apiError';
