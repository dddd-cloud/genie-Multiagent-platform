import { describe, expect, it } from 'vitest';
import {
  DEFAULT_USER_PREFERENCES,
  normalizeUserPreferences,
} from '@/services/settings';
import {
  registerUserScopedReset,
  resetUserScopedState,
} from '../userScopedReset';

describe('normalizeUserPreferences', () => {
  it('falls back to defaults when the payload is missing or not an object', () => {
    expect(normalizeUserPreferences(undefined)).toEqual(
      DEFAULT_USER_PREFERENCES,
    );
    expect(normalizeUserPreferences(null)).toEqual(DEFAULT_USER_PREFERENCES);
    expect(normalizeUserPreferences('nope')).toEqual(DEFAULT_USER_PREFERENCES);
    expect(normalizeUserPreferences({})).toEqual(DEFAULT_USER_PREFERENCES);
  });

  it('keeps recognised values and drops unknown keys', () => {
    const normalized = normalizeUserPreferences({
      defaultExecutionMode: 'ORCHESTRATED',
      defaultDeepThink: true,
      defaultOutputStyle: 'ppt',
      sidebarCollapsed: true,
      somethingAddedByANewerBackend: 'ignored',
    });

    expect(normalized.defaultExecutionMode).toBe('ORCHESTRATED');
    expect(normalized.defaultDeepThink).toBe(true);
    expect(normalized.defaultOutputStyle).toBe('ppt');
    expect(normalized.sidebarCollapsed).toBe(true);
    expect(Object.keys(normalized)).not.toContain(
      'somethingAddedByANewerBackend',
    );
  });

  it('rejects out-of-range enums and mistyped values instead of trusting them', () => {
    const normalized = normalizeUserPreferences({
      defaultExecutionMode: 'TURBO',
      defaultDeepThink: 'yes',
      defaultOutputStyle: 42,
      sidebarCollapsed: 1,
    });

    expect(normalized.defaultExecutionMode).toBe(
      DEFAULT_USER_PREFERENCES.defaultExecutionMode,
    );
    expect(normalized.defaultDeepThink).toBe(
      DEFAULT_USER_PREFERENCES.defaultDeepThink,
    );
    expect(normalized.defaultOutputStyle).toBe(
      DEFAULT_USER_PREFERENCES.defaultOutputStyle,
    );
    expect(normalized.sidebarCollapsed).toBe(
      DEFAULT_USER_PREFERENCES.sidebarCollapsed,
    );
  });
});

describe('resetUserScopedState', () => {
  it('runs every registered listener so no user-scoped cache survives a logout', () => {
    const calls: string[] = [];
    const unregisterA = registerUserScopedReset(() => calls.push('a'));
    const unregisterB = registerUserScopedReset(() => calls.push('b'));

    resetUserScopedState();
    expect(calls).toEqual(['a', 'b']);

    unregisterA();
    resetUserScopedState();
    expect(calls).toEqual(['a', 'b', 'b']);

    unregisterB();
  });

  it('keeps clearing the remaining listeners when one of them throws', () => {
    const calls: string[] = [];
    const unregisterA = registerUserScopedReset(() => {
      throw new Error('boom');
    });
    const unregisterB = registerUserScopedReset(() => calls.push('b'));

    expect(() => resetUserScopedState()).not.toThrow();
    expect(calls).toEqual(['b']);

    unregisterA();
    unregisterB();
  });
});
