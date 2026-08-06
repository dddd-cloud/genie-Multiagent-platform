import { expect, test } from '@playwright/test';
import { loginAsMock } from '../helpers';

test.describe('Phase2 local memory settings (mock)', () => {
  test('memory settings page loads and shows OPFS status', async ({ page }) => {
    await loginAsMock(page, 'user-a');
    await page
      .getByTestId('phase2-navigation')
      .getByRole('link', { name: '本地记忆' })
      .click();

    await expect(page.getByRole('heading', { name: '本地记忆' })).toBeVisible();
    await expect(page.getByText(/OPFS 状态：/)).toBeVisible();
    await expect(page.getByText(/当前 userId 作用域：user-a-id/)).toBeVisible();
  });
});
