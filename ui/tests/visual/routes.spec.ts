import { test, expect } from '@playwright/test'
import { freeze, openRoute } from './settle'
import { type RouteStub } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubCalculator } from './calculator-fixture'
import { stubDiversification } from './diversification-fixture'
import { stubEnums } from './enums-fixture'
import { stubEtfBreakdown } from './etf-fixture'
import { stubInstruments } from './instruments-fixture'
import { stubMonitoring } from './monitoring-fixture'
import { stubPoff } from './poff-fixture'
import { stubRadio, stubRadioOnAir, stubRadioStill } from './radio-fixture'
import { stubPortfolioSummary } from './summary-fixture'
import { stubTransactions } from './transactions-fixture'

const MAX_CAPTURE_HEIGHT = 12000
const MIN_TARGET_PX = 24

const ROUTES: { path: string; name: string; stub: RouteStub }[] = [
  { path: '/', name: 'summary', stub: stubPortfolioSummary },
  { path: '/transactions', name: 'transactions', stub: stubTransactions },
  { path: '/instruments', name: 'instruments', stub: stubInstruments },
  { path: '/etf-breakdown', name: 'etf-breakdown', stub: stubEtfBreakdown },
  { path: '/diversification', name: 'diversification', stub: stubDiversification },
  { path: '/calculator', name: 'calculator', stub: stubCalculator },
  { path: '/monitoring', name: 'monitoring', stub: stubMonitoring },
  { path: '/radio', name: 'radio', stub: stubRadioOnAir },
  { path: '/radio/shows', name: 'radio-shows', stub: stubRadioOnAir },
  { path: '/radio/catch-up', name: 'radio-catch-up', stub: stubRadio },
  { path: '/radio/catch-up?station=aripaev', name: 'radio-catch-up-aripaev', stub: stubRadio },
  { path: '/radio/2026-10-17', name: 'radio-listen', stub: stubRadio },
  { path: '/radio/2026-10-24', name: 'radio-find-cuts', stub: stubRadioStill },
  { path: '/radio/2026-10-23', name: 'radio-render-cuts', stub: stubRadioStill },
  { path: '/radio/2026-10-25', name: 'radio-wait-cuts', stub: stubRadio },
  { path: '/radio/2026-10-26', name: 'radio-failed-cuts', stub: stubRadio },
  { path: '/poff', name: 'poff', stub: stubPoff },
  { path: '/poff/films', name: 'poff-films', stub: stubPoff },
  { path: '/poff/film/american-doctor', name: 'poff-film', stub: stubPoff },
  { path: '/poff?film=american-doctor', name: 'poff-panel', stub: stubPoff },
  { path: '/poff?q=wil', name: 'poff-search', stub: stubPoff },
]

const BREAKDOWN_ROUTES = ROUTES.filter(route =>
  ['etf-breakdown', 'diversification'].includes(route.name)
)

test.beforeEach(async ({ page }) => {
  await stubBuildInfo(page)
  await stubEnums(page)
})

for (const route of ROUTES) {
  test(`route ${route.name}`, async ({ page }) => {
    await route.stub(page)
    await openRoute(page, route.path)
    await freeze(page)
    const width = await page.evaluate(() => document.documentElement.scrollWidth)
    await expect(page).toHaveScreenshot(`route-${route.name}.png`, {
      fullPage: true,
      clip: { x: 0, y: 0, width, height: MAX_CAPTURE_HEIGHT },
      mask: [page.locator('canvas')],
    })
  })
}

for (const route of BREAKDOWN_ROUTES) {
  test(`${route.name} lays out every breakdown control without squeezing it`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await route.stub(page)
    await openRoute(page, route.path)
    const layout = await page.evaluate(minTarget => {
      const labels = Array.from(document.querySelectorAll('.breakdown-tab, .compare-toggle'))
      const buttons = Array.from(document.querySelectorAll('.view-btn'))
      const squeezed = [
        ...labels
          .filter(label => label.scrollWidth > label.clientWidth)
          .map(label => label.textContent?.trim()),
        ...buttons
          .filter(button => button.getBoundingClientRect().width < minTarget)
          .map(button => button.getAttribute('aria-label')),
      ]
      return { controls: labels.length + buttons.length, squeezed }
    }, MIN_TARGET_PX)
    expect(layout).toEqual({ controls: 7, squeezed: [] })
  })
}

for (const viewport of [
  { width: 375, height: 667 },
  { width: 390, height: 844 },
]) {
  test(`radio catch-up gives every row control a fingertip target at ${viewport.width}px`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await page.setViewportSize(viewport)
    await stubRadio(page)
    await openRoute(page, '/radio/catch-up')
    const small = await page
      .getByTestId('radio-airing')
      .getByRole('button')
      .evaluateAll(buttons =>
        buttons
          .map(button => button.getBoundingClientRect())
          .filter(box => box.width < 44 || box.height < 44)
          .map(box => `${box.width}x${box.height}`)
      )
    expect(small).toEqual([])
  })
}

test('radio recordings table fits its card at 768px', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'tablet')
  await stubRadioOnAir(page)
  await openRoute(page, '/radio')
  const overflow = await page
    .locator('.desktop-table-wrapper')
    .evaluate(table => table.scrollWidth - table.clientWidth)
  expect(overflow).toBe(0)
})

test('radio failed cut search gives its details a fingertip target on a phone', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  await stubRadio(page)
  await openRoute(page, '/radio/2026-10-26')
  const box = await page.getByText('Details', { exact: true }).boundingBox()
  expect(box!.height).toBeGreaterThanOrEqual(44)
})

for (const viewport of [
  { width: 375, height: 667 },
  { width: 390, height: 844 },
]) {
  test(`radio shows tick from anywhere on a 44px row at ${viewport.width}px`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await page.setViewportSize(viewport)
    await stubRadioOnAir(page)
    await openRoute(page, '/radio/shows')
    const missed = await page.getByTestId('radio-programme-row').evaluateAll(
      labels =>
        labels.filter(label => {
          label.scrollIntoView({ block: 'center' })
          const row = label.parentElement!.getBoundingClientRect()
          const hit = document.elementFromPoint(row.left + 2, row.top + 2)?.closest('label')
          return hit !== label || label.getBoundingClientRect().height < 44
        }).length
    )
    const scroll = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)
    expect({ missed, scroll }).toEqual({ missed: 0, scroll: 0 })
  })
}

test('radio shows tables stay clear of the side cards at 1024px', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await page.setViewportSize({ width: 1024, height: 900 })
  await stubRadioOnAir(page)
  await openRoute(page, '/radio/shows')
  const rail = await page
    .getByRole('complementary', { name: 'Recorders and broadcast' })
    .boundingBox()
  const edges = await page
    .locator('.desktop-table-wrapper')
    .evaluateAll(wrappers => wrappers.map(wrapper => wrapper.getBoundingClientRect().right))
  expect(Math.max(...edges)).toBeLessThanOrEqual(rail!.x)
})

test.describe('radio transcript on a phone', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await stubRadio(page)
    await openRoute(page, '/radio/2026-10-17?t=0')
  })

  test("runs the screen's width", async ({ page }) => {
    const card = await page.getByTestId('radio-transcript').locator('section.card').boundingBox()
    const text = await page
      .getByTestId('radio-text-line')
      .first()
      .locator('p')
      .first()
      .boundingBox()
    expect({ x: card!.x, width: card!.width, wide: text!.width >= 300 }).toEqual({
      x: 0,
      width: 390,
      wide: true,
    })
  })

  test('lands a tap in the middle of a sentence on its play button', async ({ page }) => {
    const missed = await page.getByTestId('radio-text-line').evaluateAll(
      lines =>
        lines.filter(line => {
          line.scrollIntoView({ block: 'center' })
          const text = line.querySelector('p')!.getBoundingClientRect()
          const hit = document.elementFromPoint(text.x + text.width / 2, text.y + text.height / 2)
          return (
            hit?.closest('button')?.closest('li') !== line ||
            line.getBoundingClientRect().height < 44
          )
        }).length
    )
    expect(missed).toBe(0)
  })

  test('starts a topic title on its own line', async ({ page }) => {
    const spans = page.getByTestId('radio-section-heading').first().locator('span')
    const time = await spans.first().boundingBox()
    const title = await spans.nth(1).boundingBox()
    expect(title!.y).toBeGreaterThanOrEqual(time!.y + time!.height)
  })

  test('draws the version picker without an outline', async ({ page }) => {
    const picker = page.getByRole('combobox', { name: 'Version' })
    const look = await picker.evaluate(select => {
      const style = getComputedStyle(select)
      return { border: style.borderTopColor, fill: style.backgroundColor }
    })
    const box = await picker.boundingBox()
    expect({ ...look, tall: box!.height >= 44 }).toEqual({
      border: 'rgba(0, 0, 0, 0)',
      fill: 'rgba(0, 0, 0, 0)',
      tall: true,
    })
  })
})
