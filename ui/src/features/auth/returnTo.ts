/**
 * Safe post-login redirect: only `/app` and `/app/...` (not `/apple`).
 */
export function isSafeAppReturnTo(path: string): boolean {
  if (!path.startsWith('/')) {
    return false;
  }
  if (path.startsWith('//')) {
    return false;
  }
  return path === '/app' || path.startsWith('/app/') || path.startsWith('/app?');
}

export function resolveReturnTo(raw: string | null | undefined): string {
  if (raw && isSafeAppReturnTo(raw)) {
    return raw;
  }
  return '/app';
}

/** Build `/login` or `/login?returnTo=...` when current location is under `/app`. */
export function loginPathWithReturnTo(pathname: string, search = ''): string {
  if (pathname === '/app' || pathname.startsWith('/app/')) {
    const returnTo = `${pathname}${search}`;
    return `/login?returnTo=${encodeURIComponent(returnTo)}`;
  }
  return '/login';
}
