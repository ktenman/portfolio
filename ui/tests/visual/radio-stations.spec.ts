import { expect, test } from '@playwright/test'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubRadioFourStations } from './radio-fixture'
import { openRoute, settleAndFreeze } from './settle'

test.beforeEach(async ({ page }, testInfo) => {
  test.skip(testInfo.project.name === 'tablet')
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadioFourStations(page)
})

test('the radio tab offers a chip per listed station', async ({ page }) => {
  await openRoute(page, '/radio')
  const chips = page.getByRole('group', { name: 'Station' })
  await expect(chips.getByRole('button')).toHaveCount(5)
  await settleAndFreeze(page)
  await expect(chips).toHaveScreenshot('radio-stations-chips.png')
})

test('the recorded time card draws a track per listed station', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await openRoute(page, '/radio/catch-up')
  await expect(page.getByTestId('radio-day-track')).toHaveCount(4)
  await settleAndFreeze(page)
  await expect(page).toHaveScreenshot('radio-stations-catch-up.png', { fullPage: true })
})
