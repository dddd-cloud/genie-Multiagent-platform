import axios from 'axios';
import request from '@/utils/request';
import { LegacyApiError } from './apiError';

export { LegacyApiError } from './apiError';

interface LegacyBody<T = unknown> {
  code: number;
  data: T;
  message?: string;
  msg?: string;
}

async function unwrapLegacy<T>(promise: Promise<unknown>): Promise<T> {
  try {
    const body = (await promise) as LegacyBody<T>;
    if (body && typeof body === 'object' && Number(body.code) === 200) {
      return body.data;
    }
    throw new LegacyApiError(
      200,
      typeof body?.code === 'number' ? body.code : -1,
      body?.msg || body?.message || '请求失败',
      body?.data ?? null,
    );
  } catch (error) {
    if (error instanceof LegacyApiError) {
      throw error;
    }
    if (axios.isAxiosError(error)) {
      const status = error.response?.status ?? 0;
      const resData = error.response?.data as LegacyBody | undefined;
      throw new LegacyApiError(
        status,
        typeof resData?.code === 'number' ? resData.code : status,
        resData?.msg || resData?.message || error.message || '请求失败',
        resData?.data ?? null,
      );
    }
    throw error;
  }
}

/** Legacy `/web` / `/data` APIs — unpack `code === 200` envelopes. */
export const api = {
  get: <T>(url: string, params?: unknown) =>
    unwrapLegacy<T>(request.get(url, { params })),

  post: <T>(url: string, data?: unknown) =>
    unwrapLegacy<T>(request.post(url, data)),

  put: <T>(url: string, data?: unknown) =>
    unwrapLegacy<T>(request.put(url, data)),

  delete: <T>(url: string, params?: unknown) =>
    unwrapLegacy<T>(request.delete(url, { params })),
};

export default api;
