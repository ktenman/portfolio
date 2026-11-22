import { describe, expect, it } from 'vitest'
import {
  CHART_COLORS,
  CHART_COLORS_DARK,
  DONUT_COLORS,
  DONUT_COLORS_DARK,
  schemeColor,
  withAlpha,
} from './chart-colors'
import { contrastRatio, isInSrgbGamut, luminanceFromOklch, type Oklch } from '../tests/contrast'

const LIGHT_PAPER: Oklch = { l: 0.985, c: 0.006, h: 85 }
const DARK_PAPER: Oklch = { l: 0.2, c: 0.008, h: 60 }
const TOP_COUNT = 15
const TELLABLE_APART = 0.06

const SCHEMES = [
  { scheme: 'light', series: CHART_COLORS, donut: DONUT_COLORS, paper: LIGHT_PAPER },
  { scheme: 'dark', series: CHART_COLORS_DARK, donut: DONUT_COLORS_DARK, paper: DARK_PAPER },
]

function parseRampColor(value: string): Oklch {
  const parts = value.match(/^oklch\(([\d.]+) ([\d.]+) ([\d.]+)\)$/)
  if (!parts) {
    throw new Error(`Cannot read an oklch colour from "${value}"`)
  }
  return { l: Number(parts[1]), c: Number(parts[2]), h: Number(parts[3]) }
}

function perceptualDistance(one: Oklch, other: Oklch): number {
  const toLab = ({ l, c, h }: Oklch) => [
    l,
    c * Math.cos((h * Math.PI) / 180),
    c * Math.sin((h * Math.PI) / 180),
  ]
  const [l1, a1, b1] = toLab(one)
  const [l2, a2, b2] = toLab(other)
  return Math.hypot(l1 - l2, a1 - a2, b1 - b2)
}

function isMuddy(one: Oklch, other: Oklch): boolean {
  const hueGap = Math.abs(one.h - other.h)
  return Math.min(hueGap, 360 - hueGap) < 30 && Math.abs(one.l - other.l) < 0.15
}

describe.each(SCHEMES)('the $scheme chart palette', ({ series, paper }) => {
  it('holds enough entries that the default slice count never reuses a colour', () => {
    expect(series.length).toBeGreaterThanOrEqual(TOP_COUNT + 1)
  })

  it('contains no duplicate entries', () => {
    expect(new Set(series).size).toEqual(series.length)
  })

  it('separates every touching pair by hue or by lightness, including the closing seam', () => {
    const wheel = series.map(parseRampColor)
    const muddy = wheel.filter((color, index) => isMuddy(color, wheel[(index + 1) % wheel.length]))
    expect(muddy).toEqual([])
  })

  it('holds every entry to one of two lightness levels so no slice shouts over another', () => {
    const levels = new Set(series.map(color => parseRampColor(color).l))
    expect(levels.size).toEqual(2)
  })

  it('renders every entry inside the sRGB gamut', () => {
    const outside = series.filter(color => !isInSrgbGamut(parseRampColor(color)))
    expect(outside).toEqual([])
  })

  it('keeps every entry distinguishable from the paper it sits on', () => {
    const background = luminanceFromOklch(paper)
    const ratios = series.map(color =>
      contrastRatio(luminanceFromOklch(parseRampColor(color)), background)
    )
    expect(Math.min(...ratios)).toBeGreaterThanOrEqual(1.7)
  })
})

describe.each(SCHEMES)('the $scheme breakdown donut palette', ({ donut, paper }) => {
  const wheel = donut.map(parseRampColor)

  it('holds enough entries that the default slice count never reuses a colour', () => {
    expect(donut.length).toBeGreaterThanOrEqual(TOP_COUNT + 1)
  })

  it('keeps every pair of slices far enough apart to read as two colours, not one', () => {
    const gaps = wheel.flatMap((one, index) =>
      wheel.slice(index + 1).map(other => perceptualDistance(one, other))
    )
    expect(Math.min(...gaps)).toBeGreaterThanOrEqual(TELLABLE_APART)
  })

  it('separates every touching pair by hue or by lightness, including the closing seam', () => {
    const muddy = wheel.filter((color, index) => isMuddy(color, wheel[(index + 1) % wheel.length]))
    expect(muddy).toEqual([])
  })

  it('renders every entry inside the sRGB gamut', () => {
    expect(wheel.filter(color => !isInSrgbGamut(color))).toEqual([])
  })

  it('keeps every entry distinguishable from the paper it sits on', () => {
    const background = luminanceFromOklch(paper)
    const ratios = wheel.map(color => contrastRatio(luminanceFromOklch(color), background))
    expect(Math.min(...ratios)).toBeGreaterThanOrEqual(1.7)
  })
})

describe('the dark palettes', () => {
  it('keep the length of the light ones', () => {
    expect([CHART_COLORS_DARK.length, DONUT_COLORS_DARK.length]).toEqual([
      CHART_COLORS.length,
      DONUT_COLORS.length,
    ])
  })
})

describe('schemeColor', () => {
  it('swaps a series colour for its dark counterpart in dark', () => {
    expect(schemeColor(CHART_COLORS[0], true)).toBe(CHART_COLORS_DARK[0])
  })

  it('swaps a donut colour that already carries alpha and keeps the alpha', () => {
    expect(schemeColor(withAlpha(DONUT_COLORS[6], 0.3), true)).toBe(
      withAlpha(DONUT_COLORS_DARK[6], 0.3)
    )
  })

  it('returns its input in light', () => {
    expect(schemeColor(DONUT_COLORS[6], false)).toBe(DONUT_COLORS[6])
  })

  it('returns a colour in neither light palette unchanged in dark', () => {
    expect(schemeColor('oklch(0.42 0.1 12 / 0.5)', true)).toBe('oklch(0.42 0.1 12 / 0.5)')
  })
})
