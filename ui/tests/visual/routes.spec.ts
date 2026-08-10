import { test, expect } from '@playwright/test'
import { freeze, openRoute } from './settle'
import { type RouteStub } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubCalculator } from './calculator-fixture'
import { stubDiversification } from './diversification-fixture'
import { stubEtfBreakdown } from './etf-fixture'
import { stubInstruments } from './instruments-fixture'
import { stubPortfolioSummary } from './summary-fixture'
import { stubTransactions } from './transactions-fixture'

const ROUTES: { path: string; name: string; stub?: RouteStub }[] = [
  { path: '/', name: 'summary', stub: stubPortfolioSummary },
  { path: '/transactions', name: 'transactions', stub: stubTransactions },
  { path: '/instruments', name: 'instruments', stub: stubInstruments },
  { path: '/etf-breakdown', name: 'etf-breakdown', stub: stubEtfBreakdown },
  { path: '/diversification', name: 'diversification', stub: stubDiversification },
  { path: '/calculator', name: 'calculator', stub: stubCalculator },
]

test.beforeEach(async ({ page }) => {
  await stubBuildInfo(page)
})

for (const route of ROUTES) {
  test(`route ${route.name}`, async ({ page }) => {
    await route.stub?.(page)
    await openRoute(page, route.path)
    await freeze(page)
    await expect(page).toHaveScreenshot(`route-${route.name}.png`, { fullPage: true })
  })
}
