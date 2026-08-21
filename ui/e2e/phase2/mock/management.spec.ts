import { expect, test } from '@playwright/test';
import { loginAsMock, openMarketplaceLibrary } from '../helpers';

test.describe('Phase2 management pages (mock)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsMock(page, 'user-a');
  });

  test('navigate agents / skills / mcp and create agent', async ({ page }) => {
    await openMarketplaceLibrary(page, 'agents');
    await expect(page.getByTestId('agent-list-page')).toBeVisible();
    await expect(page.getByText('Research Agent')).toBeVisible();

    await page.getByRole('button', { name: /^新建$/ }).click();
    await expect(page.getByTestId('agent-editor-page')).toBeVisible();
    await page.getByTestId('agent-name').fill('E2E Agent');
    await page.getByTestId('agent-description').fill('created by mock e2e');
    await page.getByTestId('agent-save').click();
    await expect(page.getByTestId('agent-name')).toHaveValue('E2E Agent');

    await page.getByTestId('library-modal-close').click();
    await openMarketplaceLibrary(page, 'skills');
    await expect(page.getByTestId('skill-list-page')).toBeVisible();

    await page.getByTestId('library-modal-close').click();
    await openMarketplaceLibrary(page, 'connectors');
    await expect(page.getByTestId('mcp-list-page')).toBeVisible();
    await expect(page.getByText('Docs MCP')).toBeVisible();
  });
});
