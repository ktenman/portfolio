import { expect, test } from '@playwright/test'
import { stubShell } from './shell-fixture'
import { stubEnums } from './enums-fixture'
import { stubRadioStations } from './radio-fixture'
import { openRoute, settleAndFreeze } from './settle'

test.beforeEach(async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await stubShell(page)
  await stubEnums(page)
  await stubRadioStations(page)
})

test('the radio tab offers a chip per listed station', async ({ page }) => {
  await openRoute(page, '/radio')
  const chips = page.getByRole('group', { name: 'Station' })
  await expect(chips.getByRole('button')).toHaveCount(6)
  await settleAndFreeze(page)
  await expect(chips).toHaveScreenshot('radio-stations-chips.png')
})

test('the station chips swipe sideways on a phone to reach the last station', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  await openRoute(page, '/radio')
  const chips = page.getByRole('group', { name: 'Station' })
  await chips.evaluate(row => row.scrollTo({ left: row.scrollWidth }))
  await expect(chips.getByRole('button', { name: 'Raadio 2' })).toBeInViewport({ ratio: 1 })
})

test('the recorded time card draws a track per listed station', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await openRoute(page, '/radio/catch-up')
  await expect(page.getByTestId('radio-day-track')).toHaveCount(5)
  await settleAndFreeze(page)
  await expect(page).toHaveScreenshot('radio-stations-catch-up.png', { fullPage: true })
})

test('the recorded time card draws Now as one line through every station', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await openRoute(page, '/radio/catch-up')
  await expect(page.getByTestId('radio-day-track')).toHaveCount(5)
  const line = (await page.getByTestId('radio-day-now').boundingBox())!
  const clock = (await page.getByTestId('radio-day-clock').boundingBox())!
  const ticks = await page
    .getByTestId('radio-day-tick')
    .evaluateAll(marks => marks.map(mark => mark.getBoundingClientRect()))
  expect({
    top: line.y <= Math.min(...ticks.map(tick => tick.top)),
    bottom: line.y + line.height >= Math.max(...ticks.map(tick => tick.bottom)),
    gap: clock.y - (line.y + line.height) <= 8,
  }).toEqual({ top: true, bottom: true, gap: true })
})

test('the recorded time card keeps Now in sight through the labels it crosses', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await openRoute(page, '/radio/catch-up')
  const line = (await page.getByTestId('radio-day-now').boundingBox())!
  const labels = await page
    .locator(
      '[data-testid="radio-day-link"] span span, [data-testid="radio-day-planned"] span span'
    )
    .evaluateAll(
      (spans, x) =>
        spans
          .filter(span => {
            const box = span.getBoundingClientRect()
            return box.left <= x && box.right >= x
          })
          .map(span => getComputedStyle(span).backgroundColor),
      line.x + line.width / 2
    )
  expect({
    crossed: labels.length > 0,
    fills: labels.filter(fill => fill !== 'rgba(0, 0, 0, 0)'),
  }).toEqual({
    crossed: true,
    fills: [],
  })
})
