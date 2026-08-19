export const DEFAULT_SETTINGS_PATH = '/app/settings/models';

const SETTINGS_PREFIX = '/app/settings';
const ALIASES = ['agents', 'skills', 'mcp'] as const;

export function isTransientSettingsPath(pathname: string): boolean {
  if (pathname.startsWith(SETTINGS_PREFIX)) {
    return true;
  }
  return ALIASES.some(
    (ns) => pathname === `/app/${ns}` || pathname.startsWith(`/app/${ns}/`),
  );
}

export function canonicalizeSettingsPath(path?: string): string {
  if (!path) {
    return DEFAULT_SETTINGS_PATH;
  }
  const pathname = path.split('?')[0]?.replace(/\/+$/, '') || path;
  if (pathname === SETTINGS_PREFIX || pathname === `${SETTINGS_PREFIX}/`) {
    return DEFAULT_SETTINGS_PATH;
  }
  if (pathname.startsWith(`${SETTINGS_PREFIX}/`)) {
    return pathname;
  }
  for (const ns of ALIASES) {
    const prefix = `/app/${ns}`;
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
      return `${SETTINGS_PREFIX}/${pathname.slice('/app/'.length)}`;
    }
  }
  return DEFAULT_SETTINGS_PATH;
}

export function settingsPathToHash(path?: string): string {
  const canonical = canonicalizeSettingsPath(path);
  if (canonical === DEFAULT_SETTINGS_PATH) {
    return '#settings';
  }
  return `#settings/${canonical.slice(`${SETTINGS_PREFIX}/`.length)}`;
}

export function hashToSettingsPath(hash: string): string | null {
  const raw = hash.replace(/^#/, '').replace(/\/+$/, '');
  if (raw !== 'settings' && !raw.startsWith('settings/')) {
    return null;
  }
  if (raw === 'settings') {
    return DEFAULT_SETTINGS_PATH;
  }
  return canonicalizeSettingsPath(
    `${SETTINGS_PREFIX}/${raw.slice('settings/'.length)}`,
  );
}

export function splitPathSearch(full: string): {
  pathname: string;
  search: string;
} {
  const q = full.indexOf('?');
  if (q === -1) {
    return { pathname: full || '/app', search: '' };
  }
  return { pathname: full.slice(0, q) || '/app', search: full.slice(q) };
}
