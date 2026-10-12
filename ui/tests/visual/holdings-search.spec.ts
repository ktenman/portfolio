import { expect, test, type Locator } from '@playwright/test'
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

test('Holdings query preserves aligned bars and small weights', async ({ page }) => {
  await page.route(apiRoute(API_ENDPOINTS.LIVE_UPDATES), route => route.fulfill({ status: 204 }))
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubDiversification(page)
  await openRoute(page, '/diversification')
  const panel = page.locator('.breakdown-panel')
  await panel.getByRole('button', { name: 'Holdings', exact: true }).click()
  await expect(panel.locator('.breakdown-row')).toHaveCount(100)
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
