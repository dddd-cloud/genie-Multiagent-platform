export const EXECUTION_MODE_PREFERENCES = ['AUTO', 'DIRECT', 'ORCHESTRATED'] as const;
export type ExecutionModePreference = (typeof EXECUTION_MODE_PREFERENCES)[number];

export const STREAM_RENDER_MODES = ['BATCHED', 'INSTANT'] as const;
export type StreamRenderMode = (typeof STREAM_RENDER_MODES)[number];

export const UI_LOCALES = ['zh-CN', 'en-US'] as const;
export type UiLocale = (typeof UI_LOCALES)[number];

/** Mirrors the server whitelist in UserSettingService; every key is always present in a response. */
export interface UserPreferences {
  defaultExecutionMode: ExecutionModePreference;
  defaultDeepThink: boolean;
  defaultOutputStyle: string;
  preferredModelName: string;
  streamRenderMode: StreamRenderMode;
  sidebarCollapsed: boolean;
  locale: UiLocale;
}

/** A null value resets that key back to its server-side default. */
export type UserPreferencesPatch = {
  [K in keyof UserPreferences]?: UserPreferences[K] | null;
};

export interface UserSettingsResponse {
  settings: Record<string, unknown>;
}
