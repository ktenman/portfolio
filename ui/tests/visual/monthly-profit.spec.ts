import { expect, test, type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import { STORAGE_KEYS } from '../../constants/storage-keys'
import { openRoute, settleAndFreeze } from './settle'
import { apiRoute } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { SERIES_RESPONSE, stubPortfolioSummary } from './summary-fixture'
import {
  monthlyGapFixture,
  monthlyHistoryFixture,
  stubMonthlyHistory,
} from './monthly-profit-fixture'
import { createPortfolioSummaryDto } from '../fixtures'

const card = (page: Page) => page.locator('.monthly-profit-calendar')
const interval = (page: Page, name: string) =>
  page.getByRole('group', { name: 'Interval' }).getByRole('button', { name })

const restoreMonthly = (page: Page) =>
  page.addInitScript(key => localStorage.setItem(key, 'monthly'), STORAGE_KEYS.SUMMARY_INTERVAL)

const layout = (page: Page) =>
  page.evaluate(() => {
    const calendar = document.querySelector<HTMLElement>('.monthly-profit-calendar')!
    const squeezed = Array.from(
      calendar.querySelectorAll<HTMLElement>('.monthly-profit-cell, .monthly-profit-year-total')
    )
      .filter(element => element.scrollWidth > element.clientWidth + 1)
      .map(element => element.textContent?.trim())
    const intervalButtons = Array.from(
      document.querySelectorAll('[aria-label="Interval"] button')
    ).map(button => button.getBoundingClientRect())
    return {
      pageOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      calendarOverflow: calendar.scrollWidth > calendar.clientWidth + 1,
      squeezed,
      touchHeight: Math.min(...intervalButtons.map(button => button.height)),
      height: calendar.getBoundingClientRect().height,
    }
  })

test.beforeEach(async ({ page }) => {
  await page.clock.install({ time: new Date('2026-10-13T12:00:00Z') })
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
})

test('monthly summary fixture', async ({ page }) => {
  await restoreMonthly(page)
  await openRoute(page, '/')
  await expect(card(page)).toBeVisible()
  await expect(page.locator('thead')).toContainText('Month Change')
  await expect(page.locator('tbody tr')).toHaveCount(7)
  await expect(card(page)).toContainText('€6,334')
  await expect(card(page)).toContainText('37.6%')
  await expect(page.locator('.summary-interval-announcement')).toBeEmpty()
  expect((await layout(page)).pageOverflow).toBe(false)
  await settleAndFreeze(page)
  await expect(page).toHaveScreenshot('monthly-summary-fixture.png', {
    fullPage: true,
    mask: [page.locator('canvas')],
  })
})

for (const scenario of [
  { name: 'seven years', rows: monthlyHistoryFixture() },
  { name: 'missing months and years', rows: monthlyGapFixture() },
  { name: 'long history', rows: monthlyHistoryFixture(16) },
]) {
  test(`monthly profit ${scenario.name}`, async ({ page }) => {
    await restoreMonthly(page)
    await stubMonthlyHistory(page, scenario.rows)
    await openRoute(page, '/')
    await expect(card(page)).toBeVisible()
    const measured = await layout(page)
    expect([measured.pageOverflow, measured.calendarOverflow, measured.squeezed]).toEqual([
      false,
      false,
      [],
    ])
    if (page.viewportSize()!.width <= 768) expect(measured.touchHeight).toBeGreaterThanOrEqual(44)
    await settleAndFreeze(page)
    await expect(card(page)).toHaveScreenshot(
      `monthly-profit-${scenario.name.replaceAll(' ', '-')}.png`
    )
  })
}

for (const viewport of [
  { width: 769, height: 1024 },
  { width: 375, height: 667 },
  { width: 320, height: 568 },
]) {
  test(`monthly profit fits ${viewport.width} by ${viewport.height}`, async ({
    page,
  }, testInfo) => {
    test.skip(!testInfo.project.name.endsWith(viewport.width > 768 ? 'desktop' : 'mobile'))
    await page.setViewportSize(viewport)
    await restoreMonthly(page)
    await stubMonthlyHistory(page, monthlyHistoryFixture())
    await openRoute(page, '/')
    await expect(card(page)).toBeVisible()
    const measured = await layout(page)
    expect([measured.pageOverflow, measured.calendarOverflow, measured.squeezed]).toEqual([
      false,
      false,
      [],
    ])
    if (viewport.width <= 768) expect(measured.touchHeight).toBeGreaterThanOrEqual(44)
    if (viewport.width === 375) expect(measured.height).toBeLessThanOrEqual(viewport.height)
    await settleAndFreeze(page)
    await expect(card(page)).toHaveScreenshot(`monthly-profit-${viewport.width}.png`)
  })
}

test('monthly profit fits unusually large signed percentages', async ({ page }) => {
  await restoreMonthly(page)
  await stubMonthlyHistory(page, [
    createPortfolioSummaryDto({ date: '2024-12-31', totalProfit: 0, totalValue: 100 }),
    createPortfolioSummaryDto({ date: '2025-01-31', totalProfit: 1000000, totalValue: 1000100 }),
    createPortfolioSummaryDto({ date: '2025-02-28', totalProfit: -1000000, totalValue: -1999900 }),
  ])
  await openRoute(page, '/')
  await expect(card(page)).toBeVisible()
  const measured = await layout(page)
  expect([measured.pageOverflow, measured.calendarOverflow, measured.squeezed]).toEqual([
    false,
    false,
    [],
  ])
})

test('monthly loading keeps Daily calendar and table together until MAX arrives', async ({
  page,
}) => {
  let release: () => void = () => {}
  const held = new Promise<void>(resolve => {
    release = resolve
  })
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), async route => {
    if (new URL(route.request().url()).searchParams.get('range') !== 'MAX') return route.fallback()
    await held
    await route.fulfill({ json: SERIES_RESPONSE })
  })
  await openRoute(page, '/')
  await interval(page, 'Monthly').click()
  await expect(interval(page, 'Monthly')).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.daily-profit-calendar')).toBeVisible()
  await expect(page.locator('thead')).toContainText('24h Change')
  await expect(page.locator('.summary-history')).toHaveAttribute('aria-busy', 'true')
  await expect(page.getByText('Loading months', { exact: true })).toBeVisible()
  release()
  await expect(card(page)).toBeVisible()
  await expect(page.locator('thead')).toContainText('Month Change')
  await expect(interval(page, 'Monthly')).toBeFocused()
  await expect(page.locator('.summary-interval-announcement')).toHaveText(
    'Monthly profit Jun – Dec 2025'
  )
})

test('monthly stored history failure returns to Daily after one retry and a new press recovers', async ({
  page,
}) => {
  await restoreMonthly(page)
  await page.addInitScript(
    key => localStorage.setItem(key, '["LHV"]'),
    STORAGE_KEYS.SELECTED_SUMMARY_PLATFORMS
  )
  let attempts = 0
  await page.route(apiRoute(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES), route => {
    if (new URL(route.request().url()).searchParams.get('range') !== 'MAX') return route.fallback()
    attempts += 1
    return attempts <= 2
      ? route.fulfill({ status: 503, json: { message: 'Unavailable' } })
      : route.fulfill({ json: SERIES_RESPONSE })
  })
  await page.goto('/')
  await expect(interval(page, 'Daily')).toHaveAttribute('aria-pressed', 'true')
  await expect(
    page.getByRole('alert').filter({
      hasText: 'Could not load the monthly history. Press Monthly to try again.',
    })
  ).toHaveCount(1)
  expect(attempts).toBe(2)
  await interval(page, 'Monthly').click()
  await expect(card(page)).toBeVisible()
  expect(attempts).toBe(3)
})
