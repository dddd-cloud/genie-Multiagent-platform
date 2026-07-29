import type { CsrfTokenResponse, LoginRequest, UserResponse } from '@/contracts';
import { requestMvp } from '@/services/mvp';
import { setCsrf } from './csrf';

export async function fetchCsrf(): Promise<CsrfTokenResponse> {
  const data = await requestMvp<CsrfTokenResponse>(
    { method: 'GET', url: '/api/v1/auth/csrf' },
    { skipCsrf: true },
  );
  if (!data) {
    throw new Error('Empty CSRF response');
  }
  setCsrf({ headerName: data.headerName, token: data.token });
  return data;
}

export async function login(body: LoginRequest): Promise<UserResponse | null> {
  return requestMvp<UserResponse>({
    method: 'POST',
    url: '/api/v1/auth/login',
    data: body,
  });
}

export async function logout(): Promise<null> {
  return requestMvp<null>({
    method: 'POST',
    url: '/api/v1/auth/logout',
  });
}

export async function fetchMe(): Promise<UserResponse | null> {
  return requestMvp<UserResponse>(
    { method: 'GET', url: '/api/v1/users/me' },
    { skipCsrf: true },
  );
}
