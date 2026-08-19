import type {
  ExecutionModePreference,
  StreamRenderMode,
  UiLocale,
  UsageRangeQuery,
  UsageSummaryResponse,
  UserPreferences,
  UserPreferencesPatch,
  UserSettingsResponse,
} from '@/contracts';
import {
  EXECUTION_MODE_PREFERENCES,
  STREAM_RENDER_MODES,
  UI_LOCALES,
} from '@/contracts';
import { requestMvp } from './mvp';

const SETTINGS_URL = '/api/v1/me/settings';
const MY_USAGE_URL = '/api/v1/me/usage/summary';

export const DEFAULT_USER_PREFERENCES: UserPreferences = {
  defaultExecutionMode: 'AUTO',
  defaultDeepThink: false,
  defaultOutputStyle: '',
  preferredModelName: '',
  streamRenderMode: 'BATCHED',
  sidebarCollapsed: false,
  locale: 'zh-CN',
};

function pickEnum<T extends string>(
  raw: unknown,
  allowed: readonly T[],
  fallback: T,
): T {
  return typeof raw === 'string' && (allowed as readonly string[]).includes(raw)
    ? (raw as T)
    : fallback;
}

function pickBoolean(raw: unknown, fallback: boolean): boolean {
  return typeof raw === 'boolean' ? raw : fallback;
}

function pickString(raw: unknown, fallback: string): string {
  return typeof raw === 'string' ? raw : fallback;
}

/**
 * The server sends an open map, so a key added by a newer backend (or dropped by an older one) must
 * never break this screen: anything unrecognised falls back to the default.
 */
export function normalizeUserPreferences(raw: unknown): UserPreferences {
  const settings =
    raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {};
  return {
    defaultExecutionMode: pickEnum<ExecutionModePreference>(
      settings.defaultExecutionMode,
      EXECUTION_MODE_PREFERENCES,
      DEFAULT_USER_PREFERENCES.defaultExecutionMode,
    ),
    defaultDeepThink: pickBoolean(
      settings.defaultDeepThink,
      DEFAULT_USER_PREFERENCES.defaultDeepThink,
    ),
    defaultOutputStyle: pickString(
      settings.defaultOutputStyle,
      DEFAULT_USER_PREFERENCES.defaultOutputStyle,
    ),
    preferredModelName: pickString(
      settings.preferredModelName,
      DEFAULT_USER_PREFERENCES.preferredModelName,
    ),
    streamRenderMode: pickEnum<StreamRenderMode>(
      settings.streamRenderMode,
      STREAM_RENDER_MODES,
      DEFAULT_USER_PREFERENCES.streamRenderMode,
    ),
    sidebarCollapsed: pickBoolean(
      settings.sidebarCollapsed,
      DEFAULT_USER_PREFERENCES.sidebarCollapsed,
    ),
    locale: pickEnum<UiLocale>(
      settings.locale,
      UI_LOCALES,
      DEFAULT_USER_PREFERENCES.locale,
    ),
  };
}

export async function fetchMyPreferences(
  signal?: AbortSignal,
): Promise<UserPreferences> {
  const data = await requestMvp<UserSettingsResponse>({
    method: 'GET',
    url: SETTINGS_URL,
    signal,
  });
  return normalizeUserPreferences(data?.settings);
}

export async function updateMyPreferences(
  patch: UserPreferencesPatch,
  signal?: AbortSignal,
): Promise<UserPreferences> {
  const data = await requestMvp<UserSettingsResponse>({
    method: 'PUT',
    url: SETTINGS_URL,
    data: { settings: patch },
    signal,
  });
  return normalizeUserPreferences(data?.settings);
}

export function fetchMyUsageSummary(
  range?: UsageRangeQuery,
  signal?: AbortSignal,
): Promise<UsageSummaryResponse | null> {
  return requestMvp<UsageSummaryResponse>({
    method: 'GET',
    url: MY_USAGE_URL,
    params: range,
    signal,
  });
}
