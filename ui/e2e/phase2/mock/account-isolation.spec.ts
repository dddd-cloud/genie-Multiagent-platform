import { expect, test } from '@playwright/test';
import {
  createConversationFromSidebar,
  loginAsMock,
  logoutMock,
} from '../helpers';

test.describe('Phase2 account isolation (mock)', () => {
  test('switching user-a → user-b scopes memory and hides prior session', async ({page}) => {
    await loginAsMock(page, 'user-a');
    await createConversationFromSidebar(page);
    const convUrl = page.url();
    expect(convUrl).toMatch(/\/app\/chat\//);

    await page
      .getByTestId('phase2-navigation')
      .getByRole('link', { name: '本地记忆' })
      .click();
    await expect(page.getByText(/当前 userId 作用域：user-a-id/)).toBeVisible();

    await logoutMock(page);
    await loginAsMock(page, 'user-b');
    await expect(page.getByText(/User B|user-b/i).first()).toBeVisible();

    await page
      .getByTestId('phase2-navigation')
      .getByRole('link', { name: '本地记忆' })
      .click();
    await expect(page.getByText(/当前 userId 作用域：user-b-id/)).toBeVisible();

    // Prior conversation is not owned by user-b → 404 → redirect home.
    await page.goto(convUrl);
    await expect(page).toHaveURL(/\/app\/?$/, {timeout: 15_000});
  });
});
