import { describe, expect, it } from 'vitest'
import { CHART_COLORS, DONUT_COLORS } from './chart-colors'
import { contrastRatio, isInSrgbGamut, luminanceFromOklch, type Oklch } from '../tests/contrast'

const PAPER: Oklch = { l: 0.985, c: 0.006, h: 85 }
const TOP_COUNT = 15
const TELLABLE_APART = 0.06

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

describe('the chart palette', () => {
  it('holds enough entries that the default slice count never reuses a colour', () => {
    expect(CHART_COLORS.length).toBeGreaterThanOrEqual(TOP_COUNT + 1)
  })

  it('contains no duplicate entries', () => {
    expect(new Set(CHART_COLORS).size).toEqual(CHART_COLORS.length)
  })

  it('separates every touching pair by hue or by lightness, including the closing seam', () => {
    const wheel = CHART_COLORS.map(parseRampColor)
    const muddy = wheel.filter((color, index) => isMuddy(color, wheel[(index + 1) % wheel.length]))
    expect(muddy).toEqual([])
  })

  it('holds every entry to one of two lightness levels so no slice shouts over another', () => {
    const levels = new Set(CHART_COLORS.map(color => parseRampColor(color).l))
    expect(levels.size).toEqual(2)
  })

  it('renders every entry inside the sRGB gamut', () => {
    const outside = CHART_COLORS.filter(color => !isInSrgbGamut(parseRampColor(color)))
    expect(outside).toEqual([])
  })

  it('keeps every entry distinguishable from the paper it sits on', () => {
    const paper = luminanceFromOklch(PAPER)
    const ratios = CHART_COLORS.map(color =>
      contrastRatio(luminanceFromOklch(parseRampColor(color)), paper)
    )
    expect(Math.min(...ratios)).toBeGreaterThanOrEqual(1.7)
  })
})

describe('the breakdown donut palette', () => {
  const wheel = DONUT_COLORS.map(parseRampColor)

  it('holds enough entries that the default slice count never reuses a colour', () => {
    expect(DONUT_COLORS.length).toBeGreaterThanOrEqual(TOP_COUNT + 1)
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
    const paper = luminanceFromOklch(PAPER)
    const ratios = wheel.map(color => contrastRatio(luminanceFromOklch(color), paper))
    expect(Math.min(...ratios)).toBeGreaterThanOrEqual(1.7)
  })
})
