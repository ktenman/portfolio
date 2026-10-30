import { test, expect, type Locator, type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import { STORAGE_KEYS } from '../../constants/storage-keys'
import { type TransactionsWithSummaryDto } from '../../models/generated/domain-models'
import { freeze, openRoute, settleAndFreeze, waitForBoxHeightToSettle } from './settle'
import { apiRoute, type RouteStub } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubCalculator } from './calculator-fixture'
import { stubDiversification, stubDiversificationWithLongFundName } from './diversification-fixture'
import { stubEnums } from './enums-fixture'
import {
  stubEtfBreakdown,
  stubEtfBreakdownWithLogoCandidates,
  stubEtfBreakdownWithTuleva,
} from './etf-fixture'
import { stubInstruments } from './instruments-fixture'
import {
  stubRadio,
  stubRadioLongText,
  stubRadioPlannedOnly,
  stubRadioPlannedSoon,
} from './radio-fixture'
import { stubPortfolioSummary } from './summary-fixture'
import { stubTransactions } from './transactions-fixture'
import { stubWindows } from './windows-fixture'

const MODAL_CONTENT_TIMEOUT_MS = 60000
const STATE_TIMEOUT_MS = 30000
const TOAST_MODULE_PATH = '/composables/use-toast.ts'
const LOADING_HOLD_MS = 20000
const SUBPIXEL_PX = 0.5
const SMALL_PHONE = { width: 320, height: 568 }
const WHEEL_DELTA_PX = 300
const WHEEL_SETTLE_MS = 300

const EMPTY_TRANSACTIONS: TransactionsWithSummaryDto = {
  transactions: [],
  summary: {
    totalRealizedProfit: 0,
    totalUnrealizedProfit: 0,
    totalProfit: 0,
    netInvested: 0,
  },
}

const OPEN_MODAL = 'dialog.modal[open]'
const OPEN_REPORT = '.report-popover:popover-open'
const QUICK_DATES_TOGGLE = '[data-testid="quickDatesToggle"]'
const PICKER_MARGIN_PX = 8

async function waitForModal(page: Page, title: string | RegExp): Promise<void> {
  const content = page.locator(`${OPEN_MODAL} .modal-content`)
  await expect(content).toBeVisible()
  await expect(page.locator(`${OPEN_MODAL} .modal-title`)).toHaveText(title)
  await expect(
    page.locator(`${OPEN_MODAL} .spinner-border, ${OPEN_MODAL} .loading-spinner`)
  ).toHaveCount(0, { timeout: MODAL_CONTENT_TIMEOUT_MS })
  await waitForBoxHeightToSettle(page, content)
}

function visibleTotalsTriggers(page: Page): Locator {
  return page.locator('.xirr-trigger, .xirr-trigger-mobile').filter({ visible: true })
}

const stubInstrumentsWithWindows: RouteStub = async page => {
  await stubInstruments(page)
  await stubWindows(page)
}

async function openInstrumentModal(page: Page): Promise<void> {
  await page.evaluate(() => {
    document.querySelector<HTMLDialogElement>('#instrumentModal')?.showModal()
  })
}

async function openQuickDates(page: Page): Promise<void> {
  await stubTransactions(page)
  await openRoute(page, '/transactions')
  await expect(page.locator(QUICK_DATES_TOGGLE)).toBeVisible()
}

async function openSummaryOn(page: Page, range: string): Promise<void> {
  await page.addInitScript(({ key, value }) => localStorage.setItem(key, value), {
    key: STORAGE_KEYS.SUMMARY_CHART_RANGE,
    value: range,
  })
  await stubPortfolioSummary(page)
  await openRoute(page, '/')
}

async function openTulevaReport(page: Page): Promise<void> {
  await page.addInitScript(({ key, value }) => localStorage.setItem(key, value), {
    key: STORAGE_KEYS.ETF_FILTERS_OPEN,
    value: 'true',
  })
  await stubEtfBreakdownWithTuleva(page)
  await openRoute(page, '/etf-breakdown')
  await page.click('.report-btn')
  await expect(page.locator(OPEN_REPORT)).toBeVisible()
}

const MODALS: {
  name: string
  route: string
  title: string | RegExp
  open: (page: Page) => Promise<void>
  stub: RouteStub
}[] = [
  {
    name: 'instrument',
    route: '/instruments',
    title: 'Add New Instrument',
    open: openInstrumentModal,
    stub: stubInstruments,
  },
  {
    name: 'xirr-windows',
    route: '/instruments',
    title: 'Annualized return over time',
    open: page => visibleTotalsTriggers(page).nth(0).click(),
    stub: stubInstrumentsWithWindows,
  },
  {
    name: 'annual-windows',
    route: '/instruments',
    title: 'Buy-and-hold annualized return',
    open: page => visibleTotalsTriggers(page).nth(1).click(),
    stub: stubInstrumentsWithWindows,
  },
  {
    name: 'logo-replacement',
    route: '/etf-breakdown',
    title: /^Replace Logo: /,
    open: page => page.locator('.company-logo.clickable').filter({ visible: true }).first().click(),
    stub: stubEtfBreakdown,
  },
  {
    name: 'logo-replacement-candidates',
    route: '/etf-breakdown',
    title: /^Replace Logo: /,
    open: page => page.locator('.company-logo.clickable').filter({ visible: true }).first().click(),
    stub: stubEtfBreakdownWithLogoCandidates,
  },
  {
    name: 'config-export',
    route: '/diversification',
    title: 'Export Configuration',
    open: page => page.click('button[aria-label="Export"]'),
    stub: stubDiversification,
  },
  {
    name: 'config-import',
    route: '/diversification',
    title: 'Import Configuration',
    open: page => page.click('button[aria-label="Import"]'),
    stub: stubDiversification,
  },
]

test.describe('modals', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name === 'tablet')
  })

  test.beforeEach(async ({ page }) => {
    await stubBuildInfo(page)
    await stubEnums(page)
  })

  for (const modal of MODALS) {
    test(`modal ${modal.name}`, async ({ page }) => {
      await modal.stub(page)
      await openRoute(page, modal.route)
      await modal.open(page)
      await waitForModal(page, modal.title)
      await settleAndFreeze(page)
      await expect(page).toHaveScreenshot(`modal-${modal.name}.png`)
      await page.locator(`${OPEN_MODAL} .btn-close`).click()
      await expect(page.locator(OPEN_MODAL)).toHaveCount(0)
    })
  }

  test('modal confirm', async ({ page }) => {
    await stubPortfolioSummary(page)
    await page.route('**/api/portfolio-summary/recalculate**', route => route.abort())
    await openRoute(page, '/')
    await page.click('button:has-text("Recalculate Data")')
    await waitForModal(page, 'Recalculate Portfolio Data')
    await settleAndFreeze(page)
    await expect(page).toHaveScreenshot('modal-confirm.png')
    await page.click('[data-testid="confirmDialogCancelButton"]')
    await expect(page.locator(OPEN_MODAL)).toHaveCount(0)
  })

  test('modal escape closes a dismissable modal', async ({ page }) => {
    await stubInstrumentsWithWindows(page)
    await openRoute(page, '/instruments')
    await visibleTotalsTriggers(page).nth(0).click()
    await waitForModal(page, 'Annualized return over time')

    await page.keyboard.press('Escape')

    await expect(page.locator(OPEN_MODAL)).toHaveCount(0)
  })

  test('modal escape dont close the confirm dialog', async ({ page }) => {
    await stubPortfolioSummary(page)
    await page.route('**/api/portfolio-summary/recalculate**', route => route.abort())
    await openRoute(page, '/')
    await page.click('button:has-text("Recalculate Data")')
    await waitForModal(page, 'Recalculate Portfolio Data')

    await page.keyboard.press('Escape')

    await expect(page.locator(OPEN_MODAL)).toHaveCount(1)
    await page.click('[data-testid="confirmDialogCancelButton"]')
  })
})

test('summary chart marks the high and low of the range', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
  await openRoute(page, '/')
  await freeze(page)
  await expect(page.getByTestId('summary-chart')).toHaveScreenshot('summary-range-extremes.png')
})

test('summary chart marks the high and low of total profit when total value is hidden', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
  await page.addInitScript(({ key, value }) => localStorage.setItem(key, value), {
    key: STORAGE_KEYS.SUMMARY_CHART_HIDDEN,
    value: JSON.stringify(['totalValue', 'xirrAnnualReturn', 'earningsPerMonth']),
  })
  await openRoute(page, '/')
  await freeze(page)
  await expect(page.getByTestId('summary-chart')).toHaveScreenshot(
    'summary-range-extremes-profit.png'
  )
})

test('an off-shortlist period shows on the ninth cell of the period row', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await openSummaryOn(page, '6Y')
  await freeze(page)
  await expect(page.getByTestId('chart-controls')).toHaveScreenshot(
    'summary-period-off-shortlist.png'
  )
})

test('the More popover opens below the period row', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await openSummaryOn(page, '6Y')
  await page.getByRole('button', { name: '6Y', exact: true }).click()
  await settleAndFreeze(page)
  await page.evaluate(() => window.scrollTo(0, 0))
  const row = (await page.getByTestId('chart-controls').boundingBox())!
  const picker = (await page.locator('#period-picker').boundingBox())!
  const top = row.y - PICKER_MARGIN_PX
  await expect(page).toHaveScreenshot('summary-period-picker.png', {
    fullPage: true,
    clip: {
      x: 0,
      y: top,
      width: page.viewportSize()!.width,
      height: Math.max(row.y + row.height, picker.y + picker.height) + PICKER_MARGIN_PX - top,
    },
    mask: [page.locator('canvas')],
  })
})

test('a radio schedule row opens its recording wherever it is clicked', async ({ page }) => {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await openRoute(page, '/radio/catch-up')
  const link = page.getByRole('link', { name: 'Keskpäevatund 12:00' })
  await page
    .getByTestId('radio-airing')
    .filter({ has: link })
    .first()
    .click({ position: { x: 4, y: 4 } })
  await expect(page).toHaveURL(/\/radio\/2026-10-17$/)
})

async function labelAt(page: Page, x: number, y: number): Promise<string | null> {
  return page.evaluate(
    point =>
      document
        .elementFromPoint(point.x, point.y)
        ?.closest('a, button')
        ?.getAttribute('aria-label') ?? null,
    { x, y }
  )
}

test('the play button of a radio schedule row stays above the link to its recording', async ({
  page,
}) => {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await openRoute(page, '/radio/catch-up')
  const play = page.getByRole('button', { name: 'Listen: Keskpäevatund 12:00' }).first()
  await play.scrollIntoViewIfNeeded()
  const box = (await play.boundingBox())!
  expect(await labelAt(page, box.x + box.width / 2, box.y + box.height / 2)).toBe(
    'Listen: Keskpäevatund 12:00'
  )
})

test('the radio day bar leaves the room after a recording name unlinked', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await openRoute(page, '/radio/catch-up')
  const link = page.getByTestId('radio-day-link').filter({ hasText: 'Huvitaja' })
  await link.scrollIntoViewIfNeeded()
  const room = (await link.locator('span').first().boundingBox())!
  expect(await labelAt(page, room.x + room.width - 2, room.y + room.height / 2)).toBe(null)
})

test('the radio day bar links a recording name that runs past its block', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await openRoute(page, '/radio/catch-up')
  const link = page.getByTestId('radio-day-link').filter({ hasText: 'Huvitaja' })
  await link.scrollIntoViewIfNeeded()
  const block = (await link.boundingBox())!
  const name = (await link.getByText('Huvitaja').boundingBox())!
  expect(await labelAt(page, block.x + block.width + 10, name.y + name.height / 2)).toBe(
    'Huvitaja, 13:10–, recording now'
  )
})

test('the radio day bar stops a recording name at the next planned airing', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadioPlannedSoon(page)
  await openRoute(page, '/radio/catch-up')
  const room = (await page.getByTestId('radio-day-link').locator('span').first().boundingBox())!
  const plan = (await page.getByTestId('radio-day-planned').boundingBox())!
  expect(room.x + room.width).toBeLessThanOrEqual(plan.x + SUBPIXEL_PX)
})

test('the radio day bar sums the time planned on a day with nothing recorded', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadioPlannedOnly(page)
  await openRoute(page, '/radio/catch-up')
  await freeze(page)
  await expect(
    page.getByTestId('radio-day-summary').locator('xpath=ancestor::section[1]')
  ).toHaveScreenshot('radio-day-planned.png')
})

test('the radio player stays in view while the page scrolls', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await page.setViewportSize({ width: page.viewportSize()!.width, height: 500 })
  await openRoute(page, '/radio/2026-10-17')
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
  await expect(page.getByTestId('radio-play')).toBeInViewport()
  await settleAndFreeze(page)
  await expect(page).toHaveScreenshot('radio-player-scrolled.png')
})

test('the radio rail stays in view while the text scrolls', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadioLongText(page)
  await page.setViewportSize({ width: 1280, height: 800 })
  await openRoute(page, '/radio/2026-10-17')
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight / 2))
  await expect(page.getByTestId('radio-details')).toBeInViewport()
  await settleAndFreeze(page)
  await expect(page).toHaveScreenshot('radio-rail-pinned.png')
})

test('the raw radio recording shows the cut decisions', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await openRoute(page, '/radio/2026-10-17')
  await page.getByRole('button', { name: /^Raw/ }).click()
  await expect(page.getByTestId('radio-cut').first()).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, 0))
  await settleAndFreeze(page)
  await expect(page).toHaveScreenshot('radio-raw.png', { fullPage: true })
})

test('the radio player keeps every control at least 44 px tall on a phone', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await openRoute(page, '/radio/2026-10-17')
  const card = page.getByTestId('radio-player')
  const targets = [
    card.getByRole('slider', { name: 'Position in the recording' }),
    card.getByRole('combobox', { name: 'Version' }),
    ...(await card.getByRole('button').all()),
  ]
  const heights = await Promise.all(targets.map(async t => (await t.boundingBox())!.height))
  expect(Math.min(...heights)).toBeGreaterThanOrEqual(44)
})

test('the radio player sticks below the navbar', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadioLongText(page)
  await page.setViewportSize({ width: 1440, height: 800 })
  await openRoute(page, '/radio/2026-10-17')
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight / 2))
  const navbar = (await page.locator('nav.navbar').boundingBox())!
  const card = (await page.getByTestId('radio-player').boundingBox())!
  expect(card.y).toBe(navbar.y + navbar.height)
})

test('the playing radio line stays clear of the player', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadioLongText(page)
  await page.setViewportSize({ width: 1440, height: 600 })
  await openRoute(page, '/radio/2026-10-17')
  await page.getByTestId('radio-text-line').nth(40).getByRole('button').dispatchEvent('click')
  const line = page.locator('[data-testid="radio-text-line"][aria-current="true"]')
  await expect(line).toBeInViewport()
  const card = (await page.getByTestId('radio-player').boundingBox())!
  expect((await line.boundingBox())!.y).toBeGreaterThanOrEqual(card.y + card.height)
})

async function playRadio(page: Page, path = '/'): Promise<void> {
  await page.addInitScript(() => {
    HTMLMediaElement.prototype.play = function () {
      this.dispatchEvent(new Event('play'))
      return Promise.resolve()
    }
  })
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
  await stubCalculator(page)
  await stubRadio(page)
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/[^/]+/position`), route =>
    route.fulfill({ json: { seconds: 1666 } })
  )
  await openRoute(page, '/radio/2026-10-17')
  await page.getByTestId('radio-play').click()
  await page.locator(`.nav-link[href="${path}"]`).click()
}

async function dragRadioCard(page: Page): Promise<void> {
  const title = page.getByTestId('radio-mini-player').locator('p').first()
  const box = (await title.boundingBox())!
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 - 40, box.y + box.height / 2 - 40, { steps: 4 })
  await page.mouse.up()
}

test('the radio mini player floats over the page', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await playRadio(page)
  await expect(page.getByTestId('radio-mini-player')).toBeVisible()
  await settleAndFreeze(page)
  await expect(page).toHaveScreenshot('radio-mini-player.png')
})

test('the radio keeps its audio after leaving the recording', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await playRadio(page)
  await expect(page.getByTestId('radio-mini-player')).toBeVisible()
  await expect(page.locator('audio')).toHaveAttribute('src', /\.(m4a|mp3)/)
})

test('the radio mini player opens its episode without loading the audio again', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await playRadio(page)
  const card = page.getByTestId('radio-mini-player')
  await expect(card).toBeVisible()
  await page.locator('audio').evaluate(audio => {
    audio.addEventListener('loadstart', () => audio.setAttribute('data-loaded-again', ''))
  })
  await card.getByRole('link', { name: 'Open the episode' }).click()
  await expect(page.getByTestId('radio-player')).toBeVisible()
  await expect(card).toHaveCount(0)
  await expect(page.locator('audio')).not.toHaveAttribute('data-loaded-again')
})

test('the radio mini player waits for something to play', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPortfolioSummary(page)
  await stubRadio(page)
  await openRoute(page, '/radio/2026-10-17')
  await page.locator('.nav-link[href="/"]').click()
  await expect(page.locator('audio')).not.toHaveAttribute('src')
  await expect(page.getByTestId('radio-mini-player')).toHaveCount(0)
})

test('a dragged radio mini player stays where it was left after a reload', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await playRadio(page)
  const card = page.getByTestId('radio-mini-player')
  const { x, y } = (await card.boundingBox())!
  await dragRadioCard(page)
  await openRoute(page, '/radio/2026-10-17')
  await page.getByTestId('radio-play').click()
  await page.locator('.nav-link[href="/"]').click()
  await expect(card).toBeVisible()
  await expect.poll(() => card.boundingBox()).toMatchObject({ x: x - 40, y: y - 40 })
})

test('a dragged radio mini player comes back into view when the window shrinks', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await playRadio(page)
  await dragRadioCard(page)
  await page.setViewportSize({ width: 800, height: 500 })
  await expect(page.getByTestId('radio-mini-player')).toBeInViewport({ ratio: 1 })
})

test('the radio mini player rests at the top of a window shorter than itself', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await playRadio(page)
  await dragRadioCard(page)
  await page.setViewportSize({ width: 800, height: 100 })
  await expect
    .poll(async () => (await page.getByTestId('radio-mini-player').boundingBox())!.y)
    .toBe(0)
})

test('the radio mini player sits above the Reset bar', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  await playRadio(page, '/calculator')
  const card = (await page.getByTestId('radio-mini-player').boundingBox())!
  const bar = (await page.locator('.calculator-buttons-mobile').boundingBox())!
  expect(card.y + card.height).toBeLessThanOrEqual(bar.y)
})

for (const path of ['/', '/calculator']) {
  test(`the footer of ${path} scrolls clear of the radio mini player`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await playRadio(page, path)
    await expect
      .poll(async () => {
        await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
        const footer = (await page.locator('footer').boundingBox())!
        const card = (await page.getByTestId('radio-mini-player').boundingBox())!
        return footer.y + footer.height <= card.y
      })
      .toBe(true)
  })
}

test.describe('desktop states', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop')
  })

  test.beforeEach(async ({ page }) => {
    await stubBuildInfo(page)
    await stubEnums(page)
  })

  test('the spoken word is marked by a tint', async ({ page }) => {
    await stubRadio(page)
    await openRoute(page, '/radio/2026-10-17')
    await page.getByTestId('radio-text-line').first().getByRole('button').click()
    await page.mouse.move(0, 0)
    await settleAndFreeze(page)
    await expect(
      page.locator('[data-testid="radio-text-line"][aria-current="true"]')
    ).toHaveScreenshot('radio-word-now.png')
  })

  test('selecting a quick date applies it', async ({ page }) => {
    await openQuickDates(page)

    await page.selectOption(QUICK_DATES_TOGGLE, 'Last 7 Days')

    await expect(page.locator(QUICK_DATES_TOGGLE)).toHaveValue('Last 7 Days')
    await expect(page.locator('#fromDate')).not.toHaveValue('')
  })

  test('clearing the quick date selection clears the dates', async ({ page }) => {
    await openQuickDates(page)
    await page.selectOption(QUICK_DATES_TOGGLE, 'Last 7 Days')

    await page.selectOption(QUICK_DATES_TOGGLE, '')

    await expect(page.locator('#fromDate')).toHaveValue('')
  })

  test('selecting the sp500 mode shows the benchmark comparison', async ({ page }) => {
    await stubPortfolioSummary(page)
    await openRoute(page, '/')
    await page.getByRole('button', { name: 'S&P 500', exact: true }).click()
    await settleAndFreeze(page)
    await expect(page).toHaveScreenshot('summary-performance-mode.png')
  })

  test('the Tuleva chip opens its monthly report', async ({ page }) => {
    await openTulevaReport(page)
    await settleAndFreeze(page)
    await expect(page).toHaveScreenshot('etf-breakdown-report.png')
  })

  test('pressing Escape closes the Tuleva report', async ({ page }) => {
    await openTulevaReport(page)

    await page.keyboard.press('Escape')

    await expect(page.locator(OPEN_REPORT)).toHaveCount(0)
  })

  test('clicking outside the Tuleva report closes it', async ({ page }) => {
    await openTulevaReport(page)

    await page.click('h2:text-is("ETF Breakdown")')

    await expect(page.locator(OPEN_REPORT)).toHaveCount(0)
  })

  test('the close button closes the Tuleva report', async ({ page }) => {
    await openTulevaReport(page)

    await page.click(`${OPEN_REPORT} .btn-close`)

    await expect(page.locator(OPEN_REPORT)).toHaveCount(0)
  })

  test('picking an earlier month keeps the Tuleva report open on that month', async ({ page }) => {
    await openTulevaReport(page)

    await page.click('#fund-report-month')
    await page.click('#fund-report-month option:text-is("31.07.2026")')

    await expect(page.locator(`${OPEN_REPORT} tr:has-text("CCF Developed World") .num`)).toHaveText(
      '29.39%'
    )
  })

  test('hovering a long industry name keeps the donut label inside the ring', async ({ page }) => {
    await stubEtfBreakdown(page)
    await openRoute(page, '/etf-breakdown')
    await page.click('.breakdown-tab:text-is("Industries")')
    await settleAndFreeze(page)
    await page.hover('.legend-item:has-text("Semiconductors & Semiconductor Equipment")')
    await expect(page).toHaveScreenshot('etf-breakdown-industry-hover.png', { fullPage: true })
  })

  test('hovering a holding logo shows it can be replaced', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop')
    await stubEtfBreakdown(page)
    await openRoute(page, '/etf-breakdown')
    await settleAndFreeze(page)
    const cell = page.locator('.ticker-cell').filter({ visible: true }).first()
    await cell.locator('.logo-container').hover()
    await expect(cell).toHaveScreenshot('etf-breakdown-logo-hover.png')
  })

  test('selecting both benchmarks overlays three lines', async ({ page }) => {
    await stubPortfolioSummary(page)
    await openRoute(page, '/')
    await page.getByRole('button', { name: 'S&P 500', exact: true }).click()
    await page.getByRole('button', { name: 'VWCE', exact: true }).click()
    await settleAndFreeze(page)
    await expect(page).toHaveScreenshot('summary-benchmark-both.png')
  })

  test('going back to an earlier platform filter keeps the picked day', async ({ page }) => {
    await stubPortfolioSummary(page)
    await openRoute(page, '/')
    const lhv = page.locator('.platform-buttons .platform-btn:text-is("LHV")')
    const selectedDay = page.locator('.calendar-grid .selected')
    await lhv.click()
    await lhv.click()
    await page.getByRole('button', { name: 'Previous day', exact: true }).click()
    const pickedDay = (await selectedDay.getAttribute('aria-label'))!

    await lhv.click()

    await expect(selectedDay).toHaveAttribute('aria-label', pickedDay)
  })

  test('the calendar grid draws its keyboard focus on the selected day', async ({ page }) => {
    await stubPortfolioSummary(page)
    await openRoute(page, '/')
    const grid = page.locator('.calendar-grid[role="application"]')

    await grid.focus()
    await page.keyboard.press('ArrowUp')

    await expect(grid.locator('.selected')).toHaveCSS('outline-style', 'solid')
    await expect(grid.locator('.selected')).toHaveCSS('outline-width', '2px')
  })

  for (const variant of ['success', 'error', 'info', 'warning'] as const) {
    test(`toast ${variant}`, async ({ page }) => {
      await stubPortfolioSummary(page)
      await openRoute(page, '/')
      await freeze(page)
      await page.evaluate(
        async ({ modulePath, kind }) => {
          const toasts = await import(modulePath)
          toasts.useToast()[kind](`Baseline ${kind} message`)
        },
        { modulePath: TOAST_MODULE_PATH, kind: variant }
      )
      await expect(page.locator('.toast.show')).toBeVisible()
      await expect(page).toHaveScreenshot(`toast-${variant}.png`)
    })
  }

  test('state loading skeleton', async ({ page }) => {
    await page.route('**/api/portfolio-summary/**', async route => {
      await new Promise(resolve => setTimeout(resolve, LOADING_HOLD_MS))
      await route.abort().catch(() => undefined)
    })
    await page.goto('/')
    await settleAndFreeze(page)
    await expect(page.locator('.skeleton').first()).toBeVisible()
    await expect(page).toHaveScreenshot('state-loading.png')
  })

  test('state spinner', async ({ page }) => {
    await page.route(apiRoute(API_ENDPOINTS.ETF_BREAKDOWN), async route => {
      await new Promise(resolve => setTimeout(resolve, LOADING_HOLD_MS))
      await route.abort().catch(() => undefined)
    })
    await page.goto('/etf-breakdown')
    await settleAndFreeze(page)
    await expect(page.locator('.loading-spinner').first()).toBeVisible()
    await expect(page).toHaveScreenshot('state-spinner.png')
  })

  test('state empty table', async ({ page }) => {
    await page.route(apiRoute(API_ENDPOINTS.TRANSACTIONS), route =>
      route.fulfill({ json: EMPTY_TRANSACTIONS })
    )
    await openRoute(page, '/transactions')
    await freeze(page)
    await expect(page.locator('.alert-info')).toBeVisible()
    await expect(page).toHaveScreenshot('state-empty.png')
  })

  test('state error alert', async ({ page }) => {
    await page.route(apiRoute(API_ENDPOINTS.INSTRUMENTS), route =>
      route.fulfill({ status: 500, body: '' })
    )
    await page.goto('/instruments')
    const alert = page.locator('.alert-danger')
    await expect(alert).toBeVisible({ timeout: STATE_TIMEOUT_MS })
    await expect(alert).toHaveScreenshot('state-error.png', {
      timeout: STATE_TIMEOUT_MS,
    })
  })
})

test.describe('mobile states', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
  })

  test.beforeEach(async ({ page }) => {
    await stubBuildInfo(page)
    await stubEnums(page)
  })

  test('the Tuleva report fits inside a small phone screen', async ({ page }) => {
    await page.setViewportSize(SMALL_PHONE)
    await openTulevaReport(page)

    const box = await page.locator(OPEN_REPORT).boundingBox()

    expect([
      box!.x >= 0,
      box!.y >= 0,
      box!.x + box!.width <= SMALL_PHONE.width,
      box!.y + box!.height <= SMALL_PHONE.height,
    ]).toEqual([true, true, true, true])
  })

  test('the Tuleva report keeps its allocation bar on a small phone screen', async ({ page }) => {
    await page.setViewportSize(SMALL_PHONE)
    await openTulevaReport(page)

    await expect(page.locator(`${OPEN_REPORT} .fund-stack`)).toBeVisible()
  })

  test('the page stays put behind the open Tuleva report', async ({ page }) => {
    await openTulevaReport(page)

    await page.locator('h2:text-is("ETF Breakdown")').hover()
    await page.mouse.wheel(0, WHEEL_DELTA_PX)
    await page.waitForTimeout(WHEEL_SETTLE_MS)

    expect(await page.evaluate(() => window.scrollY)).toBe(0)
  })

  test('the period row keeps its nine cells on one line on a small phone', async ({ page }) => {
    await page.setViewportSize(SMALL_PHONE)
    await stubPortfolioSummary(page)
    await openRoute(page, '/')
    const layout = await page.getByRole('group', { name: 'Chart period' }).evaluate(group => {
      const cells = Array.from(group.children)
      return {
        cells: cells.length,
        lines: new Set(cells.map(cell => cell.getBoundingClientRect().top)).size,
        clipped: cells.filter(cell => cell.scrollWidth > cell.clientWidth).length,
        overflows: group.getBoundingClientRect().right > document.documentElement.clientWidth,
      }
    })
    expect(layout).toEqual({ cells: 9, lines: 1, clipped: 0, overflows: false })
  })

  test('the More popover spans the period row on a phone', async ({ page }) => {
    await stubPortfolioSummary(page)
    await openRoute(page, '/')
    await page.getByRole('button', { name: 'More', exact: true }).click()
    const row = (await page.getByRole('group', { name: 'Chart period' }).boundingBox())!
    const picker = (await page.locator('#period-picker').boundingBox())!
    expect([picker.x, picker.width]).toEqual([row.x, row.width])
  })

  test('state long fund name wraps inside its allocation card', async ({ page }) => {
    await stubDiversificationWithLongFundName(page)
    await openRoute(page, '/diversification')
    const layout = await page.evaluate(subpixel => {
      const names = Array.from(document.querySelectorAll<HTMLElement>('.allocation-card-name'))
      const misfits = names
        .filter(name => {
          const card = name.closest<HTMLElement>('.allocation-card')!
          const contentRight =
            card.getBoundingClientRect().right - parseFloat(getComputedStyle(card).paddingRight)
          return (
            name.getBoundingClientRect().right > contentRight + subpixel ||
            name.scrollWidth > name.clientWidth
          )
        })
        .map(name => name.textContent?.trim())
      return { cards: names.length, misfits }
    }, SUBPIXEL_PX)
    expect(layout).toEqual({ cards: 5, misfits: [] })
  })
})
