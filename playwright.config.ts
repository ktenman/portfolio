import { defineConfig, devices } from '@playwright/test'

const SUMMARY_CAPTURE_TESTS =
  /route summary$|modal confirm$|selecting the sp500 mode|selecting both benchmarks|toast (success|error|info|warning)$/

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
      grepInvert: SUMMARY_CAPTURE_TESTS,
      testIgnore: ['**/holdings-search.spec.ts', '**/monthly-profit.spec.ts'],
      use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 } },
    },
    {
      name: 'tablet',
      grepInvert: SUMMARY_CAPTURE_TESTS,
      testIgnore: ['**/holdings-search.spec.ts', '**/monthly-profit.spec.ts'],
      use: { ...devices['Desktop Chrome'], viewport: { width: 768, height: 1024 } },
    },
    {
      name: 'desktop',
      grepInvert: SUMMARY_CAPTURE_TESTS,
      testIgnore: ['**/holdings-search.spec.ts', '**/monthly-profit.spec.ts'],
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    ...[
      { name: 'summary-mobile', viewport: { width: 390, height: 844 } },
      { name: 'summary-tablet', viewport: { width: 768, height: 1024 } },
      { name: 'summary-desktop', viewport: { width: 1440, height: 900 } },
    ].map(({ name, viewport }) => ({
      name,
      testMatch: ['**/routes.spec.ts', '**/states.spec.ts'],
      grep: SUMMARY_CAPTURE_TESTS,
      snapshotPathTemplate: 'ui/tests/visual/baselines/{arg}-{projectName}{ext}',
      use: { ...devices['Desktop Chrome'], viewport },
    })),
    ...[
      { name: 'monthly-mobile', viewport: { width: 390, height: 844 } },
      { name: 'monthly-tablet', viewport: { width: 768, height: 1024 } },
      { name: 'monthly-desktop', viewport: { width: 1440, height: 900 } },
    ].map(({ name, viewport }) => ({
      name,
      testMatch: '**/monthly-profit.spec.ts',
      snapshotPathTemplate: 'ui/tests/visual/baselines/{arg}-{projectName}{ext}',
      use: { ...devices['Desktop Chrome'], viewport },
    })),
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
