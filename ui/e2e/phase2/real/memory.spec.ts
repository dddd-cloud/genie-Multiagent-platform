import { expect, test } from '@playwright/test';
import { expectPhase2Nav, loginAsAcceptanceUser } from './helpers';

const realReady = process.env.PHASE2_REAL_E2E_READY === '1';

test.describe('Phase2 real local memory', () => {
  test.skip(!realReady, 'Set PHASE2_REAL_E2E_READY=1 with real A/B/C stack');

  test('memory settings shows human copy and hides disk status by default', async ({page}) => {
    await loginAsAcceptanceUser(page, 'user-a');
    await expectPhase2Nav(page);

    await page
      .getByTestId('phase2-navigation')
      .getByRole('link', { name: '本地记忆' })
      .click();

    await expect(page.getByRole('heading', { name: '记忆' })).toBeVisible();
    await expect(page.getByText(/磁盘状态：/)).toHaveCount(0);
    await page.getByTestId('memory-advanced-toggle').click();
    // Real stack userId is account-scoped (not mock user-a-id).
    await expect(page.getByTestId('memory-account-scope')).toHaveText(
      /当前 userId 作用域：\S+/,
    );
  });
});
