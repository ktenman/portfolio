import { test, expect, type Locator, type Page } from '@playwright/test'
import { API_ENDPOINTS } from '../../constants/api'
import { type TransactionsWithSummaryDto } from '../../models/generated/domain-models'
import { freeze, openRoute, settleAndFreeze, waitForBoxHeightToSettle } from './settle'
import { apiRoute, type RouteStub } from './stub'
import { stubBuildInfo } from './build-info-fixture'
import { stubDiversification } from './diversification-fixture'
import { stubEnums } from './enums-fixture'
import { stubEtfBreakdown } from './etf-fixture'
import { stubInstruments } from './instruments-fixture'
import { stubPortfolioSummary } from './summary-fixture'
import { stubTransactions } from './transactions-fixture'
import { stubWindows } from './windows-fixture'

const MODAL_CONTENT_TIMEOUT_MS = 60000
const STATE_TIMEOUT_MS = 30000
const TOAST_MODULE_PATH = '/composables/use-toast.ts'
const LOADING_HOLD_MS = 20000

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
const QUICK_DATES_TOGGLE = '[data-testid="quickDatesToggle"]'

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

test.describe('desktop states', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop')
  })

  test.beforeEach(async ({ page }) => {
    await stubBuildInfo(page)
    await stubEnums(page)
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
    await page.click('.platform-btn:text-is("% vs S&P 500")')
    await settleAndFreeze(page)
    await expect(page).toHaveScreenshot('summary-performance-mode.png')
  })

  test('selecting both benchmarks overlays three lines', async ({ page }) => {
    await stubPortfolioSummary(page)
    await openRoute(page, '/')
    await page.click('.platform-btn:text-is("% vs S&P 500")')
    await page.click('.platform-btn:text-is("% vs World")')
    await settleAndFreeze(page)
    await expect(page).toHaveScreenshot('summary-benchmark-both.png')
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
