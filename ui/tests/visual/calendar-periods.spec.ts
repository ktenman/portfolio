import { test, expect, type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import { openRoute, settleAndFreeze } from './settle'
import { apiRoute } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { SERIES_RESPONSE, stubPortfolioSummary, TODAY_SUMMARY } from './summary-fixture'

const PHONE = { width: 375, height: 667 }
const SMALL_PHONE = { width: 320, height: 568 }
const SUBPIXEL_PX = 0.5
const EARLIER_DATES = '27 Jun – 30 Jun 2025'
const LATEST_DATES = '30 Jun – 31 Dec 2025'
const LATEST_START = '2025-06-30'
const TWO_YEAR_DATES = '28 Nov 2025 – 28 May 2026'

const dates = (page: Page) => page.locator('.period-dates').filter({ visible: true })
const arrow = (page: Page, name: string) => page.getByRole('button', { name })

async function openEarlierPeriod(page: Page): Promise<void> {
  await openRoute(page, '/')
  await arrow(page, 'Earlier period').click()
  await expect(dates(page)).toHaveText(EARLIER_DATES)
}

const headRow = (page: Page) =>
  page.evaluate(() => {
    const head = document.querySelector('.calendar-head')!
    const heading = document.querySelector('.calendar-heading')!
    return {
      extra:
        head.getBoundingClientRect().height -
        parseFloat(getComputedStyle(head).paddingBottom) -
        heading.getBoundingClientRect().height,
      laterX: head.querySelector('[aria-label="Later period"]')!.getBoundingClientRect().x,
    }
  })

const cellSize = async (page: Page) => {
  const cell = await page.locator('.calendar-grid.compact .aspect-square').first().boundingBox()
  return [cell!.width, cell!.height]
}

const phoneLayout = (page: Page) =>
  page.evaluate(subpixel => {
    const box = (selector: string) => document.querySelector(selector)!.getBoundingClientRect()
    const card = document.querySelector<HTMLElement>('.daily-profit-calendar')!
    const heading = box('.compact-heading')
    const counts = box('.compact-counts')
    const [earlier, later] = Array.from(document.querySelectorAll('.compact-period button')).map(
      button => button.getBoundingClientRect()
    )
    const datesText = document.createRange()
    datesText.selectNodeContents(document.querySelector('.compact-period .period-dates')!)
    const dates = datesText.getBoundingClientRect()
    const lineWidths = Array.from(datesText.getClientRects(), line => line.width)
    return {
      scrollsSideways: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      headingLines: Math.round(
        heading.height /
          parseFloat(getComputedStyle(document.querySelector('.compact-heading')!).lineHeight)
      ),
      countsBesideHeading: counts.top < heading.bottom && counts.left >= heading.right,
      datesLines: lineWidths.length,
      datesLinesEven: Math.min(...lineWidths) * 2 > Math.max(...lineWidths),
      datesBetweenArrows:
        earlier.right <= dates.left + subpixel && dates.right <= later.left + subpixel,
      arrowsAtCardEdges:
        Math.abs(earlier.left - box('.daily-profit-calendar').left - card.clientLeft) <= subpixel &&
        Math.abs(box('.daily-profit-calendar').right - card.clientLeft - later.right) <= subpixel,
      arrowsReach44: Math.min(earlier.width, earlier.height, later.width, later.height) >= 44,
      fitsScreen: box('.daily-profit-calendar').height <= window.innerHeight,
    }
  }, SUBPIXEL_PX)

test.beforeEach(async ({ page }) => {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), route =>
    new URL(route.request().url()).searchParams.get('range') === '6M'
      ? route.fulfill({ json: SERIES_RESPONSE.filter(row => row.date >= LATEST_START) })
      : route.fallback()
  )
})

test('calendar earlier period', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await openEarlierPeriod(page)
  await page.mouse.move(0, 0)
  await settleAndFreeze(page)
  await expect(page.locator('.daily-profit-calendar')).toHaveScreenshot(
    'calendar-earlier-period.png'
  )
})

test('changing the platform filter opens the latest period', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await openEarlierPeriod(page)

  await page.locator('.platform-buttons .platform-btn:text-is("LHV")').click()

  await expect(dates(page)).toHaveText(LATEST_DATES)
})

test.describe('wide layout', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name === 'mobile')
  })

  test('the period control keeps the heading row and its place through a step', async ({
    page,
  }) => {
    await openRoute(page, '/')
    const before = await headRow(page)

    await arrow(page, 'Earlier period').click()
    await expect(dates(page)).toHaveText(EARLIER_DATES)
    const after = await headRow(page)

    expect([before.extra, after.laterX]).toEqual([0, before.laterX])
  })
})

test.describe('phone layout', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
  })

  test('the daily profit calendar fits a 375 by 667 phone screen', async ({ page }) => {
    await page.setViewportSize(PHONE)
    await openRoute(page, '/')
    await expect(page.locator('.compact-heading')).toBeVisible()

    expect(await phoneLayout(page)).toEqual({
      scrollsSideways: false,
      headingLines: 1,
      countsBesideHeading: true,
      datesLines: 1,
      datesLinesEven: true,
      datesBetweenArrows: true,
      arrowsAtCardEdges: true,
      arrowsReach44: true,
      fitsScreen: true,
    })
  })

  test('the earlier period keeps the cell size of the latest period', async ({ page }) => {
    await openRoute(page, '/')
    const before = await cellSize(page)

    await arrow(page, 'Earlier period').click()
    await expect(dates(page)).toHaveText(EARLIER_DATES)
    const after = await cellSize(page)

    expect(after).toEqual(before)
  })

  test('a period across two years fits a 320 pixel phone', async ({ page }) => {
    await page.setViewportSize(SMALL_PHONE)
    await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_CURRENT), route =>
      route.fulfill({ json: { ...TODAY_SUMMARY, date: '2026-05-28' } })
    )
    await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), route =>
      route.fulfill({ json: [{ ...TODAY_SUMMARY, date: '2025-11-28' }] })
    )
    await openRoute(page, '/')
    await expect(dates(page)).toHaveText(TWO_YEAR_DATES)

    const layout = await phoneLayout(page)

    expect([
      layout.scrollsSideways,
      layout.datesBetweenArrows,
      layout.arrowsReach44,
      layout.datesLinesEven,
    ]).toEqual([false, true, true, true])
  })
})
