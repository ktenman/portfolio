import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './ui/tests/visual',
  snapshotPathTemplate: 'docs/superpowers/baseline/{arg}-{projectName}{ext}',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90000,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:61234',
    screenshot: 'off',
    reducedMotion: 'reduce',
  },
  webServer: {
    command: 'npm run dev:ui',
    url: 'http://localhost:61234',
    reuseExistingServer: true,
    timeout: 120000,
  },
  expect: {
    timeout: 30000,
    toHaveScreenshot: {
      maxDiffPixels: 0,
      animations: 'disabled',
      caret: 'hide',
      scale: 'css',
    },
  },
  projects: [
    {
      name: 'mobile',
      testIgnore: '**/holdings-search.spec.ts',
      use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 } },
    },
    {
      name: 'tablet',
      testIgnore: '**/holdings-search.spec.ts',
      use: { ...devices['Desktop Chrome'], viewport: { width: 768, height: 1024 } },
    },
    {
      name: 'desktop',
      testIgnore: '**/holdings-search.spec.ts',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    ...[
      { name: 'holdings-mobile', viewport: { width: 390, height: 844 } },
      { name: 'holdings-desktop', viewport: { width: 1440, height: 900 } },
    ].map(({ name, viewport }) => ({
      name,
      testMatch: '**/holdings-search.spec.ts',
      snapshotPathTemplate: 'ui/tests/visual/baselines/{arg}-{projectName}{ext}',
      use: { ...devices['Desktop Chrome'], viewport },
    })),
  ],
})
