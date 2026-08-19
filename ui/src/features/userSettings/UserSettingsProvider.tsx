import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { UserPreferences, UserPreferencesPatch } from '@/contracts';
import { useAuth } from '@/features/auth/useAuth';
import { MvpApiError } from '@/services/apiError';
import {
  DEFAULT_USER_PREFERENCES,
  fetchMyPreferences,
  updateMyPreferences,
} from '@/services/settings';
import {
  UserSettingsContext,
  type UserSettingsContextValue,
  type UserSettingsStatus,
} from './useUserSettings';
import { registerUserScopedReset } from './userScopedReset';

type UserSettingsProviderProps = {
  children: React.ReactNode;
};

const UserSettingsProvider: React.FC<UserSettingsProviderProps> = ({children,}) => {
  /** Re-fetches and re-scopes whenever the signed-in user changes. */
  const userId = useAuth().user?.id ?? null;
  const [preferences, setPreferences] = useState<UserPreferences>(
    DEFAULT_USER_PREFERENCES,
  );
  const [status, setStatus] = useState<UserSettingsStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const userIdRef = useRef(userId);
  userIdRef.current = userId;

  const load = useCallback(async (signal?: AbortSignal) => {
    if (!userIdRef.current) {
      setPreferences(DEFAULT_USER_PREFERENCES);
      setStatus('idle');
      setError(null);
      return;
    }
    setStatus('loading');
    try {
      const next = await fetchMyPreferences(signal);
      if (signal?.aborted) {
        return;
      }
      setPreferences(next);
      setStatus('ready');
      setError(null);
    } catch (err: unknown) {
      if (signal?.aborted) {
        return;
      }
      // Preferences are a convenience: the app keeps working on defaults when they cannot be read.
      setPreferences(DEFAULT_USER_PREFERENCES);
      setStatus('error');
      setError(err instanceof MvpApiError ? err.message : '读取设置失败');
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load, userId]);

  useEffect(
    () =>
      registerUserScopedReset(() => {
        setPreferences(DEFAULT_USER_PREFERENCES);
        setStatus('idle');
        setError(null);
      }),
    [],
  );

  const reload = useCallback(async () => {
    await load();
  }, [load]);

  const save = useCallback(async (patch: UserPreferencesPatch) => {
    const next = await updateMyPreferences(patch);
    setPreferences(next);
    setStatus('ready');
    setError(null);
  }, []);

  const value = useMemo<UserSettingsContextValue>(
    () => ({
      preferences,
      status,
      error,
      reload,
      save,
    }),
    [error, preferences, reload, save, status],
  );

  return (
    <UserSettingsContext.Provider value={value}>
      {children}
    </UserSettingsContext.Provider>
  );
};

UserSettingsProvider.displayName = 'UserSettingsProvider';

export default UserSettingsProvider;
