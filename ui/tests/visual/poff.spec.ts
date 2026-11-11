import { expect, test, type Page } from '@playwright/test'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubPoff } from './poff-fixture'
import { openRoute } from './settle'

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

const openTimetable = async (page: Page, phone: (typeof PHONES)[number]) => {
  await page.setViewportSize(phone)
  await stubBuildInfo(page)
  await stubEnums(page)
  await stubPoff(page)
  await openRoute(page, '/poff')
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

  test(`PÖFF timetable gives every control a 44px target without sideways scroll at ${size}`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await openTimetable(page, phone)
    const layout = await page.locator(CONTROLS).evaluateAll((controls, target) => {
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

  test(`PÖFF timetable draws 36px chips and 52px day buttons at ${size}`, async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile')
    await openTimetable(page, phone)
    expect({
      chips: await distinct(page, CHIPS, 'height'),
      days: await distinct(page, DAYS, 'width'),
    }).toEqual({ chips: [36], days: [52] })
  })
}
