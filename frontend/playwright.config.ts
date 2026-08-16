import { defineConfig, devices } from '@playwright/test';

// Mobile-first acceptance testing (Principle IV) — mobile viewport is the primary project;
// tablet/desktop are progressive-enhancement checks, matching plan.md's Target Platform.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  reporter: 'html',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'mobile-chromium',
      use: { ...devices['Pixel 7'] },
    },
    {
      name: 'mobile-webkit',
      use: { ...devices['iPhone 14'] },
    },
    {
      name: 'tablet-chromium',
      use: { ...devices['iPad Mini landscape'] },
    },
    {
      name: 'desktop-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
