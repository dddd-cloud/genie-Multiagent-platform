import { expect, type Page } from '@playwright/test';
import {
  clickSend,
  createConversationFromSidebar,
  loginAs,
} from '../../helpers/auth';

/** Real stack uses acceptance passwords — never mock `password`. */
export { clickSend, createConversationFromSidebar, loginAs };

export const SSE_TIMEOUT_MS = 60_000;

export async function loginAsAcceptanceUser(
  page: Page,
  username = 'user-a',
): Promise<void> {
  await loginAs(page, username);
  await expect(page).toHaveURL(/\/app/);
}

/**
 * Phase2 nav requires VITE_PHASE2_ENABLED=true baked into the UI build.
 * When PHASE2_REAL_E2E_READY=1, missing nav is a hard FAIL with a clear message.
 */
export async function expectPhase2Nav(page: Page): Promise<void> {
  const nav = page.getByTestId('phase2-navigation');
  try {
    // Wait for post-login Suspense/layout (isVisible does not wait by default).
    await expect(nav).toBeVisible({ timeout: 30_000 });
  } catch {
    throw new Error(
      'Phase2 navigation missing. Rebuild UI with VITE_PHASE2_ENABLED=true ' +
        '(deploy stack) before running PHASE2_REAL_E2E_READY=1.',
    );
  }
}

export async function fillComposer(page: Page, text: string): Promise<void> {
  const box = page
    .getByPlaceholder(/Genie|希望|输入|消息|ask|message/i)
    .or(page.locator('textarea').last());
  await box.fill(text);
}

export async function selectExecutionMode(
  page: Page,
  mode: 'AUTO' | 'DIRECT' | 'ORCHESTRATED',
): Promise<void> {
  const group = page.getByTestId('execution-mode-selector');
  await expect(group).toBeVisible({ timeout: 15_000 });
  await group.getByText(mode, { exact: true }).click();
}

/**
 * Wait until streaming settles: loading gone, or a clear error/terminal signal.
 */
export async function waitForStreamSettlement(page: Page): Promise<void> {
  const chat = page.locator('#chat-view');
  await expect
    .poll(
      async () => {
        const loading = await chat.getByText(/加载中|loading/i).count();
        if (loading === 0) return 'idle';
        const err = await page
          .getByText(/失败|中断|格式错误|连接已断开|error|interrupted|无权限|请求失败/i)
          .count();
        if (err > 0) return 'error';
        return 'busy';
      },
      { timeout: SSE_TIMEOUT_MS },
    )
    .not.toBe('busy');
}
