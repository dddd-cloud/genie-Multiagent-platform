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

export async function openSettingsSection(
  page: Page,
  name: '本地记忆' | '模型' | '偏好' | '账户与用量',
): Promise<void> {
  await page.getByTestId('sidebar-user-menu').click();
  await page.getByTestId('settings-menu-item').click();
  await page.getByTestId('settings-nav').waitFor();
  await page.getByTestId('settings-nav').getByRole('link', { name }).click();
}

export async function openMarketplaceLibrary(
  page: Page,
  kind: 'agents' | 'teams' | 'skills' | 'connectors',
): Promise<void> {
  await page.getByTestId('app-navigation').getByRole('link', { name: '资源广场' }).click();
  const tabLabel = {
    agents: '智能体',
    teams: '智能体团队',
    skills: '技能',
    connectors: '连接器',
  }[kind];
  await page.getByTestId('marketplace-tabs').getByText(tabLabel, { exact: true }).click();
  const buttonId = {
    agents: 'marketplace-my-agents',
    teams: 'marketplace-my-teams',
    skills: 'marketplace-my-skills',
    connectors: 'marketplace-my-connectors',
  }[kind];
  await page.getByTestId(buttonId).click();
}

export async function logoutMock(page: Page): Promise<void> {
  await page.getByTestId('sidebar-user-menu').click();
  await page.getByTestId('logout-button').click();
  await page.waitForURL(/\/login/);
}

export async function createConversationFromSidebar(page: Page): Promise<void> {
  await page.getByRole('button', { name: /新会话|新建|新对话|new/i }).click();
  await page.waitForURL((url) => {
    const pathname = new URL(url).pathname;
    return pathname === '/app' || pathname === '/app/';
  });
}

export async function clickSend(page: Page): Promise<void> {
  await page.getByRole('button', { name: /发送|send/i }).click();
}

export async function fillComposer(page: Page, text: string): Promise<void> {
  const box = page.locator('textarea').last();
  await box.fill(text);
}

export async function startPersistedConversation(
  page: Page,
  text = 'E2E persist conversation',
): Promise<void> {
  await createConversationFromSidebar(page);
  await fillComposer(page, text);
  await clickSend(page);
  await page.waitForURL(/\/app\/chat\//);
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
  const option = page.getByRole('option', {
    name: EXECUTION_MODE_LABELS[mode],
  });
  if (!(await option.isVisible().catch(() => false))) {
    await trigger.click();
  }
  await option.click();
}
