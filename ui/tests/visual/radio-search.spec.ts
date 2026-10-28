import { expect, test, type Page } from '@playwright/test'
import { nothingFound, radioSearch } from '../../components/radio/radio-fixture'
import { API_ENDPOINTS } from '../../constants/api'
import { type RadioSearch } from '../../models/generated/domain-models'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubRadio } from './radio-fixture'
import { openRoute, settleAndFreeze } from './settle'
import { apiRoute } from './stub'

const search = async (page: Page, found: RadioSearch) => {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await page.route(apiRoute(`${API_ENDPOINTS.RADIO}/search`), route =>
    route.fulfill({ json: found })
  )
  await openRoute(page, '/radio?q=valimis')
}

test('radio search results replace the tab', async ({ page }) => {
  await search(page, radioSearch())
  await expect(page.getByTestId('radio-search-line')).toHaveCount(2)
  await settleAndFreeze(page)
  await expect(page).toHaveScreenshot('radio-search.png', { fullPage: true })
})

test('the radio search field shows its whole placeholder', async ({ page }) => {
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubRadio(page)
  await openRoute(page, '/radio')
  const room = await page.getByRole('textbox', { name: 'Search what was said' }).evaluate(input => {
    const field = input as HTMLInputElement
    const style = getComputedStyle(field)
    const context = document.createElement('canvas').getContext('2d')!
    context.font = `${style.fontWeight} ${style.fontSize} ${style.fontFamily}`
    const text = context.measureText(field.placeholder).width
    const inner = field.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)
    return inner - text
  })
  expect(room).toBeGreaterThanOrEqual(0)
})

test('radio search suggests close words when nothing was said', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile')
  await search(page, nothingFound(['valised', 'valitsus']))
  await expect(page.getByTestId('radio-search-close')).toHaveCount(2)
  await settleAndFreeze(page)
  await expect(page.getByRole('region', { name: 'Search results' })).toHaveScreenshot(
    'radio-search-close.png'
  )
})

test('radio search result opens its recording at that second', async ({ page }) => {
  await search(page, radioSearch())
  await page.getByTestId('radio-search-line').nth(1).getByRole('link').click()
  await expect(page).toHaveURL(/\/radio\/2026-10-17$/)
  await expect(page.getByTestId('radio-clock')).toContainText('23:23')
  await page.getByRole('link', { name: 'Back to search results' }).click()
  await expect(page).toHaveURL(/\/radio\?q=valimis$/)
})
