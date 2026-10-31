import { test, expect, type Page } from '@playwright/test'
import { freeze, openRoute } from './settle'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubInstruments } from './instruments-fixture'

const MAX_CAPTURE_HEIGHT = 12000
const TOUCH_PX = 44
const WIDTHS = [1440, 1024, 1023, 768, 390, 375, 320]
const PHONE_WIDTHS = [390, 375, 320]
const PICKER = '[aria-controls="period-picker"]'
const FORM_SELECT_FACE = [
  'background-color',
  'background-image',
  'border-top-color',
  'border-top-left-radius',
  'color',
  'font-family',
  'font-size',
  'font-weight',
  'height',
]
const RAIL_PX = 45
const WRAPPED_ROW_PX = 43
const TABLE_RAIL = [
  { width: 1440, oneLine: true },
  { width: 768, oneLine: true },
  { width: 390, oneLine: true },
  { width: 375, oneLine: true },
  { width: 320, oneLine: false },
]
const SELECT_HINT = ['All positions', 'Select a box to see its figures']
const TAP_HINT = ['All positions', 'Tap a box to see its figures']
const READOUT_LINES = [
  { width: 1440, lines: SELECT_HINT },
  { width: 1280, lines: SELECT_HINT },
  { width: 1279, lines: [] },
  { width: 992, lines: [] },
  { width: 991, lines: SELECT_HINT },
  { width: 768, lines: SELECT_HINT },
  { width: 767, lines: TAP_HINT },
  { width: 320, lines: TAP_HINT },
]

test.beforeEach(async ({ page }) => {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubInstruments(page)
  await openRoute(page, '/instruments/heatmap')
})

const capture = async (page: Page, name: string) => {
  await freeze(page)
  const width = await page.evaluate(() => document.documentElement.scrollWidth)
  await expect(page).toHaveScreenshot(name, {
    fullPage: true,
    clip: { x: 0, y: 0, width, height: MAX_CAPTURE_HEIGHT },
  })
}

const openTable = (page: Page) =>
  page
    .getByRole('navigation', { name: 'Instrument views' })
    .getByRole('link', { name: 'Table' })
    .click()

const pickPeriod = async (page: Page, period: string) => {
  await page.locator(PICKER).click()
  await page.locator('#period-picker').getByRole('button', { name: period, exact: true }).click()
  await expect(page.locator(PICKER)).toHaveText(period)
}

const railLayout = (page: Page) =>
  page.evaluate(picker => {
    const middle = (target: Element) => {
      const { top, height } = target.getBoundingClientRect()
      return Math.round(top + height / 2)
    }
    const rail = document.querySelector('.controls-row')!
    const button = middle(rail.querySelector(picker)!)
    const toggle = rail.querySelector('.toggle-switch')
    return {
      width: window.innerWidth,
      height: rail.getBoundingClientRect().height,
      besideTabs: button === middle(rail.querySelector('nav')!),
      together: toggle === null || middle(toggle) === button,
    }
  }, PICKER)

test('heatmap', async ({ page }) => {
  await capture(page, 'instruments-heatmap.png')
})

test('heatmap with a box selected', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await page.getByRole('button', { name: /^TSTINF,/ }).click()
  await page.mouse.move(0, 0)
  await capture(page, 'instruments-heatmap-selected.png')
})

test('readout logo shows on hover that it can be replaced', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await page.getByRole('button', { name: /^TSTINF,/ }).click()
  await freeze(page)
  await page.getByTitle('Click to replace logo').hover()
  await expect(page.locator('[data-readout]')).toHaveScreenshot(
    'instruments-heatmap-logo-hover.png'
  )
})

test('heatmap fits every width without horizontal scroll', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await expect
      .poll(() =>
        page.evaluate(() => ({
          width: window.innerWidth,
          overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
        }))
      )
      .toEqual({ width, overflow: 0 })
  }
})

test('heatmap readout shows every figure in full on phones', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  for (const width of PHONE_WIDTHS) {
    await page.setViewportSize({ width, height: 844 })
    await expect
      .poll(() =>
        page.evaluate(() => ({
          width: window.innerWidth,
          clipped: Array.from(document.querySelectorAll('[data-readout] dd'))
            .filter(figure => figure.scrollWidth > figure.clientWidth)
            .map(figure => figure.textContent?.trim()),
        }))
      )
      .toEqual({ width, clipped: [] })
  }
})

test('change list bars grow rightward from one edge on phones', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  for (const width of PHONE_WIDTHS) {
    await page.setViewportSize({ width, height: 844 })
    await expect
      .poll(() =>
        page.evaluate(() => ({
          width: window.innerWidth,
          starts: new Set(
            Array.from(
              document.querySelectorAll('[data-bar]'),
              bar => bar.getBoundingClientRect().left
            )
          ).size,
        }))
      )
      .toEqual({ width, starts: 1 })
  }
})

test('view tabs are 44 px tall on phones', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  const heights = await page
    .getByRole('navigation', { name: 'Instrument views' })
    .getByRole('link')
    .evaluateAll(links => links.map(link => link.getBoundingClientRect().height))
  expect(heights).toEqual([TOUCH_PX, TOUCH_PX])
})

test('view tabs sit 16 px apart on phones', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  const gap = await page
    .getByRole('navigation', { name: 'Instrument views' })
    .evaluate(tabs => getComputedStyle(tabs).columnGap)
  expect(gap).toBe('16px')
})

test('table controls stay beside the tabs until they wrap together', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await openTable(page)
  await pickPeriod(page, 'MAX')
  for (const { width, oneLine } of TABLE_RAIL) {
    await page.setViewportSize({ width, height: 900 })
    await expect
      .poll(() => railLayout(page))
      .toEqual({
        width,
        height: oneLine ? RAIL_PX : RAIL_PX + WRAPPED_ROW_PX,
        besideTabs: oneLine,
        together: true,
      })
  }
})

test('heatmap controls stay beside the tabs at every width', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await expect
      .poll(() => railLayout(page))
      .toEqual({ width, height: RAIL_PX, besideTabs: true, together: true })
  }
})

test('hairline stays directly under the tabs when the controls wrap', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await openTable(page)
  for (const { width } of TABLE_RAIL) {
    await page.setViewportSize({ width, height: 900 })
    await expect
      .poll(() =>
        page.evaluate(() => {
          const rail = document.querySelector('.controls-row')!
          const tabs = rail.querySelector('nav')!.getBoundingClientRect().bottom
          const hairline = parseFloat(getComputedStyle(rail, '::before').top)
          return {
            width: window.innerWidth,
            gap: tabs - rail.getBoundingClientRect().top - hairline,
          }
        })
      )
      .toEqual({ width, gap: 0 })
  }
})

test('tab rail stays hidden on a short screen', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await page.setViewportSize({ width: 844, height: 390 })
  await expect(page.locator('.controls-row')).toBeHidden()
})

test('period popover stays inside the viewport with every cell whole', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await page.locator(PICKER).click()
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    await expect
      .poll(() =>
        page.evaluate(() => {
          const popover = document.querySelector('#period-picker')!
          const { left, right } = popover.getBoundingClientRect()
          return {
            width: window.innerWidth,
            inside: left >= 0 && right <= window.innerWidth,
            clipped: Array.from(popover.querySelectorAll('button'))
              .filter(cell => cell.scrollWidth > cell.clientWidth)
              .map(cell => cell.textContent?.trim()),
          }
        })
      )
      .toEqual({ width, inside: true, clipped: [] })
  }
})

test('period button looks like the form select', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  const [button, select] = await page.locator(PICKER).evaluate((picker, properties) => {
    const field = document.createElement('select')
    field.className = 'form-select form-select-sm'
    picker.after(field)
    return [picker, field].map(target =>
      properties.map(property => getComputedStyle(target).getPropertyValue(property))
    )
  }, FORM_SELECT_FACE)
  expect(button).toEqual(select)
})

test('period button leaves the same room beside every period', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  const roomWith = async (period: string) => {
    await pickPeriod(page, period)
    return page.locator(PICKER).evaluate(button => {
      const label = document.createRange()
      label.selectNodeContents(button)
      return button.getBoundingClientRect().width - label.getBoundingClientRect().width
    })
  }
  const rooms = [await roomWith('1D'), await roomWith('YTD'), await roomWith('MAX')]
  expect(new Set(rooms).size).toBe(1)
})

test('period picker and Active only are 44 px tall to tap', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'desktop')
  await openTable(page)
  await page.locator(PICKER).click()
  const heights = await page
    .locator(`${PICKER}, #period-picker button, .toggle-switch`)
    .evaluateAll(targets =>
      targets.map(target => parseFloat(getComputedStyle(target, '::before').height))
    )
  expect(new Set(heights)).toEqual(new Set([TOUCH_PX]))
})

test('change list sets tickers and amounts at 14 px', async ({ page }) => {
  const sizes = await page
    .locator('ol li .truncate, ol li .text-right')
    .evaluateAll(spans => spans.map(span => getComputedStyle(span).fontSize))
  expect(new Set(sizes)).toEqual(new Set(['14px']))
})

test('change list shows a 12-character ticker in full', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  const clipped = await page.locator('ol li .truncate').evaluateAll(tickers => {
    tickers[0].textContent = 'WMWMWMWMWMWM'
    return tickers.filter(ticker => ticker.scrollWidth > ticker.clientWidth).length
  })
  expect(clipped).toBe(0)
})

test('resting readout hides the hint only where the band is too narrow', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  for (const { width, lines } of READOUT_LINES) {
    await page.setViewportSize({ width, height: 900 })
    await expect
      .poll(() =>
        page.evaluate(() => ({
          width: window.innerWidth,
          lines: Array.from(document.querySelectorAll<HTMLElement>('[data-readout] p'))
            .filter(line => line.getClientRects().length > 0)
            .map(line => line.innerText.trim()),
        }))
      )
      .toEqual({ width, lines })
  }
})

test('resting readout sets the hint at 14 px and the totals at 16 px', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  const sizes = await page
    .locator('[data-readout] p:last-child, [data-readout] dd')
    .evaluateAll(lines => lines.map(line => getComputedStyle(line).fontSize))
  expect(sizes).toEqual(['14px', '16px', '16px', '16px'])
})
