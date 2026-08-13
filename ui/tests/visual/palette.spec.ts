import { expect, test } from '@playwright/test'
import { contrastRatio, flattenLayers, luminanceFromRgbString } from '../contrast'
import { freeze, openRoute } from './settle'
import { stubBuildInfo } from './build-info-fixture'
import { stubEnums } from './enums-fixture'
import { stubEtfBreakdown } from './etf-fixture'
import { stubInstruments } from './instruments-fixture'
import { stubTransactions } from './transactions-fixture'

const AA_TEXT = 4.5
const AA_LARGE_TEXT = 3
const AA_NON_TEXT = 3
const TAB_STOPS = 15

const ROUTES = [
  { path: '/instruments', name: 'instruments', stub: stubInstruments },
  { path: '/transactions', name: 'transactions', stub: stubTransactions },
  { path: '/etf-breakdown', name: 'etf-breakdown', stub: stubEtfBreakdown },
]

interface PaintedText {
  sample: string
  color: string[]
  background: string[]
  large: boolean
}

interface PaintedRing {
  target: string
  style: string
  width: number
  offset: number
  outlineColor: string
  background: string[]
}

test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await stubBuildInfo(page)
  await stubEnums(page)
})

for (const route of ROUTES) {
  test(`${route.name} paints every piece of text above its AA threshold`, async ({ page }) => {
    await route.stub(page)
    await openRoute(page, route.path)
    await freeze(page)

    const painted: PaintedText[] = await page.evaluate(() => {
      const backgroundLayers = (element: Element): string[] => {
        const layers: string[] = []
        for (let node: Element | null = element; node; node = node.parentElement) {
          layers.push(getComputedStyle(node).backgroundColor)
        }
        return layers
      }
      const ownText = (element: Element): string =>
        [...element.childNodes]
          .filter(node => node.nodeType === Node.TEXT_NODE)
          .map(node => node.textContent ?? '')
          .join('')
          .trim()
      return [...document.querySelectorAll('body *')]
        .filter(
          element =>
            ownText(element).length > 0 &&
            element.checkVisibility({
              opacityProperty: true,
              visibilityProperty: true,
              contentVisibilityAuto: true,
            })
        )
        .map(element => {
          const style = getComputedStyle(element)
          const size = Number.parseFloat(style.fontSize)
          const bold = Number.parseInt(style.fontWeight, 10) >= 700
          const background = backgroundLayers(element)
          return {
            sample: ownText(element).slice(0, 40),
            color: [style.color, ...background],
            background,
            large: size >= 24 || (size >= 18.66 && bold),
          }
        })
    })

    expect(painted.length).toBeGreaterThan(20)

    const seen = new Set<string>()
    const failures = painted
      .map(item => ({
        sample: item.sample,
        color: flattenLayers(item.color),
        background: flattenLayers(item.background),
        large: item.large,
      }))
      .filter(item => {
        const key = `${item.color}|${item.background}|${item.large}`
        if (seen.has(key)) return false
        seen.add(key)
        return true
      })
      .map(({ sample, color, background, large }) => ({
        sample,
        color,
        background,
        ratio: contrastRatio(luminanceFromRgbString(color), luminanceFromRgbString(background)),
        minimum: large ? AA_LARGE_TEXT : AA_TEXT,
      }))
      .filter(item => item.ratio < item.minimum)
      .map(item => ({ ...item, ratio: Number(item.ratio.toFixed(2)) }))

    expect(failures).toEqual([])
  })

  test(`${route.name} keeps every focus ring at 2px and above the non-text threshold`, async ({
    page,
  }) => {
    await route.stub(page)
    await openRoute(page, route.path)
    await freeze(page)

    const rings: PaintedRing[] = []
    for (let stop = 0; stop < TAB_STOPS; stop += 1) {
      await page.keyboard.press('Tab')
      const ring = await page.evaluate(async () => {
        await new Promise(requestAnimationFrame)
        await new Promise(requestAnimationFrame)
        const focused = document.activeElement
        if (!focused || focused === document.body) return null
        const layers: string[] = []
        for (let node: Element | null = focused.parentElement; node; node = node.parentElement) {
          layers.push(getComputedStyle(node).backgroundColor)
        }
        const style = getComputedStyle(focused)
        return {
          target: `${focused.tagName.toLowerCase()}.${focused.className}`.slice(0, 40),
          style: style.outlineStyle,
          width: Number.parseFloat(style.outlineWidth),
          offset: Number.parseFloat(style.outlineOffset),
          outlineColor: style.outlineColor,
          background: layers,
        }
      })
      if (ring) rings.push(ring)
    }

    expect(rings).toHaveLength(TAB_STOPS)

    const failures = rings
      .map(ring => {
        const background = flattenLayers(ring.background)
        const color = flattenLayers([ring.outlineColor, ...ring.background])
        return {
          target: ring.target,
          style: ring.style,
          width: ring.width,
          offset: ring.offset,
          color,
          ratio: contrastRatio(luminanceFromRgbString(color), luminanceFromRgbString(background)),
        }
      })
      .filter(
        ring =>
          ring.style === 'none' || ring.width < 2 || ring.offset <= 0 || ring.ratio < AA_NON_TEXT
      )
      .map(ring => ({ ...ring, ratio: Number(ring.ratio.toFixed(2)) }))

    expect(failures).toEqual([])
  })
}
