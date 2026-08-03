import { expect, test } from '@playwright/test';
import {
  clickSend,
  createConversationFromSidebar,
  loginAs,
} from './helpers/auth';

/**
 * Real E2E against Fake acceptance stack.
 * Skip unless MVP_E2E_READY=1 — do not pretend coverage with unconditional skip.
 *
 * Fake mode is process-level (MVP_FAKE_AGENT_MODE). run_all recreates backend
 * per mode before this spec — do not invent browser-side failure simulation.
 */
const e2eReady = process.env.MVP_E2E_READY === '1';
const fakeMode = process.env.MVP_FAKE_AGENT_MODE || 'SUCCESS';

test.describe('stream-failure', () => {
  test.skip(!e2eReady, 'Set MVP_E2E_READY=1 with mvp-acceptance Fake stack');

  test(`fake mode ${fakeMode} ends without permanent loading`, async ({page,}) => {
    await loginAs(page, 'user-a');
    await expect(page).toHaveURL(/\/app/);

    await createConversationFromSidebar(page);
    await expect(page).toHaveURL(/\/app\/chat\//);

    const composer = page.getByPlaceholder(/Genie|希望|输入|消息|ask|message/i).or(
      page.getByRole('textbox').last(),
    );
    // Query text is ordinary; failure comes from Fake Agent mode, not magic strings.
    await composer.fill(`E2E stream probe under ${fakeMode}`);
    await clickSend(page);

    await expect(
      page
        .getByText(/失败|中断|格式错误|连接已断开|error|interrupted|手动刷新/i)
        .first(),
    ).toBeVisible({ timeout: 90_000 });
    await expect(page.locator('#chat-view').getByText(/加载中|loading/i)).toHaveCount(
      0,
      { timeout: 30_000 },
    );
  });
});
