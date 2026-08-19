import { expect, type Page } from '@playwright/test';
import {
  clickSend,
  createConversationFromSidebar,
  loginAs,
  startPersistedConversation,
} from '../../helpers/auth';

/** Real stack uses acceptance passwords — never mock `password`. */
export { clickSend, createConversationFromSidebar, loginAs, startPersistedConversation };

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
    .getByLabel('消息')
    .or(page.locator('#chat-view textarea').last());
  await box.fill(text);
}

const EXECUTION_MODE_LABELS: Record<
  'AUTO' | 'DIRECT' | 'ORCHESTRATED',
  string
> = {
  AUTO: 'Auto',
  DIRECT: 'Solo',
  ORCHESTRATED: 'Ensemble',
};

export async function selectExecutionMode(
  page: Page,
  mode: 'AUTO' | 'DIRECT' | 'ORCHESTRATED',
): Promise<void> {
  const trigger = page.getByTestId('execution-mode-selector');
  await expect(trigger).toBeVisible({ timeout: 15_000 });
  const option = page.getByRole('option', {
    name: EXECUTION_MODE_LABELS[mode],
  });
  if (!(await option.isVisible().catch(() => false))) {
    await trigger.click();
  }
  await option.click();
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
