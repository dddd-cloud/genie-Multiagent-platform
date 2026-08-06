import type { Page } from '@playwright/test';

/** Mock login password (mvp-mock MSW — NOT acceptance password). */
export const MOCK_PASSWORD = 'password';

export async function loginAsMock(
  page: Page,
  username: 'user-a' | 'user-b' = 'user-a',
  password = MOCK_PASSWORD,
): Promise<void> {
  await page.goto('/login');
  await page.getByLabel(/用户名|username/i).fill(username);
  await page.getByLabel(/密码|password/i).fill(password);
  await page.getByRole('button', { name: /登录|login/i }).click();
  await page.waitForURL(/\/app/);
}

export async function logoutMock(page: Page): Promise<void> {
  await page.getByRole('button', { name: /退出|logout/i }).click();
  await page.waitForURL(/\/login/);
}

export async function createConversationFromSidebar(page: Page): Promise<void> {
  await page.getByRole('button', { name: /新会话|新建|新对话|new/i }).click();
  await page.waitForURL(/\/app\/chat\//);
}

export async function clickSend(page: Page): Promise<void> {
  await page.getByRole('button', { name: /发送|send/i }).click();
}

export async function fillComposer(page: Page, text: string): Promise<void> {
  const box = page.locator('textarea').last();
  await box.fill(text);
}

export async function setPhase2SseScenario(
  page: Page,
  scenario:
    | 'direct-success'
    | 'direct-failure'
    | 'orchestrated-success'
    | 'orchestrated-replan'
    | 'orchestrated-summary-fallback',
): Promise<void> {
  const res = await page.request.post('/api/v2/_test/sse-scenario', { data: { scenario } });
  if (!res.ok()) {
    throw new Error(`Failed to set SSE scenario: ${res.status()} ${await res.text()}`);
  }
}

export async function setPhase2TestFlags(
  page: Page,
  flags: {
    forceVersionConflict?: boolean;
    forceSkillInUse?: boolean;
    forceMcpError?: boolean;
  },
): Promise<void> {
  const res = await page.request.post('/api/v2/_test/flags', { data: flags });
  if (!res.ok()) {
    throw new Error(`Failed to set test flags: ${res.status()} ${await res.text()}`);
  }
}

export async function selectExecutionMode(
  page: Page,
  mode: 'AUTO' | 'DIRECT' | 'ORCHESTRATED',
): Promise<void> {
  const group = page.getByTestId('execution-mode-selector');
  await group.getByText(mode, { exact: true }).click();
}
