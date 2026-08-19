import { expect, test } from '@playwright/test';
import { loginAs } from '../helpers/auth';

test.describe('Generation', () => {
  test('renders the generation page after login', async ({ page }) => {
    await loginAs(page, 'user-a');
    await page.getByTestId('app-navigation').getByRole('link', { name: '一句话生成' }).click();
    await expect(page).toHaveURL(/\/app\/generate/);
    await expect(page.getByTestId('generation-page')).toBeVisible();
  });
});
