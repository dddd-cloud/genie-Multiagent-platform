import { expect, test } from '@playwright/test';
import { expectPhase2Nav, loginAsAcceptanceUser } from './helpers';
import { openSettingsSection } from '../helpers';

/**
 * Real Phase2 smoke E2E against acceptance stack.
 * Skip unless PHASE2_REAL_E2E_READY=1 — never fake PASS with mock.
 * Shell gate (real_e2e.sh) BLOCKED(2) when READY!=1 before Playwright runs.
 */
const realReady = process.env.PHASE2_REAL_E2E_READY === '1';

test.describe('Phase2 real E2E smoke', () => {
  test.skip(!realReady, 'Set PHASE2_REAL_E2E_READY=1 with real A/B/C stack');

  test('login → Phase2 nav visible when enabled', async ({ page }) => {
    await loginAsAcceptanceUser(page, 'user-a');
    await expectPhase2Nav(page);
    await expect(page.getByTestId('phase2-navigation')).toBeVisible();
    await expect(page.getByTestId('app-navigation').getByRole('link', { name: '资源广场' })).toBeVisible();
    await openSettingsSection(page, '本地记忆');
    await expect(
      page.getByTestId('settings-nav').getByRole('link', { name: '本地记忆' }),
    ).toBeVisible();
    await expect(
      page.getByTestId('settings-nav').getByRole('link', { name: '智能体' }),
    ).toHaveCount(0);
  });
});
