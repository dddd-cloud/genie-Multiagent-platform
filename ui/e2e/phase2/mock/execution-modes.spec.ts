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
    await expect(selector.getByText('AUTO', { exact: true })).toBeVisible();
    await expect(selector.getByText('DIRECT', { exact: true })).toBeVisible();
    await expect(
      selector.getByText('ORCHESTRATED', { exact: true }),
    ).toBeVisible();
  });
});
