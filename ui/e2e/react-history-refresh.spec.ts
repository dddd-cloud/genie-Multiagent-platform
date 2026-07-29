import { expect, test } from '@playwright/test';
import {
  clickSend,
  createConversationFromSidebar,
  loginAs,
} from './helpers/auth';

/**
 * Real E2E against Fake acceptance stack.
 * Skip unless MVP_E2E_READY=1 — do not pretend coverage with unconditional skip.
 */
const e2eReady = process.env.MVP_E2E_READY === '1';

test.describe('react-history-refresh', () => {
  test.skip(!e2eReady, 'Set MVP_E2E_READY=1 with mvp-acceptance Fake stack');

  test('refresh restores ReAct history from snapshot', async ({ page }) => {
    await loginAs(page, 'user-a');
    await expect(page).toHaveURL(/\/app/);

    await createConversationFromSidebar(page);
    await expect(page).toHaveURL(/\/app\/chat\//);

    const composer = page.getByPlaceholder(/Genie|希望|输入|消息|ask|message/i).or(
      page.getByRole('textbox').last(),
    );
    await composer.fill('E2E ReAct history probe');
    await clickSend(page);
    await expect(page.getByText('E2E ReAct history probe').first()).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.locator('#chat-view').getByText(/加载中|loading/i)).toHaveCount(
      0,
      { timeout: 90_000 },
    );

    await page.reload();
    await expect(page).toHaveURL(/\/app\/chat\//);
    await expect(page.getByText('E2E ReAct history probe').first()).toBeVisible();
    await expect(page.locator('#chat-view').getByText(/加载中|loading/i)).toHaveCount(0);
  });
});
