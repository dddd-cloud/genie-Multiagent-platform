import { expect, test } from '@playwright/test';
import { createConversationFromSidebar, loginAs } from './helpers/auth';

/**
 * Real E2E against Fake acceptance stack.
 * Skip unless MVP_E2E_READY=1 — do not pretend coverage with unconditional skip.
 */
const e2eReady = process.env.MVP_E2E_READY === '1';

test.describe('isolation', () => {
  test.skip(!e2eReady, 'Set MVP_E2E_READY=1 with mvp-acceptance Fake stack');

  test('user B cannot open user A conversation — 404 → /app', async ({browser,}) => {
    // Plan §15.5: two independent BrowserContexts (not clearCookies on one page).
    const contextA = await browser.newContext();
    const contextB = await browser.newContext();
    const pageA = await contextA.newPage();
    const pageB = await contextB.newPage();

    await loginAs(pageA, 'user-a');
    await expect(pageA).toHaveURL(/\/app/);
    await createConversationFromSidebar(pageA);
    await expect(pageA).toHaveURL(/\/app\/chat\//);
    const ownerConversationUrl = pageA.url();
    const conversationId = ownerConversationUrl.match(/\/app\/chat\/([^/?#]+)/)?.[1];
    expect(conversationId).toBeTruthy();

    await loginAs(pageB, 'user-b');
    await expect(pageB).toHaveURL(/\/app/);

    // Plan §8.6 / §15.5: 404 RESOURCE_NOT_FOUND → navigate /app; list must not show it.
    await pageB.goto(ownerConversationUrl);
    await expect(pageB).toHaveURL(/\/app$/, { timeout: 15_000 });
    await expect(pageB).not.toHaveURL(/\/app\/chat\//);
    if (conversationId) {
      await expect(pageB.getByText(conversationId)).toHaveCount(0);
    }

    await contextA.close();
    await contextB.close();
  });
});
