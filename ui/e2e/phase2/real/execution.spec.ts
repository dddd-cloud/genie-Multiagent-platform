import { expect, test } from '@playwright/test';
import {
  clickSend,
  createConversationFromSidebar,
  expectPhase2Nav,
  fillComposer,
  loginAsAcceptanceUser,
  selectExecutionMode,
  SSE_TIMEOUT_MS,
  waitForStreamSettlement,
} from './helpers';

const realReady = process.env.PHASE2_REAL_E2E_READY === '1';

test.describe('Phase2 real execution', () => {
  test.skip(!realReady, 'Set PHASE2_REAL_E2E_READY=1 with real A/B/C stack');

  for (const mode of ['DIRECT', 'AUTO'] as const) {
    test(`${mode}: create conversation, send message, settle or clear error`, async ({page}) => {
      await loginAsAcceptanceUser(page, 'user-a');
      await expectPhase2Nav(page);

      await createConversationFromSidebar(page);
      await expect(page.getByTestId('new-conversation')).toBeVisible();

      await selectExecutionMode(page, mode);

      const probe = `Phase2 real E2E ${mode} probe ${Date.now()}`;
      await fillComposer(page, probe);
      await clickSend(page);
      await expect(page).toHaveURL(/\/app\/chat\//, { timeout: 30_000 });

      await expect(page.getByText(probe).first()).toBeVisible({ timeout: 30_000 });

      await waitForStreamSettlement(page);

      // Must not remain stuck in loading after SSE window.
      await expect(
        page.locator('#chat-view').getByText(/加载中|loading/i),
      ).toHaveCount(0, { timeout: SSE_TIMEOUT_MS });
    });
  }
});
