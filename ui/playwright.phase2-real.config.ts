import { defineConfig, devices } from '@playwright/test';

/**
 * Phase2 real E2E against an external acceptance stack (UI :3000).
 * No mvp-mock webServer — expects VITE_PHASE2_ENABLED=true built into the UI
 * and backend A/B/C already running. Gate with PHASE2_REAL_E2E_READY=1.
 */
export default defineConfig({
  testDir: './e2e/phase2/real',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: 'list',
  timeout: 90_000,
  expect: { timeout: 60_000 },
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
