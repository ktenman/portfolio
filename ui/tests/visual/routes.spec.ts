import { test, expect } from '@playwright/test'
import { freeze, openRoute } from './settle'
import { type RouteStub } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubCalculator } from './calculator-fixture'
import { stubDiversification } from './diversification-fixture'
import { stubEnums } from './enums-fixture'
import { stubEtfBreakdown } from './etf-fixture'
import { stubInstruments } from './instruments-fixture'
import { stubMonitoring } from './monitoring-fixture'
import { stubPortfolioSummary } from './summary-fixture'
import { stubTransactions } from './transactions-fixture'

const MAX_CAPTURE_HEIGHT = 12000
const MIN_TARGET_PX = 24

const ROUTES: { path: string; name: string; stub: RouteStub }[] = [
  { path: '/', name: 'summary', stub: stubPortfolioSummary },
  { path: '/transactions', name: 'transactions', stub: stubTransactions },
  { path: '/instruments', name: 'instruments', stub: stubInstruments },
  { path: '/etf-breakdown', name: 'etf-breakdown', stub: stubEtfBreakdown },
  { path: '/diversification', name: 'diversification', stub: stubDiversification },
  { path: '/calculator', name: 'calculator', stub: stubCalculator },
  { path: '/monitoring', name: 'monitoring', stub: stubMonitoring },
]

const BREAKDOWN_ROUTES = ROUTES.filter(route =>
  ['etf-breakdown', 'diversification'].includes(route.name)
)

test.beforeEach(async ({ page }) => {
  await stubBuildInfo(page)
  await stubEnums(page)
})

for (const route of ROUTES) {
  test(`route ${route.name}`, async ({ page }) => {
    await route.stub(page)
    await openRoute(page, route.path)
    await freeze(page)
    const width = await page.evaluate(() => document.documentElement.scrollWidth)
    await expect(page).toHaveScreenshot(`route-${route.name}.png`, {
      fullPage: true,
      clip: { x: 0, y: 0, width, height: MAX_CAPTURE_HEIGHT },
      mask: [page.locator('canvas')],
    })
  })
}

for (const route of BREAKDOWN_ROUTES) {
  test(`${route.name} lays out every breakdown control without squeezing it`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await route.stub(page)
    await openRoute(page, route.path)
    const layout = await page.evaluate(minTarget => {
      const labels = Array.from(document.querySelectorAll('.breakdown-tab, .compare-toggle'))
      const buttons = Array.from(document.querySelectorAll('.view-btn'))
      const squeezed = [
        ...labels
          .filter(label => label.scrollWidth > label.clientWidth)
          .map(label => label.textContent?.trim()),
        ...buttons
          .filter(button => button.getBoundingClientRect().width < minTarget)
          .map(button => button.getAttribute('aria-label')),
      ]
      return { controls: labels.length + buttons.length, squeezed }
    }, MIN_TARGET_PX)
    expect(layout).toEqual({ controls: 7, squeezed: [] })
  })
}
