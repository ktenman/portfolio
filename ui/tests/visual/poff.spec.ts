import { expect, test, type Page } from '@playwright/test'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubPoff } from './poff-fixture'
import { freeze, openRoute } from './settle'

const PHONES = [
  { width: 375, height: 667 },
  { width: 390, height: 844 },
]
const TARGET_PX = 44
const DAYS = '[aria-label="Festival day"] button'
const CHIPS = '[aria-label="Filters"] button'
const CONTROLS = [DAYS, CHIPS, '[data-testid="poff-earlier"]', '[data-testid="poff-title"]'].join(
  ', '
)

const FILM_CONTROLS = [
  CHIPS,
  '[aria-label="Filters"] [aria-expanded]',
  '[data-testid="poff-sort"]',
  '[data-testid="poff-film-title"]',
].join(', ')

const open = async (page: Page, phone: (typeof PHONES)[number], path = '/poff') => {
  await page.setViewportSize(phone)
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPoff(page)
  await openRoute(page, path)
}

const distinct = (page: Page, selector: string, side: 'width' | 'height') =>
  page
    .locator(selector)
    .evaluateAll(
      (elements, measured) => [
        ...new Set(elements.map(element => Math.round(element.getBoundingClientRect()[measured]))),
      ],
      side
    )

for (const phone of PHONES) {
  const size = `${phone.width} × ${phone.height}`

  for (const [view, path, selector] of [
    ['PÖFF timetable gives', '/poff', CONTROLS],
    ['PÖFF films give', '/poff/films', FILM_CONTROLS],
    ['PÖFF film page gives', '/poff/film/american-doctor', 'article :is(a, button, summary)'],
    [
      'PÖFF search gives',
      '/poff?q=wil',
      'main :is(label:has(input), a, button), [aria-label="Results"] button',
    ],
  ]) {
    test(`${view} every control a 44px target without sideways scroll at ${size}`, async ({
      page,
    }, testInfo) => {
      test.skip(testInfo.project.name !== 'mobile')
      await open(page, phone, path)
      const layout = await page.locator(selector).evaluateAll((controls, target) => {
        const reach = (control: Element, x: number, y: number, dx: number, dy: number) => {
          let steps = 0
          while (
            steps < target &&
            control.contains(document.elementFromPoint(x + dx * (steps + 1), y + dy * (steps + 1)))
          ) {
            steps += 1
          }
          return steps
        }
        const small = controls.flatMap(control => {
          control.scrollIntoView({ block: 'center', inline: 'center' })
          const box = control.getBoundingClientRect()
          const x = box.left + box.width / 2
          const y = box.top + box.height / 2
          const width = 1 + reach(control, x, y, -1, 0) + reach(control, x, y, 1, 0)
          const height = 1 + reach(control, x, y, 0, -1) + reach(control, x, y, 0, 1)
          const name = control.textContent?.replace(/\s+/g, ' ').trim()
          return width < target || height < target ? [`${name} ${width} × ${height}`] : []
        })
        const root = document.documentElement
        return { sideways: root.scrollWidth - root.clientWidth, small }
      }, TARGET_PX)
      expect(layout).toEqual({ sideways: 0, small: [] })
    })
  }

  test(`PÖFF timetable draws 32px chips and 52px day buttons at ${size}`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await open(page, phone)
    expect({
      chips: await distinct(page, CHIPS, 'height'),
      days: await distinct(page, DAYS, 'width'),
    }).toEqual({ chips: [32], days: [52] })
  })
}

test('PÖFF timetable opens a film in the panel from its row and closes it on Escape', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop')
  await open(page, { width: 1440, height: 900 })
  await page.locator('[data-testid="poff-row"]', { hasText: 'Ameerika arst' }).first().click()
  await expect(page.getByTestId('poff-panel').getByRole('heading', { level: 2 })).toHaveText(
    'Ameerika arst'
  )
  await page.mouse.wheel(0, 400)
  const nav = await page.locator('nav.navbar').boundingBox()
  await expect
    .poll(async () => (await page.getByTestId('poff-panel').boundingBox())?.y)
    .toBe(nav!.y + nav!.height + 16)
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('poff-panel')).toHaveCount(0)
})

const FOLDS = ['poff-earlier', 'poff-search-more']

for (const [view, path, title] of [
  ['timetable', '/poff', 'poff-title'],
  ['films', '/poff/films', 'poff-film-title'],
  ['search', '/poff?q=kino', 'poff-search-film'],
] as const) {
  for (const [way, back] of [
    ['its back link', (page: Page) => page.getByRole('link', { name: /^Back to/ }).click()],
    ["the browser's back", (page: Page) => page.goBack()],
  ] as const) {
    test(`PÖFF ${view} keeps its place and folds when a film is left by ${way}`, async ({
      page,
    }, testInfo) => {
      await stubBuildInfo(page)
      await stubEnums(page)
      await stubPoff(page)
      await openRoute(page, path)
      for (const fold of FOLDS) {
        const button = page.getByTestId(fold).first()
        if (await button.count()) await button.click()
      }
      const link = page.getByTestId(title).last()
      await link.scrollIntoViewIfNeeded()
      let place = await page.evaluate(() => scrollY)
      const panel = view === 'timetable' && testInfo.project.name === 'desktop'
      await (panel ? page.getByTestId('poff-row').last().locator('td').first() : link).click()
      await expect(page).toHaveURL(/\/poff\/film\/|[?&]film=/)
      if (page.url().includes('film=')) {
        place = await page.evaluate(() => scrollY)
        await page.getByRole('link', { name: 'Open film page' }).click()
      }
      await expect(page).toHaveURL(/\/poff\/film\//)
      await back(page)
      await expect(page.getByTestId(title).last()).toBeVisible()
      await expect.poll(() => page.evaluate(() => scrollY)).toBe(place)
      await page.waitForTimeout(500)
      expect(await page.evaluate(() => scrollY)).toBe(place)
    })
  }
}

for (const [device, viewport] of [
  ['phone', PHONES[1]],
  ['desktop', { width: 1440, height: 900 }],
] as const) {
  test(`PÖFF film steps through every still in the enlarged view on a ${device}`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop')
    await open(page, viewport, '/poff/film/american-doctor')
    await page.getByRole('button', { name: /^Still 1 of 5/ }).click()
    for (let still = 2; still <= 5; still++) {
      await page.getByRole('button', { name: 'Next still' }).click()
      await expect(page.getByRole('dialog')).toHaveAccessibleName(
        `Still ${still} of 5 from Ameerika arst`
      )
    }
    await expect(page.getByRole('button', { name: 'Next still' })).toHaveCount(0)
    await page.keyboard.press('ArrowLeft')
    await page.keyboard.press('ArrowLeft')
    await expect(page.getByRole('dialog')).toHaveAccessibleName('Still 3 of 5 from Ameerika arst')
    await freeze(page)
    const sizes = await distinct(page, 'dialog[open] button', 'width')
    await expect(page).toHaveScreenshot(`poff-still-${device}.png`)
    expect(sizes).toEqual([TARGET_PX])
    const view = page.locator('dialog[open]')
    expect(await view.boundingBox()).toEqual({ x: 0, y: 0, ...viewport })
    await expect(view).toHaveCSS('outline-style', 'none')
    const still = (await page.locator('dialog[open] img').boundingBox())!
    await page.mouse.move(still.x + still.width * 0.8, still.y + still.height / 2)
    await page.mouse.down()
    await page.mouse.move(still.x + still.width * 0.2, still.y + still.height / 2, { steps: 5 })
    await page.mouse.up()
    await expect(page.getByRole('dialog')).toHaveAccessibleName('Still 4 of 5 from Ameerika arst')
  })
}
