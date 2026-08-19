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
  // Prefer role/label; fall back to submit when CJK fonts are missing in CI images.
  const userInput = page
    .getByLabel(/用户名|username/i)
    .or(page.locator('input#username, input[name="username"]').first())
    .or(page.locator('input').nth(0));
  const passInput = page
    .getByLabel(/密码|password/i)
    .or(page.locator('input#password, input[name="password"], input[type="password"]').first());
  await userInput.fill(username);
  await passInput.fill(password);
  const submit = page
    .getByRole('button', { name: /登录|login/i })
    .or(page.locator('button[type="submit"]'))
    .or(page.locator('.ant-btn-primary'));
  await submit.first().click();
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
  // Opens the unsaved composer at /app. A conversation is created only after send.
  await page.getByRole('button', { name: /新会话|新建|新对话|new/i }).click();
  await page.waitForURL((url) => {
    const pathname = new URL(url).pathname;
    return pathname === '/app' || pathname === '/app/';
  });
}

/** Click the composer send control (accessible button aria-label=发送). */
export async function clickSend(page: Page): Promise<void> {
  await page.getByRole('button', { name: /发送|send/i }).click();
}

export async function startPersistedConversation(
  page: Page,
  text = 'E2E persist conversation',
): Promise<void> {
  await createConversationFromSidebar(page);
  const composer = page
    .getByLabel('消息')
    .or(page.locator('#chat-view textarea').last());
  await composer.fill(text);
  await clickSend(page);
  await page.waitForURL(/\/app\/chat\//);
}
