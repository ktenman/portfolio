import { test, expect, type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import { freeze, openRoute } from './settle'
import { apiRoute, type RouteStub } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { SERIES_RESPONSE, stubPortfolioSummary } from './summary-fixture'

const FIXTURE_TODAY = '2026-01-15T12:00:00Z'
const SYNTHETIC_TODAY = '2026-10-17T12:00:00Z'
const PHONE = { width: 375, height: 667 }
const SMALL_PHONE = { width: 320, height: 568 }
const NARROWEST_WIDE = { width: 769, height: 1024 }
const MAX_CAPTURE_HEIGHT = 12000
const TOUCH_PX = 44
const COMPACT_MAX_WIDTH = 768
const PHONE_MAX_WIDTH = 575
const SERIES_ROWS = 184
const HISTORICAL_ROWS = 30
const START_VALUE = 100000
const DAY_MS = 86_400_000
const CYCLE = [0.1, 0.6, 1.7, 3.7, 7.5, -0.1, -0.6, -1.7, -3.7, -7.5]
const LONG_HISTORY_TARGETS: Record<string, number> = { '2009-03': 150, '2009-04': -60 }

const LAYOUT_VIEWPORTS = [
  [1440, 900],
  [1280, 800],
  [1100, 800],
  [1024, 768],
  [900, 1024],
  [769, 1024],
  [768, 1024],
  [390, 844],
  [375, 667],
  [320, 568],
].map(([width, height]) => ({ width, height }))

const datesBetween = (from: string, to: string) =>
  Array.from({ length: Math.round((Date.parse(to) - Date.parse(from)) / DAY_MS) + 1 }, (_, index) =>
    new Date(Date.parse(from) + index * DAY_MS).toISOString().slice(0, 10)
  )

const cycleTarget = (month: string) =>
  CYCLE[(Number(month.slice(0, 4)) * 12 + Number(month.slice(5, 7)) - 1) % CYCLE.length]

const cents = (amount: number) => Math.round(amount * 100) / 100

const summaryOn = (date: string, value: number, previous: number): PortfolioSummaryDto => ({
  date,
  totalValue: cents(value),
  xirrAnnualReturn: 0.0812,
  realizedProfit: 0,
  unrealizedProfit: cents(cents(value) - START_VALUE),
  totalProfit: cents(cents(value) - START_VALUE),
  earningsPerDay: 12.5,
  earningsPerMonth: 380.21,
  totalProfitChange24h: cents(cents(value) - cents(previous)),
})

const compound = (from: string, to: string, target: (month: string) => number) => {
  const dates = datesBetween(from, to)
  const days = new Map<string, number>()
  dates.slice(1).forEach(date => days.set(date.slice(0, 7), (days.get(date.slice(0, 7)) ?? 0) + 1))
  const values = [START_VALUE]
  dates.slice(1).forEach(date => {
    const month = date.slice(0, 7)
    values.push(values[values.length - 1] * (1 + target(month) / 100) ** (1 / days.get(month)!))
  })
  return dates.map((date, index) => summaryOn(date, values[index], values[Math.max(index - 1, 0)]))
}

const SEVEN_YEARS = compound('2019-12-31', '2026-10-16', cycleTarget)

const LONG_HISTORY = compound(
  '2008-12-31',
  '2026-10-16',
  month => LONG_HISTORY_TARGETS[month] ?? cycleTarget(month)
).filter(row => row.date < '2014-11-20' || row.date > '2015-02-10')

const stubHistory =
  (rows: PortfolioSummaryDto[]): RouteStub =>
  async page => {
    const newest = [...rows].reverse()
    await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), route =>
      route.fulfill({
        json:
          new URL(route.request().url()).searchParams.get('range') === 'MAX'
            ? newest
            : newest.slice(0, SERIES_ROWS),
      })
    )
    await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_CURRENT), route =>
      route.fulfill({ json: newest[0] })
    )
    await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_HISTORICAL), route =>
      route.fulfill({
        json: {
          content: newest.slice(0, HISTORICAL_ROWS),
          totalPages: 1,
          totalElements: HISTORICAL_ROWS,
          size: HISTORICAL_ROWS,
          number: 0,
        },
      })
    )
  }

const stubMissingSeptember: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), route =>
    route.fulfill({ json: SERIES_RESPONSE.filter(row => !row.date.startsWith('2025-09')) })
  )
}

const HISTORIES = [
  { name: 'one year', today: FIXTURE_TODAY },
  { name: 'seven years', today: SYNTHETIC_TODAY, stub: stubHistory(SEVEN_YEARS) },
  { name: 'long history', today: SYNTHETIC_TODAY, stub: stubHistory(LONG_HISTORY) },
]

const calendar = (page: Page) => page.locator('.monthly-profit-calendar')

async function openMonthly(page: Page, today: string, stub?: RouteStub): Promise<void> {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
  await stub?.(page)
  await page.clock.setFixedTime(today)
  await page.addInitScript(() => localStorage.setItem('portfolio_summary_interval', 'monthly'))
  await openRoute(page, '/')
  await expect(page.locator('.monthly-profit-calendar:not([aria-hidden])')).toBeVisible()
  await page.mouse.move(0, 0)
}

const nextFrame = (page: Page) =>
  page.evaluate(
    () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  )

const layoutProblems = (page: Page, viewport: { width: number; height: number }) =>
  page.evaluate(
    ({ touch, isCompact, isPhone }) => {
      const root = document.documentElement
      const sideways =
        root.scrollWidth > root.clientWidth
          ? [`page scrolls sideways by ${root.scrollWidth - root.clientWidth}px`]
          : []
      const clipped = Array.from(document.querySelectorAll('.month-cell'))
        .filter(cell => cell.scrollWidth > cell.clientWidth)
        .map(cell => `${cell.getAttribute('aria-label')} clips`)
      const targets = Array.from(
        document.querySelectorAll(
          '[aria-label="Interval"] button, .monthly-profit-calendar .month-buttons button'
        )
      ).filter(target => target.getClientRects().length > 0)
      const tapHeight = (target: Element) =>
        Math.max(
          target.getBoundingClientRect().height,
          parseFloat(getComputedStyle(target, '::before').height) || 0
        )
      const short = isCompact
        ? targets
            .filter(target => tapHeight(target) < touch)
            .map(
              target =>
                `${target.getAttribute('aria-label') ?? target.textContent?.trim()} is ${tapHeight(target)}px tall to tap`
            )
        : []
      const segments = Array.from(
        document.querySelectorAll('[aria-label="Interval"] button'),
        segment => segment.getBoundingClientRect()
      )
      const card = document
        .querySelector('.monthly-profit-calendar:not([aria-hidden])')!
        .getBoundingClientRect()
      const uneven =
        isPhone &&
        (Math.abs(segments[0].left - card.left) > 1 ||
          Math.abs(segments[1].right - card.right) > 1 ||
          Math.abs(segments[0].width - segments[1].width) > 1)
      const split = uneven
        ? [
            `Interval segments span ${segments.map(({ left, right }) => `${left}–${right}`).join(' and ')} over a ${card.left}–${card.right} card`,
          ]
        : []
      return [...sideways, ...clipped, ...short, ...split]
    },
    {
      touch: TOUCH_PX,
      isCompact: viewport.width <= COMPACT_MAX_WIDTH,
      isPhone: viewport.width <= PHONE_MAX_WIDTH,
    }
  )

test('the monthly summary shows the calendar and month rows', async ({ page }) => {
  await openMonthly(page, FIXTURE_TODAY)
  await freeze(page)
  const width = await page.evaluate(() => document.documentElement.scrollWidth)
  await expect(page).toHaveScreenshot('summary-monthly.png', {
    fullPage: true,
    clip: { x: 0, y: 0, width, height: MAX_CAPTURE_HEIGHT },
    mask: [page.locator('canvas')],
  })
})

test('the monthly calendar fits the narrowest wide layout', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'monthly-tablet')
  await page.setViewportSize(NARROWEST_WIDE)
  await openMonthly(page, FIXTURE_TODAY)
  await freeze(page)
  await expect(calendar(page)).toHaveScreenshot('calendar-769.png')
})

test('the monthly calendar fits small phones', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'monthly-mobile')
  await page.setViewportSize(PHONE)
  await openMonthly(page, FIXTURE_TODAY)
  await freeze(page)
  await expect(calendar(page)).toHaveScreenshot('calendar-375.png')
  await page.setViewportSize(SMALL_PHONE)
  await expect(calendar(page)).toHaveScreenshot('calendar-320.png')
})

test('seven years of months fit the calendar', async ({ page }, testInfo) => {
  if (testInfo.project.name === 'monthly-mobile') await page.setViewportSize(PHONE)
  await openMonthly(page, SYNTHETIC_TODAY, stubHistory(SEVEN_YEARS))
  await freeze(page)
  await expect(calendar(page)).toHaveScreenshot('seven-years.png')
})

test('a long history grows the calendar without clipping', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'monthly-tablet')
  if (testInfo.project.name === 'monthly-mobile') await page.setViewportSize(SMALL_PHONE)
  await openMonthly(page, SYNTHETIC_TODAY, stubHistory(LONG_HISTORY))
  await freeze(page)
  const clip = await calendar(page).boundingBox()
  await expect(page).toHaveScreenshot('long-history.png', { fullPage: true, clip: clip! })
})

test('a missing month draws a dashed slot and a two-month interval', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'monthly-tablet')
  await openMonthly(page, FIXTURE_TODAY, stubMissingSeptember)
  if (testInfo.project.name === 'monthly-desktop') {
    await page.locator('.calendar-grid [aria-label^="October 2025,"]').click()
    await page.mouse.move(0, 0)
  }
  await freeze(page)
  await expect(calendar(page)).toHaveScreenshot('missing-month.png')
})

test.describe('layout', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name !== 'monthly-desktop')
  })

  for (const history of HISTORIES) {
    test(`the ${history.name} fits every viewport without sideways scroll or clipping`, async ({
      page,
    }) => {
      await openMonthly(page, history.today, history.stub)
      const problems: string[] = []
      for (const viewport of LAYOUT_VIEWPORTS) {
        await page.setViewportSize(viewport)
        await nextFrame(page)
        const found = await layoutProblems(page, viewport)
        problems.push(...found.map(problem => `${viewport.width}×${viewport.height}: ${problem}`))
      }
      expect(problems).toEqual([])
    })
  }

  test('seven years of months fit a 375 by 667 phone screen', async ({ page }) => {
    await openMonthly(page, SYNTHETIC_TODAY, stubHistory(SEVEN_YEARS))
    await page.setViewportSize(PHONE)
    await nextFrame(page)
    const height = await calendar(page).evaluate(card => card.getBoundingClientRect().height)
    expect(height).toBeLessThanOrEqual(PHONE.height)
  })
})
