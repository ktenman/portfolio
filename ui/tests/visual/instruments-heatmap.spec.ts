import { test, expect, type Page } from '@playwright/test'
import { freeze, openRoute } from './settle'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubInstruments } from './instruments-fixture'

const MAX_CAPTURE_HEIGHT = 12000
const TOUCH_PX = 44
const WIDTHS = [1440, 1024, 1023, 768, 390, 375, 320]
const PHONE_WIDTHS = [390, 375, 320]

test.beforeEach(async ({ page }) => {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubInstruments(page)
  await openRoute(page, '/instruments/heatmap')
})

const capture = async (page: Page, name: string) => {
  await freeze(page)
  const width = await page.evaluate(() => document.documentElement.scrollWidth)
  await expect(page).toHaveScreenshot(name, {
    fullPage: true,
    clip: { x: 0, y: 0, width, height: MAX_CAPTURE_HEIGHT },
  })
}

test('heatmap', async ({ page }) => {
  await capture(page, 'instruments-heatmap.png')
})

test('heatmap with a box selected', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await page.getByRole('button', { name: /^TSTINF,/ }).click()
  await page.mouse.move(0, 0)
  await capture(page, 'instruments-heatmap-selected.png')
})

test('readout logo shows on hover that it can be replaced', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await page.getByRole('button', { name: /^TSTINF,/ }).click()
  await freeze(page)
  await page.getByTitle('Click to replace logo').hover()
  await expect(page.locator('[data-readout]')).toHaveScreenshot(
    'instruments-heatmap-logo-hover.png'
  )
})

test('heatmap fits every width without horizontal scroll', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await expect
      .poll(() =>
        page.evaluate(() => ({
          width: window.innerWidth,
          overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        }))
      )
      .toEqual({ width, overflow: 0 })
  }
})

test('heatmap readout shows every figure in full on phones', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  for (const width of PHONE_WIDTHS) {
    await page.setViewportSize({ width, height: 844 })
    await expect
      .poll(() =>
        page.evaluate(() => ({
          width: window.innerWidth,
          clipped: Array.from(document.querySelectorAll('[data-readout] dd'))
            .filter(figure => figure.scrollWidth > figure.clientWidth)
            .map(figure => figure.textContent?.trim()),
        }))
      )
      .toEqual({ width, clipped: [] })
  }
})

test('change list bars grow rightward from one edge on phones', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  for (const width of PHONE_WIDTHS) {
    await page.setViewportSize({ width, height: 844 })
    await expect
      .poll(() =>
        page.evaluate(() => ({
          width: window.innerWidth,
          starts: new Set(
            Array.from(
              document.querySelectorAll('[data-bar]'),
              bar => bar.getBoundingClientRect().left
            )
          ).size,
        }))
      )
      .toEqual({ width, starts: 1 })
  }
})

test('view tabs are 44 px tall on phones', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  const heights = await page
    .getByRole('navigation', { name: 'Instrument views' })
    .getByRole('link')
    .evaluateAll(links => links.map(link => link.getBoundingClientRect().height))
  expect(heights).toEqual([TOUCH_PX, TOUCH_PX])
})
