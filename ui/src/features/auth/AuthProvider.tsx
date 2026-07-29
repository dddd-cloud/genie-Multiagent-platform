import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { UserResponse } from '@/contracts';
import { MvpApiError } from '@/services/apiError';
import { showMessage } from '@/utils';
import { abortAllActiveSse } from '@/utils/querySSE';
import * as authApi from './api';
import { clearCsrf, getCsrf } from './csrf';
import { setMvpErrorHandler } from './mvpErrorBus';
import { loginPathWithReturnTo } from './returnTo';
import { AuthContext, type AuthStatus } from './useAuth';

type AuthProviderProps = {
  children: React.ReactNode;
};

const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [status, setStatus] = useState<AuthStatus>('booting');
  const [user, setUser] = useState<UserResponse | null>(null);
  const [bootError, setBootError] = useState<string | null>(null);
  const [bootNonce, setBootNonce] = useState(0);
  const bootingRef = useRef(true);
  const authEpochRef = useRef(0);

  const refreshAnonymousCsrf = useCallback(async () => {
    clearCsrf();
    try {
      await authApi.fetchCsrf();
    } catch {
      showMessage()?.warning('获取 CSRF 失败，请刷新页面后重试');
    }
  }, []);

  const handleAuthRequired = useCallback(async () => {
    authEpochRef.current += 1;
    // Plan §7.5 / §11.10: cancel in-flight SSE before navigating away.
    abortAllActiveSse();
    setUser(null);
    setStatus('unauthenticated');
    setBootError(null);

    if (bootingRef.current) {
      // Boot path: KEEP csrf (already fetched).
      return;
    }

    await refreshAnonymousCsrf();
    if (!location.pathname.startsWith('/login')) {
      navigate(loginPathWithReturnTo(location.pathname, location.search), {replace: true,});
    }
  }, [location.pathname, location.search, navigate, refreshAnonymousCsrf]);

  const handleCsrfInvalid = useCallback(async () => {
    clearCsrf();
    try {
      await authApi.fetchCsrf();
      showMessage()?.warning('CSRF 校验失败，请重试当前操作');
    } catch {
      showMessage()?.warning('CSRF 刷新失败，请刷新页面后重试');
    }
  }, []);

  useEffect(() => {
    setMvpErrorHandler((error) => {
      // Plan §7.5: only HTTP 401 + AUTH_REQUIRED means session expired.
      if (error.code === 'AUTH_REQUIRED' && error.httpStatus === 401) {
        void handleAuthRequired();
        return;
      }
      if (error.code === 'CSRF_INVALID') {
        void handleCsrfInvalid();
        return;
      }
      // ACCESS_DENIED: stay authenticated; page/caller shows 无权限.
    });
    return () => setMvpErrorHandler(undefined);
  }, [handleAuthRequired, handleCsrfInvalid]);

  useEffect(() => {
    let cancelled = false;
    const epoch = authEpochRef.current;

    const boot = async () => {
      bootingRef.current = true;
      setStatus('booting');
      setBootError(null);

      try {
        await authApi.fetchCsrf();
        if (cancelled || epoch !== authEpochRef.current) return;

        const me = await authApi.fetchMe();
        if (cancelled || epoch !== authEpochRef.current) return;

        if (!me) {
          throw new Error('Empty /users/me response');
        }

        setUser(me);
        setStatus('authenticated');
      } catch (error) {
        if (cancelled || epoch !== authEpochRef.current) return;

        if (error instanceof MvpApiError && error.code === 'AUTH_REQUIRED') {
          // KEEP csrf token from successful csrf fetch.
          setUser(null);
          setStatus('unauthenticated');
          return;
        }

        setBootError(error instanceof Error ? error.message : '启动失败');
        setStatus('booting');
      } finally {
        if (!cancelled) {
          bootingRef.current = false;
        }
      }
    };

    void boot();
    return () => {
      cancelled = true;
    };
  }, [bootNonce]);

  const login = useCallback(async (username: string, password: string) => {
    if (!getCsrf()) {
      await authApi.fetchCsrf();
    }
    await authApi.login({
      username,
      password
    });
    const me = await authApi.fetchMe();
    if (!me) {
      throw new Error('Login succeeded but /users/me returned empty');
    }
    setUser(me);
    setStatus('authenticated');
    setBootError(null);
  }, []);

  const logout = useCallback(async () => {
    abortAllActiveSse();
    try {
      if (!getCsrf()) {
        await authApi.fetchCsrf();
      }
      await authApi.logout();
    } catch {
      // Still clear local session even if logout request fails.
    }
    setUser(null);
    setStatus('unauthenticated');
    clearCsrf();
    await refreshAnonymousCsrf();
    navigate('/login', { replace: true });
  }, [navigate, refreshAnonymousCsrf]);

  const retryBoot = useCallback(() => {
    setBootNonce((n) => n + 1);
  }, []);

  const value = useMemo(
    () => ({
      status,
      user,
      bootError,
      login,
      logout,
      retryBoot,
    }),
    [status, user, bootError, login, logout, retryBoot],
  );

  if (status === 'booting' && bootError) {
    return (
      <AuthContext.Provider value={value}>
        <div className="flex h-full w-full flex-col items-center justify-center gap-16 bg-page p-24">
          <p className="text-[16px] text-text-secondary">{bootError}</p>
          <button
            type="button"
            className="rounded-md bg-brand px-16 py-8 text-white transition-colors duration-150 hover:bg-brand-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
            onClick={retryBoot}
          >
            重试
          </button>
        </div>
      </AuthContext.Provider>
    );
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

AuthProvider.displayName = 'AuthProvider';

export default AuthProvider;
