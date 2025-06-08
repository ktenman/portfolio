import { describe, it, expect } from 'vitest'
import { Platform } from './platform'

describe('Platform', () => {
  it('exports all platform constants', () => {
    expect(Platform.AVIVA).toBe('AVIVA')
    expect(Platform.BINANCE).toBe('BINANCE')
    expect(Platform.COINBASE).toBe('COINBASE')
    expect(Platform.LHV).toBe('LHV')
    expect(Platform.LIGHTYEAR).toBe('LIGHTYEAR')
    expect(Platform.SWEDBANK).toBe('SWEDBANK')
    expect(Platform.TRADING212).toBe('TRADING212')
    expect(Platform.UNKNOWN).toBe('UNKNOWN')
  })

  it('has the expected number of platform values', () => {
    const platformValues = Object.values(Platform)
    expect(platformValues).toHaveLength(8)
  })
})
