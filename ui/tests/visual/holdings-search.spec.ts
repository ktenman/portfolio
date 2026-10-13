import { expect, test, type Locator, type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import { stubBuildInfo } from './build-info-fixture'
import { stubDiversification } from './diversification-fixture'
import { stubEnums } from './enums-fixture'
import { openRoute } from './settle'
import { apiRoute } from './stub'

const trackWidths = (panel: Locator) =>
  panel
    .locator('.row-track')
    .evaluateAll(tracks =>
      tracks.map(track => Math.round(track.getBoundingClientRect().width * 100) / 100)
    )

const openHoldings = async (page: Page) => {
  await page.route(apiRoute(API_ENDPOINTS.LIVE_UPDATES), route => route.fulfill({ status: 204 }))
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubDiversification(page)
  await openRoute(page, '/diversification')
  const panel = page.locator('.breakdown-panel')
  await panel.getByRole('button', { name: 'Holdings', exact: true }).click()
  await expect(panel.locator('.breakdown-row')).toHaveCount(100)
  return panel
}

test('Holdings query preserves aligned bars and small weights', async ({ page }) => {
  const panel = await openHoldings(page)
  await expect(panel.getByRole('status')).toHaveText('1,842 holdings')
  await panel.getByRole('textbox', { name: 'Search holdings' }).fill('n')
  await expect(panel.getByRole('status')).toHaveText('8 matches')
  await expect(panel.locator('.row-value').filter({ hasText: '0.0049%' })).toHaveCount(1)
  await expect(panel.locator('.row-value').filter({ hasText: '<0.0001%' })).toHaveCount(1)
  await expect(panel.locator('.row-benchmark')).toHaveCount(4)
  expect(new Set(await trackWidths(panel)).size).toBe(1)
  await expect(panel).toHaveScreenshot('diversification-search.png')
  await panel.locator('.compare-toggle').click()
  await expect(panel.getByRole('switch')).not.toBeChecked()
  expect(new Set(await trackWidths(panel)).size).toBe(1)
  await expect(panel.locator('.row-benchmark')).toHaveCount(0)
})

test('Holdings append the next hundred as the end of the list nears', async ({ page }) => {
  const panel = await openHoldings(page)
  const rows = panel.locator('.breakdown-row')
  await rows.nth(99).scrollIntoViewIfNeeded()
  await expect(rows).toHaveCount(200)
  await expect(rows.nth(99)).toBeInViewport()
})

test('Holdings offer a retry when more cannot be loaded', async ({ page }) => {
  const panel = await openHoldings(page)
  let fail!: () => void
  const gate = new Promise<void>(resolve => {
    fail = resolve
  })
  await page.route(apiRoute(`${API_ENDPOINTS.DIVERSIFICATION}/holdings`), async route => {
    await gate
    await route.fulfill({ status: 500 })
  })
  const more = panel.locator('.holdings-more')
  await more.scrollIntoViewIfNeeded()
  await expect(more).toHaveScreenshot('diversification-more-loading.png')
  fail()
  await expect(more.getByRole('button', { name: 'Retry' })).toBeVisible()
  await expect(more).toHaveScreenshot('diversification-more-failed.png')
})
