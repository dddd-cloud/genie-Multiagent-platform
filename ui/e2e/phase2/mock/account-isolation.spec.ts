import { expect, test } from '@playwright/test';
import {
  loginAsMock,
  logoutMock,
  openSettingsSection,
  startPersistedConversation,
} from '../helpers';

test.describe('Phase2 account isolation (mock)', () => {
  test('switching user-a → user-b scopes memory and hides prior session', async ({page}) => {
    await loginAsMock(page, 'user-a');
    await startPersistedConversation(page);
    const convUrl = page.url();
    expect(convUrl).toMatch(/\/app\/chat\//);

    await openSettingsSection(page, '本地记忆');
    await page.getByTestId('memory-advanced-toggle').click();
    await expect(page.getByTestId('memory-account-scope')).toHaveText(
      /当前 userId 作用域：user-a-id/,
    );

    await logoutMock(page);
    await loginAsMock(page, 'user-b');
    await expect(page.getByText(/User B|user-b/i).first()).toBeVisible();

    await openSettingsSection(page, '本地记忆');
    await page.getByTestId('memory-advanced-toggle').click();
    await expect(page.getByTestId('memory-account-scope')).toHaveText(
      /当前 userId 作用域：user-b-id/,
    );

    // Prior conversation is not owned by user-b → 404 → unsaved composer.
    await page.goto(convUrl);
    await expect(page).toHaveURL(/\/app\/?$/, {timeout: 15_000});
  });
});
