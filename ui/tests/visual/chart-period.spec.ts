import { test, expect, type Page } from '@playwright/test'
import { openRoute, settleAndFreeze } from './settle'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubPortfolioSummary } from './summary-fixture'

const PLOT_HEIGHT_SHARE = 0.4
const PERIOD_FROM_SHARE = 0.35
const PERIOD_TO_SHARE = 0.7
const DRAG_STEPS = 8
const MARGIN_PX = 8

async function openSummary(page: Page): Promise<void> {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
  await openRoute(page, '/')
}

async function drawPeriod(page: Page): Promise<void> {
  const chart = page.getByTestId('summary-chart')
  await chart.scrollIntoViewIfNeeded()
  const box = (await chart.boundingBox())!
  const y = box.y + box.height * PLOT_HEIGHT_SHARE
  await page.mouse.move(box.x + box.width * PERIOD_FROM_SHARE, y)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width * PERIOD_TO_SHARE, y, { steps: DRAG_STEPS })
  await page.mouse.up()
  await page.mouse.move(0, 0)
}

async function captureChart(page: Page, name: string): Promise<void> {
  await settleAndFreeze(page)
  await page.evaluate(() => window.scrollTo(0, 0))
  const headline = (await page.locator('.portfolio-headline').boundingBox())!
  const controls = (await page.getByTestId('chart-controls').boundingBox())!
  const top = headline.y - MARGIN_PX
  await expect(page).toHaveScreenshot(name, {
    fullPage: true,
    clip: {
      x: 0,
      y: top,
      width: page.viewportSize()!.width,
      height: controls.y + controls.height + MARGIN_PX - top,
    },
  })
}

test('a period drawn across the summary chart', async ({ page }) => {
  await openSummary(page)
  await drawPeriod(page)
  await captureChart(page, 'chart-period-drawn.png')
})

test('the summary chart zoomed to a drawn period', async ({ page }) => {
  await openSummary(page)
  await drawPeriod(page)
  await page.getByTestId('period-zoom').click()
  await page.mouse.move(0, 0)
  await captureChart(page, 'chart-period-zoomed.png')
})
