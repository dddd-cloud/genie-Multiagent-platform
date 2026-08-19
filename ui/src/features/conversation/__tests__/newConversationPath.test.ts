import { describe, expect, it } from 'vitest';
import {
  isChatSurfacePath,
  isNewConversationPath,
} from '../newConversationPath';

describe('isNewConversationPath', () => {
  it('treats /app as the unsaved composer', () => {
    expect(isNewConversationPath('/app')).toBe(true);
    expect(isNewConversationPath('/app/')).toBe(true);
  });

  it('does not treat other app routes as the composer', () => {
    expect(isNewConversationPath('/app/chat/abc')).toBe(false);
    expect(isNewConversationPath('/app/settings')).toBe(false);
    expect(isNewConversationPath('/app/teams')).toBe(false);
  });
});

describe('isChatSurfacePath', () => {
  it('covers composer and an open thread', () => {
    expect(isChatSurfacePath('/app')).toBe(true);
    expect(isChatSurfacePath('/app/chat/abc')).toBe(true);
  });

  it('does not cover other app routes', () => {
    expect(isChatSurfacePath('/app/settings')).toBe(false);
    expect(isChatSurfacePath('/app/teams')).toBe(false);
    expect(isChatSurfacePath('/app/chat')).toBe(false);
  });
});
