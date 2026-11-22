import { expect, type Page, test } from '@playwright/test'
import { stubShell } from './shell-fixture'
import { stubCalculator } from './calculator-fixture'
import { stubEnums } from './enums-fixture'
import { freeze, openRoute, settleAndFreeze } from './settle'

const HOSTED = 'http://fov.ee'
const LOCAL = 'http://localhost:61234'
const MENU_MARGIN_PX = 12

async function openHosted(page: Page): Promise<void> {
  await page.route(`${HOSTED}/**`, async route => {
    const local = route.request().url().replace(HOSTED, LOCAL)
    await route.fulfill({ response: await route.fetch({ url: local }) })
  })
  await stubShell(page)
  await stubEnums(page)
  await stubCalculator(page)
  await openRoute(page, `${HOSTED}/calculator`)
}

async function showNavEnd(page: Page): Promise<void> {
  await page
    .locator('nav.navbar .navbar-scroll-container')
    .evaluate(row => (row.scrollLeft = row.scrollWidth))
}

test('the navbar offers Log out when served from fov.ee', async ({ page }) => {
  await openHosted(page)
  const navbar = page.locator('nav.navbar')
  await expect(navbar.getByRole('button', { name: 'Log out' })).toBeVisible()
  await showNavEnd(page)
  await settleAndFreeze(page)
  await expect(navbar).toHaveScreenshot('log-out-navbar.png')
})

test('the Theme menu opens under its glyph', async ({ page }) => {
  await openHosted(page)
  await showNavEnd(page)
  await page.getByRole('button', { name: 'Theme', exact: true }).click()
  await freeze(page)
  const menu = (await page.locator('#theme-menu').boundingBox())!
  await expect(page).toHaveScreenshot('theme-menu.png', {
    clip: {
      x: 0,
      y: 0,
      width: page.viewportSize()!.width,
      height: menu.y + menu.height + MENU_MARGIN_PX,
    },
  })
})

test('the navbar row cannot scroll up and down', async ({ page }) => {
  await openHosted(page)
  const row = page.locator('nav.navbar .navbar-scroll-container')
  await row.locator('.nav-link').first().hover()
  await page.mouse.wheel(0, 100)
  await page.evaluate(() => new Promise(requestAnimationFrame))
  expect(await row.evaluate(element => element.scrollTop)).toBe(0)
})
