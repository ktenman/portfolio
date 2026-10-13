import { test, expect, type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import { STORAGE_KEYS } from '../../constants/storage-keys'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import { openRoute, settleAndFreeze } from './settle'
import { apiRoute } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { longHistory, stubPortfolioSummary } from './summary-fixture'

const NOW = new Date('2026-01-15T12:00:00')
const CARD = '.monthly-profit-calendar'
const LOADING_HOLD_MS = 20000
const GAPS = ['2021', '2023-02', '2024-03']
const VIEWPORTS = [
  { width: 1440, height: 900 },
  { width: 769, height: 1024 },
  { width: 768, height: 1024 },
  { width: 390, height: 844 },
  { width: 375, height: 667 },
  { width: 320, height: 568 },
]

async function openMonthly(page: Page, rows?: PortfolioSummaryDto[]): Promise<void> {
  await page.addInitScript(
    key => localStorage.setItem(key, 'monthly'),
    STORAGE_KEYS.SUMMARY_INTERVAL
  )
  if (rows) {
    await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), route =>
      route.fulfill({ json: rows })
    )
    await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_CURRENT), route =>
      route.fulfill({ json: rows[rows.length - 1] })
    )
  }
  await openRoute(page, '/')
  await expect(page.locator(`${CARD} h2`).filter({ visible: true })).toHaveText('Monthly profit')
}

const layout = (page: Page) =>
  page.evaluate(card => {
    const visible = (selector: string) =>
      Array.from(document.querySelectorAll<HTMLElement>(selector)).filter(
        element => element.getClientRects().length > 0
      )
    const height = (selector: string) =>
      Math.min(...visible(selector).map(element => element.getBoundingClientRect().height))
    const centre = (selector: string) => {
      const [first] = visible(selector)
      return first ? Math.round(first.getBoundingClientRect().x + first.offsetWidth / 2) : 0
    }
    const root = document.documentElement
    const box = document.querySelector<HTMLElement>(card)!
    return {
      pageOverflows: root.scrollWidth > root.clientWidth,
      cardOverflows: box.scrollWidth > box.clientWidth,
      clippedCells: visible('.month-slot, .year-total').filter(
        cell => cell.scrollWidth > cell.clientWidth
      ).length,
      januaryOffset: centre('.month-label, .dot-letter') - centre('.month-slot, .dot-place'),
      chipHeight: height('[aria-label=Interval] button'),
      arrowHeight: height(`${card} .arrow-btn`),
      cardHeight: box.getBoundingClientRect().height,
      tableFrame: visible('.desktop-table-wrapper').map(frame => getComputedStyle(frame).overflowX),
    }
  }, CARD)

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(NOW)
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
})

test('monthly fixture', async ({ page }) => {
  await openMonthly(page)
  await settleAndFreeze(page)
  await expect(page.locator(CARD)).toHaveScreenshot('monthly-fixture.png')
})

test('monthly seven years', async ({ page }) => {
  await openMonthly(page, longHistory(7))
  await settleAndFreeze(page)
  await expect(page.locator(CARD)).toHaveScreenshot('monthly-seven-years.png')
})

test.describe('desktop', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop')
  })

  test('monthly gaps', async ({ page }) => {
    await openMonthly(page, longHistory(6, GAPS))
    await page.locator(`${CARD} .month-slot[aria-label^="April 2024"]`).click()
    await page.mouse.move(0, 0)
    await settleAndFreeze(page)
    await expect(page.locator(CARD)).toHaveScreenshot('monthly-gaps.png')
  })

  test('monthly loss month', async ({ page }) => {
    await openMonthly(page, longHistory(7))
    await page.locator(`${CARD} .month-slot.bg-loss`).first().click()
    await page.mouse.move(0, 0)
    await settleAndFreeze(page)
    await expect(page.locator(CARD)).toHaveScreenshot('monthly-loss-month.png')
  })

  test('monthly loading', async ({ page }) => {
    await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), async route => {
      if (new URL(route.request().url()).searchParams.get('range') !== 'MAX')
        return route.fallback()
      await new Promise(resolve => setTimeout(resolve, LOADING_HOLD_MS))
      await route.abort().catch(() => undefined)
    })
    await openRoute(page, '/')
    await page.getByRole('button', { name: 'Monthly', exact: true }).click()
    await page.mouse.move(0, 0)
    await expect(page.locator('.loading-veil')).toBeVisible()
    await expect(page.locator('.daily-profit-calendar')).toHaveScreenshot('monthly-loading.png')
  })

  test('the grid is one tab stop and its arrow keys move the outlined month', async ({ page }) => {
    await openMonthly(page)
    const grid = page.getByRole('application', { name: 'Monthly profit calendar' })
    await page.getByRole('button', { name: 'Monthly', exact: true }).focus()
    await page.keyboard.press('Tab')
    const focused = await grid.evaluate(element => [
      element === document.activeElement,
      element.querySelectorAll('[tabindex="0"], button, a').length,
      getComputedStyle(element).outlineStyle,
    ])

    await page.keyboard.press('ArrowLeft')

    await expect(page.locator('.panel-label').filter({ visible: true })).toContainText(
      'November 2025'
    )
    expect(focused).toEqual([true, 0, 'solid'])
  })

  for (const years of [1, 7, 12]) {
    for (const viewport of VIEWPORTS) {
      test(`${years} years fit ${viewport.width}x${viewport.height}`, async ({ page }) => {
        await page.setViewportSize(viewport)
        await openMonthly(page, longHistory(years))
        const isPhone = viewport.width < 768
        const measured = await layout(page)
        expect({
          ...measured,
          chipHeight: isPhone ? measured.chipHeight >= 44 : true,
          arrowHeight: isPhone || measured.arrowHeight >= 32,
          cardHeight: viewport.width !== 375 || years > 7 || measured.cardHeight <= viewport.height,
          tableFrame: measured.tableFrame.every(overflow => overflow === 'auto'),
        }).toEqual({
          pageOverflows: false,
          cardOverflows: false,
          clippedCells: 0,
          januaryOffset: 0,
          chipHeight: true,
          arrowHeight: true,
          cardHeight: true,
          tableFrame: true,
        })
      })
    }
  }
})
