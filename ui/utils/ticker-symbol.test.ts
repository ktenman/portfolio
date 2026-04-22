import { describe, it, expect } from 'vitest'
import { formatTickerSymbol } from './ticker-symbol'

describe('formatTickerSymbol', () => {
  it('strips exchange and currency suffix from Lightyear-style symbols', () => {
    expect(formatTickerSymbol('AIFS:GER:EUR')).toBe('AIFS')
  })

  it('strips a single colon suffix', () => {
    expect(formatTickerSymbol('VUAA:LN')).toBe('VUAA')
  })

  it('returns the input unchanged when no colon is present', () => {
    expect(formatTickerSymbol('VUAA')).toBe('VUAA')
  })

  it('returns an empty string for empty input', () => {
    expect(formatTickerSymbol('')).toBe('')
  })

  it('returns an empty string when the symbol starts with a colon', () => {
    expect(formatTickerSymbol(':GER:EUR')).toBe('')
  })
})
