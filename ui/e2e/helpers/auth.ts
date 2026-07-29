import type { Page } from '@playwright/test';

/**
 * Acceptance passwords come from env (never hardcode production secrets).
 * Default matches MVP-CONTRACT-002 acceptance-only default for local Fake runs.
 */
function acceptancePassword(): string {
  return process.env.MVP_ACCEPTANCE_USER_PASSWORD || 'MvpTest-Only-123';
}

export async function loginAs(
  page: Page,
  username: string,
  password = acceptancePassword(),
): Promise<void> {
  await page.goto('/login');
  await page.getByLabel(/用户名|username/i).fill(username);
  await page.getByLabel(/密码|password/i).fill(password);
  await page.getByRole('button', { name: /登录|login/i }).click();
  await page.waitForURL(/\/app/);
}

/** @deprecated use loginAs */
export async function login(
  page: Page,
  username = 'user-a',
  password = acceptancePassword(),
): Promise<void> {
  await loginAs(page, username, password);
}

export async function createConversationFromSidebar(page: Page): Promise<void> {
  // Sidebar label is「新会话」(ConversationSidebar) — match real UI, not wishful aliases.
  await page.getByRole('button', { name: /新会话|新建|新对话|new/i }).click();
  await page.waitForURL(/\/app\/chat\//);
}

/** Click the composer send control (accessible button aria-label=发送). */
export async function clickSend(page: Page): Promise<void> {
  await page.getByRole('button', { name: /发送|send/i }).click();
}
