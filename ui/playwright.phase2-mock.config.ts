import { defineConfig, devices } from '@playwright/test';

/**
 * Phase2 mock E2E against vite --mode mvp-mock (MSW + VITE_PHASE2_ENABLED).
 * Starts its own webServer unless one is already running (local reuse).
 */
export default defineConfig({
  testDir: './e2e/phase2/mock',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: 'list',
  timeout: 60_000,
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'pnpm dev:phase2-mock',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
