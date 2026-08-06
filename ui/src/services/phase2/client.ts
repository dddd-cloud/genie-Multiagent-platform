import { requestMvp, MvpApiError } from '@/services';

function shouldRetryGet(error: unknown): boolean {
  if (error instanceof MvpApiError) {
    const status = error.httpStatus;
    return status === 0 || status >= 500;
  }
  return true;
}

export async function phase2Get<T>(
  url: string,
  params?: unknown,
  signal?: AbortSignal,
): Promise<T | null> {
  const config = {
    method: 'GET' as const,
    url,
    params,
    signal,
  };
  try {
    return await requestMvp<T>(config);
  } catch (error) {
    if (!shouldRetryGet(error)) {
      throw error;
    }
    return await requestMvp<T>(config);
  }
}

export function phase2Post<T>(
  url: string,
  body?: unknown,
  signal?: AbortSignal,
): Promise<T | null> {
  return requestMvp<T>({
    method: 'POST',
    url,
    data: body,
    signal,
  });
}

/** POST with query params (e.g. MCP enable/disable `?version=`). */
export function phase2PostWithParams<T>(
  url: string,
  params?: unknown,
  body?: unknown,
  signal?: AbortSignal,
): Promise<T | null> {
  return requestMvp<T>({
    method: 'POST',
    url,
    params,
    data: body,
    signal,
  });
}

export function phase2Put<T>(
  url: string,
  body?: unknown,
  signal?: AbortSignal,
): Promise<T | null> {
  return requestMvp<T>({
    method: 'PUT',
    url,
    data: body,
    signal,
  });
}

/** DELETE with JSON body (e.g. Agent/Skill version). */
export function phase2Delete<T>(
  url: string,
  bodyOrParams?: unknown,
  signal?: AbortSignal,
): Promise<T | null> {
  return requestMvp<T>({
    method: 'DELETE',
    url,
    data: bodyOrParams,
    signal,
  });
}

/** DELETE with query params (e.g. MCP `?version=`). */
export function phase2DeleteWithParams<T>(
  url: string,
  params?: unknown,
  signal?: AbortSignal,
): Promise<T | null> {
  return requestMvp<T>({
    method: 'DELETE',
    url,
    params,
    signal,
  });
}
