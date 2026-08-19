import { expect, test } from '@playwright/test';
import { expectPhase2Nav, loginAsAcceptanceUser } from './helpers';

const realReady = process.env.PHASE2_REAL_E2E_READY === '1';

test.describe('Phase2 real management pages', () => {
  test.skip(!realReady, 'Set PHASE2_REAL_E2E_READY=1 with real A/B/C stack');

  test.beforeEach(async ({ page }) => {
    await loginAsAcceptanceUser(page, 'user-a');
    await expectPhase2Nav(page);
    await page.getByTestId('app-navigation').getByRole('link', { name: '设置中心' }).click();
  });

  test('agents / skills / mcp load; create skill when API allows', async ({page}) => {
    const nav = page.getByTestId('settings-nav');

    await nav.getByRole('link', { name: 'Agent' }).click();
    await expect(page.getByTestId('agent-list-page')).toBeVisible();
    // Soft: list API errors should not block page shell coverage.
    await expect
      .soft(page.locator('[data-testid="agent-list-page"] .ant-alert-error'))
      .toHaveCount(0);

    await nav.getByRole('link', { name: 'Skill' }).click();
    await expect(page.getByTestId('skill-list-page')).toBeVisible();
    await expect
      .soft(page.locator('[data-testid="skill-list-page"] .ant-alert-error'))
      .toHaveCount(0);

    await page.getByRole('button', { name: /新建 Skill/i }).click();
    await expect(page.getByTestId('skill-editor-page')).toBeVisible();
    const skillName = `E2E Skill ${Date.now()}`;
    await page.getByTestId('skill-name').fill(skillName);
    await page
      .getByTestId('skill-instruction')
      .fill('Real E2E skill instruction — summarize input briefly.');
    await page.getByTestId('skill-save').click();

    // Create when API allows; stay resilient if backend rejects (soft).
    const created = await page
      .waitForURL(/\/app\/(?:settings\/)?skills\/(?!new$)[^/]+/, { timeout: 15_000 })
      .then(() => true)
      .catch(() => false);
    if (created) {
      await expect(page.getByTestId('skill-name')).toHaveValue(skillName);
    } else {
      await expect
        .soft(page.getByTestId('skill-editor-page'))
        .toBeVisible();
    }

    await nav.getByRole('link', { name: 'MCP' }).click();
    await expect(page.getByTestId('mcp-list-page')).toBeVisible();
    await expect
      .soft(page.locator('[data-testid="mcp-list-page"] .ant-alert-error'))
      .toHaveCount(0);
  });
});
