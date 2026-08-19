import { expect, test } from '@playwright/test';
import { loginAsMock } from '../helpers';

test.describe('Marketplace and generation mounts (mock)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsMock(page, 'user-a');
  });

  test('opens the marketplace page from app navigation', async ({ page }) => {
    await page.getByTestId('app-navigation').getByRole('link', { name: '资源广场' }).click();
    await expect(page).toHaveURL(/\/app\/marketplace/);
    await expect(page.getByTestId('marketplace-page')).toBeVisible();
  });

  test('opens the generation page from app navigation', async ({ page }) => {
    await page.getByTestId('app-navigation').getByRole('link', { name: '一句话生成' }).click();
    await expect(page).toHaveURL(/\/app\/generate/);
    await expect(page.getByTestId('generation-page')).toBeVisible();
  });
});
