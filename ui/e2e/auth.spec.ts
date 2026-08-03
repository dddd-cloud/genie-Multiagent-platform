import { expect, test } from '@playwright/test';
import { loginAs } from './helpers/auth';

/**
 * Real E2E against Fake acceptance stack.
 * Skip unless MVP_E2E_READY=1 — do not pretend coverage with unconditional skip.
 */
const e2eReady = process.env.MVP_E2E_READY === '1';

test.describe('auth', () => {
  test.skip(!e2eReady, 'Set MVP_E2E_READY=1 with mvp-acceptance Fake stack');

  test('login then refresh keeps session', async ({ page }) => {
    await loginAs(page, 'user-a');
    await expect(page).toHaveURL(/\/app/);
    await page.reload();
    await expect(page).toHaveURL(/\/app/);
    await expect(page.getByText(/当前用户|User A|user-a/i).first()).toBeVisible();
  });

  test('unauthenticated /app redirects to login with returnTo', async ({page,}) => {
    await page.goto('/app');
    await expect(page).toHaveURL(/\/login/);
    expect(page.url()).toContain('returnTo');
  });
});
