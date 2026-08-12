import { test, expect } from '@playwright/test'
import { freeze, openRoute } from './settle'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubInstruments } from './instruments-fixture'
import { stubTransactions } from './transactions-fixture'

const GAIN = 'rgb(33, 197, 93)'
const LOSS = 'rgb(220, 53, 69)'

const ROUTES = [
  { path: '/instruments', name: 'instruments', stub: stubInstruments },
  { path: '/transactions', name: 'transactions', stub: stubTransactions },
]

test.beforeEach(async ({ page }) => {
  await stubBuildInfo(page)
  await stubEnums(page)
})

for (const route of ROUTES) {
  test(`${route.name} paints every gain and loss cell in the palette tokens`, async ({ page }) => {
    await route.stub(page)
    await openRoute(page, route.path)
    await freeze(page)
    const painted = await page.evaluate(() => {
      const distinct = (selector: string): string[] => [
        ...new Set([...document.querySelectorAll(selector)].map(el => getComputedStyle(el).color)),
      ]
      return { gain: distinct('.text-gain'), loss: distinct('.text-loss') }
    })
    expect(painted).toEqual({ gain: [GAIN], loss: [LOSS] })
  })
}
