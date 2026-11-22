import { describe, expect, it } from 'vitest'
import themeCss from './theme.css?raw'
import { EDITOR_TOKEN_HEX } from '../constants/editor-theme'
import {
  contrastRatio,
  hexFromOklch,
  isInSrgbGamut,
  luminanceFromOklch,
  type Oklch,
} from '../tests/contrast'

type Palette = 'light' | 'dark'

const declarations = new Map(
  [...themeCss.matchAll(/--color-([a-z0-9-]+):\s*([^;]+);/g)].map(([, name, value]) => [
    name,
    value.trim(),
  ])
)

function sideOf(value: string, palette: Palette): string {
  const pair = value.match(/^light-dark\(\s*([^,]+?)\s*,\s*([^,]+?)\s*\)$/)
  if (!pair) return value
  return palette === 'light' ? pair[1] : pair[2]
}

function resolve(name: string, palette: Palette): Oklch | undefined {
  const value = sideOf(declarations.get(name) ?? '', palette)
  const alias = value.match(/^var\(--color-([a-z0-9-]+)\)$/)
  if (alias) return resolve(alias[1], palette)
  const color = value.match(/^oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\)$/)
  if (!color) return undefined
  return { l: Number(color[1]), c: Number(color[2]), h: Number(color[3]) }
}

function declaredTokens(palette: Palette): Map<string, Oklch> {
  const tokens = new Map<string, Oklch>()
  for (const name of declarations.keys()) {
    const color = resolve(name, palette)
    if (color) tokens.set(name, color)
  }
  return tokens
}

const AA_TEXT = 4.5
const AA_NON_TEXT = 3

const PAIRS: ReadonlyArray<[string, string, number]> = [
  ['ink', 'surface', AA_TEXT],
  ['ink', 'paper', AA_TEXT],
  ['ink', 'surface-sunken', AA_TEXT],
  ['ink-soft', 'surface', AA_TEXT],
  ['ink-soft', 'paper', AA_TEXT],
  ['ink-soft', 'surface-sunken', AA_TEXT],
  ['ink-faint', 'surface', AA_NON_TEXT],
  ['brass', 'surface', AA_TEXT],
  ['brass', 'paper', AA_TEXT],
  ['brass-deep', 'surface', AA_TEXT],
  ['gain', 'surface', AA_TEXT],
  ['gain', 'paper', AA_TEXT],
  ['loss', 'surface', AA_TEXT],
  ['loss', 'paper', AA_TEXT],
  ['gain', 'surface-sunken', AA_TEXT],
  ['loss', 'surface-sunken', AA_TEXT],
  ['notice', 'surface', AA_TEXT],
  ['notice', 'surface-sunken', AA_TEXT],
  ['brass', 'brass-wash', AA_TEXT],
  ['gain', 'gain-wash', AA_TEXT],
  ['loss', 'loss-wash', AA_TEXT],
  ['notice', 'notice-wash', AA_TEXT],
  ['ink', 'loss-wash-deep', AA_TEXT],
  ['ink', 'change-flat', AA_TEXT],
  ['ink', 'gain-step-1', AA_TEXT],
  ['ink', 'gain-step-2', AA_TEXT],
  ['ink', 'gain-step-3', AA_TEXT],
  ['ink', 'loss-step-1', AA_TEXT],
  ['ink', 'loss-step-2', AA_TEXT],
  ['ink', 'loss-step-3', AA_TEXT],
  ['surface', 'gain', AA_TEXT],
  ['surface', 'loss', AA_TEXT],
  ['paper', 'gray-700', AA_TEXT],
  ['gray-500', 'surface', AA_TEXT],
  ['gray-500', 'paper', AA_TEXT],
  ['gray-600', 'surface', AA_TEXT],
  ['gray-600', 'paper', AA_TEXT],
  ['series-1', 'surface', AA_NON_TEXT],
  ['series-2', 'surface', AA_NON_TEXT],
  ['series-3', 'surface', AA_NON_TEXT],
  ['series-4', 'surface', AA_NON_TEXT],
  ['series-5', 'surface', AA_NON_TEXT],
  ['series-6', 'surface', AA_NON_TEXT],
  ['on-fill', 'brass', AA_TEXT],
  ['on-fill', 'gain', AA_TEXT],
  ['on-fill', 'loss', AA_TEXT],
  ['on-fill', 'notice', AA_TEXT],
  ['on-fill', 'control-graphite', AA_TEXT],
  ['on-fill', 'control-graphite-deep', AA_TEXT],
  ['on-fill', 'brass-deep', AA_TEXT],
  ['on-fill', 'loss-deep', AA_TEXT],
  ['on-fill', 'gray-600', AA_TEXT],
  ['on-neutral', 'neutral-fill', AA_TEXT],
  ['on-neutral', 'neutral-fill-hover', AA_TEXT],
]

const PALETTES: ReadonlyArray<{
  palette: Palette
  locks: ReadonlyArray<[string, string, number]>
}> = [
  {
    palette: 'light',
    locks: [
      ['gain', 'loss', 0.52],
      ['gain-step-1', 'loss-step-1', 0.93],
      ['gain-step-2', 'loss-step-2', 0.84],
      ['gain-step-3', 'loss-step-3', 0.68],
    ],
  },
  {
    palette: 'dark',
    locks: [
      ['gain', 'loss', 0.76],
      ['gain-step-1', 'loss-step-1', 0.29],
      ['gain-step-2', 'loss-step-2', 0.38],
      ['gain-step-3', 'loss-step-3', 0.48],
    ],
  },
]

describe.each(PALETTES)('the Statement palette in $palette', ({ palette, locks }) => {
  it('declares every token the contrast contract refers to', () => {
    const tokens = declaredTokens(palette)
    const missing = [...new Set(PAIRS.flatMap(([a, b]) => [a, b]))].filter(
      name => !tokens.has(name)
    )
    expect(missing).toEqual([])
  })

  it('keeps every declared token inside the sRGB gamut', () => {
    const clipped = [...declaredTokens(palette)]
      .filter(([, color]) => !isInSrgbGamut(color))
      .map(([name]) => name)
    expect(clipped).toEqual([])
  })

  it('meets its contrast contract on every declared pair', () => {
    const tokens = declaredTokens(palette)
    const failures = PAIRS.filter(
      ([foreground, background]) => tokens.has(foreground) && tokens.has(background)
    )
      .map(([foreground, background, minimum]) => ({
        pair: `${foreground} on ${background}`,
        ratio: contrastRatio(
          luminanceFromOklch(tokens.get(foreground) as Oklch),
          luminanceFromOklch(tokens.get(background) as Oklch)
        ),
        minimum,
      }))
      .filter(({ ratio, minimum }) => ratio < minimum)
      .map(({ pair, ratio, minimum }) => ({ pair, ratio: Number(ratio.toFixed(2)), minimum }))
    expect(failures).toEqual([])
  })

  it.each(locks)(
    'locks %s and %s to the same lightness so equal movements read with equal weight',
    (gain, loss, lightness) => {
      const tokens = declaredTokens(palette)
      expect([tokens.get(gain)?.l, tokens.get(loss)?.l]).toEqual([lightness, lightness])
    }
  )

  it.each(['step-1', 'step-2', 'step-3'])('keeps %s of each side on the hue of its side', step => {
    const tokens = declaredTokens(palette)
    expect([tokens.get(`gain-${step}`)?.h, tokens.get(`loss-${step}`)?.h]).toEqual([
      tokens.get('gain')?.h,
      tokens.get('loss')?.h,
    ])
  })
})

describe('the editor theme', () => {
  it.each(Object.entries(EDITOR_TOKEN_HEX))(
    'paints with %s at the sRGB hex of its dark token',
    (token, hex) => {
      expect(hexFromOklch(declaredTokens('dark').get(token) as Oklch)).toBe(hex)
    }
  )
})
