import { expect, test } from '@playwright/test';
import { loginAs } from '../helpers/auth';

test.describe('Marketplace', () => {
  test('renders the marketplace page after login', async ({ page }) => {
    await loginAs(page, 'user-a');
    await page.getByTestId('app-navigation').getByRole('link', { name: '资源广场' }).click();
    await expect(page).toHaveURL(/\/app\/marketplace/);
    await expect(page.getByTestId('marketplace-page')).toBeVisible();
  });
});
