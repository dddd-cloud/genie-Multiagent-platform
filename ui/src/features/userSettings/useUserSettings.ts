import { createContext, useContext } from 'react';
import type { UserPreferences, UserPreferencesPatch } from '@/contracts';
import { DEFAULT_USER_PREFERENCES } from '@/services/settings';

export type UserSettingsStatus = 'idle' | 'loading' | 'ready' | 'error';

export type UserSettingsContextValue = {
  preferences: UserPreferences;
  status: UserSettingsStatus;
  error: string | null;
  reload: () => Promise<void>;
  save: (patch: UserPreferencesPatch) => Promise<void>;
};

/**
 * Defaults are the fallback context so a component rendered outside the provider (or before the first
 * fetch resolves) still gets usable preferences instead of throwing.
 */
export const USER_SETTINGS_FALLBACK: UserSettingsContextValue = {
  preferences: DEFAULT_USER_PREFERENCES,
  status: 'idle',
  error: null,
  reload: async () => {},
  save: async () => {},
};

export const UserSettingsContext = createContext<UserSettingsContextValue>(
  USER_SETTINGS_FALLBACK,
);

export function useUserSettings(): UserSettingsContextValue {
  return useContext(UserSettingsContext);
}
