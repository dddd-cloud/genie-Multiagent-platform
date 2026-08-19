export function isNewConversationPath(pathname: string): boolean {
  return pathname === '/app' || pathname === '/app/';
}

/** Composer (`/app`) plus an open thread (`/app/chat/:id`). */
export function isChatSurfacePath(pathname: string): boolean {
  if (isNewConversationPath(pathname)) {
    return true;
  }
  return /^\/app\/chat\/[^/]+\/?$/.test(pathname);
}

/** Matched by `/app` and `/app/chat/:id`; ConversationLayout renders the page. */
export function ChatSurfaceSlot() {
  return null;
}
