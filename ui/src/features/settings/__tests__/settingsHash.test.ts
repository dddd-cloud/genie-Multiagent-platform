import { describe, expect, it } from 'vitest';
import {
  canonicalizeSettingsPath,
  hashToSettingsPath,
  settingsPathToHash,
} from '../settingsHash';

describe('settings hash overlay', () => {
  it('opens the default pane with #settings', () => {
    expect(settingsPathToHash()).toBe('#settings');
    expect(settingsPathToHash('/app/settings/models')).toBe('#settings');
    expect(hashToSettingsPath('#settings')).toBe('/app/settings/models');
    expect(hashToSettingsPath('#settings/models')).toBe('/app/settings/models');
  });

  it('encodes nested settings pages in the hash', () => {
    expect(settingsPathToHash('/app/settings/agents')).toBe('#settings/agents');
    expect(settingsPathToHash('/app/settings/agents/new')).toBe(
      '#settings/agents/new',
    );
    expect(hashToSettingsPath('#settings/memory')).toBe('/app/settings/memory');
    expect(hashToSettingsPath('#settings/agents/abc')).toBe(
      '/app/settings/agents/abc',
    );
  });

  it('maps legacy resource paths into settings', () => {
    expect(canonicalizeSettingsPath('/app/agents')).toBe('/app/settings/agents');
    expect(canonicalizeSettingsPath('/app/skills/new')).toBe(
      '/app/settings/skills/new',
    );
    expect(canonicalizeSettingsPath('/app/mcp/mcp-a')).toBe(
      '/app/settings/mcp/mcp-a',
    );
    expect(settingsPathToHash('/app/agents/abc')).toBe('#settings/agents/abc');
  });

  it('ignores unrelated hashes', () => {
    expect(hashToSettingsPath('')).toBeNull();
    expect(hashToSettingsPath('#results')).toBeNull();
  });
});
