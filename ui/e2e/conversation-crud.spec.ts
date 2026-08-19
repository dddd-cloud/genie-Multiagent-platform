import { expect, test } from '@playwright/test';
import { startPersistedConversation, loginAs } from './helpers/auth';

/**
 * Real E2E against Fake acceptance stack.
 * Skip unless MVP_E2E_READY=1 — do not pretend coverage with unconditional skip.
 */
const e2eReady = process.env.MVP_E2E_READY === '1';

test.describe('conversation-crud', () => {
  test.skip(!e2eReady, 'Set MVP_E2E_READY=1 with mvp-acceptance Fake stack');

  test('create list rename delete conversation', async ({ page }) => {
    await loginAs(page, 'user-a');
    await expect(page).toHaveURL(/\/app/);

    await startPersistedConversation(page);
    await expect(page).toHaveURL(/\/app\/chat\//);
    const conversationUrl = page.url();

    // Rename/delete are opacity-0 until row hover (ConversationSidebar).
    await page.locator('aside .group').first().hover();
    await page.getByRole('button', { name: /重命名|rename/i }).first().click();
    const renameInput = page.getByRole('textbox').last();
    await renameInput.fill('E2E renamed title');
    await page.getByRole('button', { name: /确定|ok|保存|save/i }).click();
    await expect(page.getByText('E2E renamed title').first()).toBeVisible();

    await page
      .locator('aside .group')
      .filter({ hasText: 'E2E renamed title' })
      .first()
      .hover();
    await page.getByRole('button', { name: /删除|delete/i }).first().click();
    await page.getByRole('button', { name: /删除|delete|确定|ok/i }).last().click();
    // Deleting the open chat returns to the unsaved composer at /app.
    await expect(page).toHaveURL(/\/app$/);
    await expect(page).not.toHaveURL(conversationUrl);
    await expect(page.getByText('E2E renamed title')).toHaveCount(0);
  });
});
