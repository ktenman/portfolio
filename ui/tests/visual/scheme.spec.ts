import { createHash } from 'node:crypto'
import { test, expect, type Page } from '@playwright/test'
import { freeze, openRoute, routeShot, settleAndFreeze, waitForPageToSettle } from './settle'
import { type RouteStub } from './stub'
import { stubShell } from './shell-fixture'
import { stubCalculator } from './calculator-fixture'
import { stubDiversification } from './diversification-fixture'
import { stubEnums } from './enums-fixture'
import { stubPortfolioSummary } from './summary-fixture'

interface SchemeRoute {
  path: string
  name: string
  stub: RouteStub
  chartView?: string
  pageView?: string
}

const ROUTES: SchemeRoute[] = [
  { path: '/', name: 'summary', stub: stubPortfolioSummary },
  {
    path: '/diversification',
    name: 'diversification',
    stub: stubDiversification,
    chartView: 'Donut',
    pageView: 'Bars',
  },
  { path: '/calculator', name: 'calculator', stub: stubCalculator },
]

test.use({ colorScheme: 'light' })

async function open(page: Page, route: SchemeRoute): Promise<void> {
  await stubShell(page)
  await stubEnums(page)
  await route.stub(page)
  await openRoute(page, route.path)
}

async function show(page: Page, view: string | undefined): Promise<void> {
  if (!view) return
  await page.getByRole('button', { name: view, exact: true }).click()
  await page.mouse.move(0, 0)
  await page.evaluate(() => window.scrollTo(0, 0))
  await waitForPageToSettle(page)
}

async function paint(page: Page): Promise<string[]> {
  const urls = await page
    .locator('canvas')
    .evaluateAll(canvases =>
      canvases
        .filter(canvas => canvas.checkVisibility())
        .map(canvas => (canvas as HTMLCanvasElement).toDataURL())
    )
  return urls.map(url => createHash('sha256').update(url).digest('hex').slice(0, 16))
}

for (const route of ROUTES) {
  test(`${route.name} repaints in dark when the scheme changes`, async ({ page, browser }) => {
    await open(page, route)
    await show(page, route.chartView)
    const light = await paint(page)
    await page.emulateMedia({ colorScheme: 'dark' })
    await settleAndFreeze(page)
    const switched = await paint(page)
    await show(page, route.pageView)
    const context = await browser.newContext({ colorScheme: 'dark' })
    const loadedPage = await context.newPage()
    await open(loadedPage, route)
    await freeze(loadedPage)
    await show(loadedPage, route.chartView)
    const loaded = await paint(loadedPage)
    await context.close()
    expect.soft(switched).not.toHaveLength(0)
    expect.soft(switched.filter((digest, index) => digest === light[index])).toEqual([])
    expect.soft(switched).toEqual(loaded)
    await expect(page).toHaveScreenshot(`route-${route.name}.png`, await routeShot(page))
  })
}
