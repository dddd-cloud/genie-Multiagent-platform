import { execSync } from 'node:child_process';
import path from 'node:path';
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
 * Plan §15.4: SLOW_STREAM → send → Loading → docker restart backend →
 * INTERRUPTED/SERVICE_RESTARTED → Session still valid → no permanent Loading.
 */
const e2eReady = process.env.MVP_E2E_READY === '1';
const composeFile = path.resolve(
  __dirname,
  '../../deploy/docker-compose.mvp.yml',
);

function restartBackend(): void {
  execSync(`docker compose -f "${composeFile}" restart genie-backend`, {
    stdio: 'inherit',
    timeout: 120_000,
  });
}

async function waitBackendHealth(page: import('@playwright/test').Page): Promise<void> {
  // Backend :8080 is not published to the host (plan §14.7) — probe via UI same-origin proxy.
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    try {
      const res = await page.request.get('/web/health', { timeout: 5_000 });
      if (res.ok()) {
        return;
      }
    } catch {
      // keep polling
    }
    await page.waitForTimeout(2_000);
  }
  throw new Error('genie-backend /web/health did not recover after restart (via UI proxy)');
}

test.describe('service-restart', () => {
  test.skip(!e2eReady, 'Set MVP_E2E_READY=1 with mvp-acceptance Fake stack');

  test('backend restart during stream → INTERRUPTED, session kept', async ({
    page,
  }) => {
    test.setTimeout(300_000);

    await loginAs(page, 'user-a');
    await expect(page).toHaveURL(/\/app/);
    await expect(page.getByText(/当前用户|user-a/i).first()).toBeVisible();

    await createConversationFromSidebar(page);
    await expect(page).toHaveURL(/\/app\/chat\//);

    const composer = page.getByPlaceholder(/Genie|希望|输入|消息|ask|message/i).or(
      page.getByRole('textbox').last(),
    );
    await composer.fill('E2E slow stream during restart');
    await clickSend(page);

    // Enter loading / streaming UI before killing the JVM.
    await expect(
      page.getByText(/已接收到你的任务|正在执行|任务进行中/i).first(),
    ).toBeVisible({ timeout: 30_000 });

    restartBackend();
    await waitBackendHealth(page);

    // History / page should settle to interrupted terminal — not permanent loading.
    await expect(
      page.getByText(/已中断|SERVICE_RESTARTED|服务重启|连接已断开|手动刷新/i).first(),
    ).toBeVisible({ timeout: 120_000 });

    await page.reload();
    // Session cookie must still be valid after Java restart (Spring Session JDBC).
    await expect(page).toHaveURL(/\/app\/chat\//);
    await expect(page.getByText(/当前用户|user-a/i).first()).toBeVisible();
    await expect(page.locator('#chat-view').getByText(/加载中|loading/i)).toHaveCount(0);
    await expect(
      page.getByText(/已中断|SERVICE_RESTARTED|服务重启|可重新发送/i).first(),
    ).toBeVisible();
  });
});
