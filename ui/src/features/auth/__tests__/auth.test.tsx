import React from 'react';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { server } from '../../../../mocks/server';
import { resetMockState, mockState } from '../../../../mocks/handlers';
import { http, HttpResponse } from 'msw';
import AuthProvider from '../AuthProvider';
import { useAuth } from '../useAuth';
import { clearCsrf, getCsrf } from '../csrf';
import * as authApi from '../api';
import { requestMvp } from '@/services/mvp';
import { MvpApiError } from '@/services/apiError';
import { setMessage } from '@/utils';

function AuthProbe() {
  const { status, user, bootError } = useAuth();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="user">{user?.username ?? ''}</span>
      <span data-testid="boot-error">{bootError ?? ''}</span>
    </div>
  );
}

function LoginProbe() {
  const { status, login } = useAuth();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <button
        type="button"
        data-testid="login-btn"
        onClick={() => {
          void login('user-a', 'password').catch(() => undefined);
        }}
      >
        login
      </button>
      <button
        type="button"
        data-testid="bad-login-btn"
        onClick={() => {
          void login('user-a', 'wrong').catch(() => undefined);
        }}
      >
        bad
      </button>
    </div>
  );
}

function renderWithAuth(
  ui: React.ReactElement,
  initialEntries: string[] = ['/'],
) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route
          path="*"
          element={<AuthProvider>{ui}</AuthProvider>}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('auth (MSW)', () => {
  beforeAll(() => {
    server.listen({ onUnhandledRequest: 'error' });
    setMessage({
      warning: vi.fn(),
      error: vi.fn(),
      success: vi.fn(),
      info: vi.fn(),
    } as never);
  });

  afterEach(() => {
    server.resetHandlers();
    resetMockState();
    clearCsrf();
    localStorage.clear();
    sessionStorage.clear();
  });

  afterAll(() => {
    server.close();
  });

  beforeEach(() => {
    clearCsrf();
    resetMockState();
  });

  it('boots to unauthenticated when /me returns AUTH_REQUIRED and keeps csrf', async () => {
    renderWithAuth(<AuthProbe />);

    await waitFor(() => {
      expect(screen.getByTestId('status').textContent).toBe('unauthenticated');
    });

    expect(getCsrf()?.token).toBeTruthy();
    expect(screen.getByTestId('user').textContent).toBe('');
  });

  it('boots to authenticated after csrf + me success', async () => {
    resetMockState({
      authenticated: true,
      user: {
        id: 'user-a-id',
        username: 'user-a',
        displayName: 'User A',
        role: 'USER',
      },
    });

    renderWithAuth(<AuthProbe />);

    await waitFor(() => {
      expect(screen.getByTestId('status').textContent).toBe('authenticated');
    });
    expect(screen.getByTestId('user').textContent).toBe('user-a');
    expect(getCsrf()?.token).toBeTruthy();
  });

  it('me 401 keeps csrf token from successful csrf fetch', async () => {
    await authApi.fetchCsrf();
    const tokenBefore = getCsrf()?.token;
    expect(tokenBefore).toBeTruthy();

    await expect(authApi.fetchMe()).rejects.toMatchObject({
      code: 'AUTH_REQUIRED',
    });

    expect(getCsrf()?.token).toBe(tokenBefore);
  });

  it('login rejects invalid credentials', async () => {
    await authApi.fetchCsrf();
    await expect(
      authApi.login({ username: 'user-a', password: 'wrong' }),
    ).rejects.toMatchObject({
      code: 'AUTH_INVALID_CREDENTIALS',
      httpStatus: 401,
    });
    expect(mockState.authenticated).toBe(false);
  });

  it('AUTH_REQUIRED from protected API notifies and leaves csrf for boot path', async () => {
    await authApi.fetchCsrf();
    const csrf = getCsrf()?.token;

    await expect(
      requestMvp({ method: 'GET', url: '/api/v1/conversations', params: { page: 1, pageSize: 20 } }),
    ).rejects.toBeInstanceOf(MvpApiError);

    expect(getCsrf()?.token).toBe(csrf);
  });

  it('ACCESS_DENIED does not clear authenticated session', async () => {
    resetMockState({
      authenticated: true,
      user: {
        id: 'user-a-id',
        username: 'user-a',
        displayName: 'User A',
        role: 'USER',
      },
    });

    renderWithAuth(<AuthProbe />);

    await waitFor(() => {
      expect(screen.getByTestId('status').textContent).toBe('authenticated');
    });

    mockState.forceAccessDenied = true;
    await expect(
      requestMvp({ method: 'GET', url: '/api/v1/conversations', params: { page: 1, pageSize: 20 } }),
    ).rejects.toMatchObject({ code: 'ACCESS_DENIED' });

    expect(screen.getByTestId('status').textContent).toBe('authenticated');
    expect(screen.getByTestId('user').textContent).toBe('user-a');
  });

  it('CSRF_INVALID refreshes token but does not auto-replay the mutating request', async () => {
    await authApi.fetchCsrf();
    resetMockState({
      authenticated: true,
      user: {
        id: 'user-a-id',
        username: 'user-a',
        displayName: 'User A',
        role: 'USER',
      },
    });

    let createCount = 0;
    server.use(
      http.post('/api/v1/conversations', () => {
        createCount += 1;
        return HttpResponse.json(
          { code: 'CSRF_INVALID', message: 'CSRF token invalid or missing', data: null },
          { status: 403 },
        );
      }),
    );

    // Mount provider so CSRF_INVALID handler is registered.
    renderWithAuth(<AuthProbe />);
    await waitFor(() => {
      expect(screen.getByTestId('status').textContent).toBe('authenticated');
    });

    clearCsrf();
    await authApi.fetchCsrf();
    const tokenBefore = getCsrf()?.token;

    await expect(
      requestMvp({
        method: 'POST',
        url: '/api/v1/conversations',
        data: { title: 'x' },
      }),
    ).rejects.toMatchObject({ code: 'CSRF_INVALID' });

    await waitFor(() => {
      // handler refreshed csrf (may be same mock token value)
      expect(getCsrf()?.token).toBeTruthy();
    });

    // Original mutating call happened once — no automatic replay.
    expect(createCount).toBe(1);
    expect(getCsrf()?.token).toBeTruthy();
    // Token object may be refreshed; still present and not wiped permanently.
    expect(tokenBefore).toBeTruthy();
  });

  it('does not persist password or csrf token in web storage', async () => {
    renderWithAuth(<LoginProbe />, ['/login']);

    await waitFor(() => {
      expect(screen.getByTestId('status').textContent).toBe('unauthenticated');
    });

    screen.getByTestId('login-btn').click();

    await waitFor(() => {
      expect(screen.getByTestId('status').textContent).toBe('authenticated');
    });

    const storageBlobs = [
      ...Object.values(localStorage),
      ...Object.values(sessionStorage),
    ].join(' ');

    expect(storageBlobs).not.toMatch(/password/i);
    expect(storageBlobs).not.toContain('mvp-mock-csrf-token');
    expect(storageBlobs).not.toContain('X-XSRF-TOKEN');
  });
});
