import { expect, type Page, test } from '@playwright/test'
import { stubBuildInfo } from './build-info-fixture'
import { stubCalculator } from './calculator-fixture'
import { stubEnums } from './enums-fixture'
import { openRoute, settleAndFreeze } from './settle'

const HOSTED = 'http://fov.ee'
const LOCAL = 'http://localhost:61234'

async function openHosted(page: Page): Promise<void> {
  await page.route(`${HOSTED}/**`, async route => {
    const local = route.request().url().replace(HOSTED, LOCAL)
    await route.fulfill({ response: await route.fetch({ url: local }) })
  })
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubCalculator(page)
  await openRoute(page, `${HOSTED}/calculator`)
}

test('the navbar offers Log out when served from fov.ee', async ({ page }) => {
  await openHosted(page)
  const navbar = page.locator('nav.navbar')
  await expect(navbar.getByRole('button', { name: 'Log out' })).toBeVisible()
  await navbar
    .locator('.navbar-scroll-container')
    .evaluate(row => (row.scrollLeft = row.scrollWidth))
  await settleAndFreeze(page)
  await expect(navbar).toHaveScreenshot('log-out-navbar.png')
})

test('the navbar row cannot scroll up and down', async ({ page }) => {
  await openHosted(page)
  const row = page.locator('nav.navbar .navbar-scroll-container')
  await page.getByRole('button', { name: 'Log out' }).hover()
  await page.mouse.wheel(0, 100)
  await page.evaluate(() => new Promise(requestAnimationFrame))
  expect(await row.evaluate(element => element.scrollTop)).toBe(0)
})
