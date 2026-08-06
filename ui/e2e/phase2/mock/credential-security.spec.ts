import { expect, test } from '@playwright/test';
import { loginAsMock } from '../helpers';

test.describe('Phase2 MCP credential security (mock)', () => {
  test('credential not in URL or localStorage after save', async ({ page }) => {
    const secret = `e2e-secret-${Date.now()}`;
    await loginAsMock(page, 'user-a');

    await page.getByTestId('phase2-navigation').getByRole('link', { name: 'MCP' }).click();
    await page.getByRole('button', { name: /新建 MCP/i }).click();
    await expect(page.getByTestId('mcp-editor-page')).toBeVisible();

    await page.getByTestId('mcp-name').fill('Secure MCP');
    await page.getByTestId('mcp-server-url').fill('https://mcp.example.com/secure');
    await page.getByTestId('mcp-auth-type').getByText('BEARER_TOKEN').click();
    await page.getByTestId('mcp-credential').fill(secret);
    await page.getByTestId('mcp-save').click();

    await expect(page).toHaveURL(/\/app\/mcp\/mcp-/, { timeout: 15_000 });
    expect(page.url()).not.toContain(secret);

    const storageBlob = await page.evaluate(() => {
      const out: string[] = [];
      for (let i = 0; i < localStorage.length; i += 1) {
        const key = localStorage.key(i);
        if (key) out.push(`${key}=${localStorage.getItem(key) ?? ''}`);
      }
      for (let i = 0; i < sessionStorage.length; i += 1) {
        const key = sessionStorage.key(i);
        if (key) out.push(`${key}=${sessionStorage.getItem(key) ?? ''}`);
      }
      return out.join('\n');
    });
    expect(storageBlob).not.toContain(secret);

    await expect(page.getByTestId('mcp-credential-configured')).toBeVisible();
    await expect(page.getByTestId('mcp-credential')).toHaveValue('');
  });
});
