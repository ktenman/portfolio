import { describe, expect, it } from 'vitest'
import themeCss from './theme.css?raw'
import { contrastRatio, isInSrgbGamut, luminanceFromOklch, type Oklch } from '../tests/contrast'

function declaredTokens(): Map<string, Oklch> {
  const pattern = /--color-([a-z0-9-]+):\s*oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\)/g
  const tokens = new Map<string, Oklch>()
  for (const [, name, l, c, h] of themeCss.matchAll(pattern)) {
    tokens.set(name, { l: Number(l), c: Number(c), h: Number(h) })
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
  ['brass', 'brass-wash', AA_TEXT],
  ['gain', 'gain-wash', AA_TEXT],
  ['loss', 'loss-wash', AA_TEXT],
  ['notice', 'notice-wash', AA_TEXT],
  ['ink', 'loss-wash-deep', AA_TEXT],
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
]

describe('the Statement palette', () => {
  it('declares every token the contrast contract refers to', () => {
    const tokens = declaredTokens()
    const missing = [...new Set(PAIRS.flatMap(([a, b]) => [a, b]))].filter(
      name => !tokens.has(name)
    )
    expect(missing).toEqual([])
  })

  it('keeps every declared token inside the sRGB gamut', () => {
    const clipped = [...declaredTokens()]
      .filter(([, color]) => !isInSrgbGamut(color))
      .map(([name]) => name)
    expect(clipped).toEqual([])
  })

  it('meets its contrast contract on every declared pair', () => {
    const tokens = declaredTokens()
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

  it('locks gain and loss to the same lightness so equal movements read with equal weight', () => {
    const tokens = declaredTokens()
    expect([tokens.get('gain')?.l, tokens.get('loss')?.l]).toEqual([0.52, 0.52])
  })
})
