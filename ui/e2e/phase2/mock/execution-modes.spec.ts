import { expect, test } from '@playwright/test';
import {
  createConversationFromSidebar,
  loginAsMock,
} from '../helpers';

test.describe('Phase2 execution modes UI (mock)', () => {
  test('AUTO / DIRECT / ORCHESTRATED visible when phase2 enabled', async ({page}) => {
    await loginAsMock(page, 'user-a');
    await expect(page.getByTestId('phase2-navigation')).toBeVisible();

    await createConversationFromSidebar(page);
    const selector = page.getByTestId('execution-mode-selector');
    await expect(selector).toBeVisible();
    await expect(selector).toHaveText(/Auto/);
    await selector.click();
    await expect(page.getByRole('option', { name: 'Auto' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Solo' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Ensemble' })).toBeVisible();
  });
});
