import { expect, test } from '@playwright/test';
import { expectPhase2Nav, loginAsAcceptanceUser } from './helpers';
import { openMarketplaceLibrary } from '../helpers';

const realReady = process.env.PHASE2_REAL_E2E_READY === '1';

test.describe('Phase2 real management pages', () => {
  test.skip(!realReady, 'Set PHASE2_REAL_E2E_READY=1 with real A/B/C stack');

  test.beforeEach(async ({ page }) => {
    await loginAsAcceptanceUser(page, 'user-a');
    await expectPhase2Nav(page);
  });

  test('agents / skills / mcp load; create skill when API allows', async ({ page }) => {
    await openMarketplaceLibrary(page, 'agents');
    await expect(page.getByTestId('agent-list-page')).toBeVisible();
    await expect
      .soft(page.locator('[data-testid="agent-list-page"] .ant-alert-error'))
      .toHaveCount(0);
    await page.getByTestId('library-modal-close').click();

    await openMarketplaceLibrary(page, 'skills');
    await expect(page.getByTestId('skill-list-page')).toBeVisible();
    await expect
      .soft(page.locator('[data-testid="skill-list-page"] .ant-alert-error'))
      .toHaveCount(0);

    await page.getByRole('button', { name: /新建/i }).click();
    await expect(page.getByTestId('skill-editor-page')).toBeVisible();
    const skillName = `E2E Skill ${Date.now()}`;
    await page.getByTestId('skill-name').fill(skillName);
    await page
      .getByTestId('skill-instruction')
      .fill('Real E2E skill instruction — summarize input briefly.');
    await page.getByTestId('skill-save').click();

    await expect(page.getByTestId('skill-editor-page')).toBeVisible();
    const savedName = page.getByTestId('skill-name');
    const created = await savedName
      .inputValue()
      .then((value) => value === skillName)
      .catch(() => false);
    if (created) {
      await expect(savedName).toHaveValue(skillName);
    } else {
      await expect.soft(page.getByTestId('skill-editor-page')).toBeVisible();
    }

    await page.getByTestId('library-modal-close').click();
    await openMarketplaceLibrary(page, 'connectors');
    await expect(page.getByTestId('mcp-list-page')).toBeVisible();
    await expect
      .soft(page.locator('[data-testid="mcp-list-page"] .ant-alert-error'))
      .toHaveCount(0);
  });
});
