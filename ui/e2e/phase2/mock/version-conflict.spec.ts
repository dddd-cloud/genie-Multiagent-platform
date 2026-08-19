import { expect, test } from '@playwright/test';
import { loginAsMock, openSettingsSection, setPhase2TestFlags } from '../helpers';

test.describe('Phase2 version conflict UI (mock)', () => {
  test('forceVersionConflict shows reload alert on agent save', async ({page}) => {
    await loginAsMock(page, 'user-a');
    await openSettingsSection(page, 'Agent');
    await page.getByRole('button', { name: '编辑' }).first().click();
    await expect(page.getByTestId('agent-editor-page')).toBeVisible();

    await setPhase2TestFlags(page, { forceVersionConflict: true });
    await page.getByTestId('agent-description').fill('conflict edit');
    await page.getByTestId('agent-save').click();

    await expect(page.getByTestId('version-conflict-alert')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('version-conflict-reload')).toBeVisible();

    await page.getByTestId('version-conflict-reload').click();
    await expect(page.getByTestId('version-conflict-alert')).toHaveCount(0, { timeout: 10_000 });
  });
});
