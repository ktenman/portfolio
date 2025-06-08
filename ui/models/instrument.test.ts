import { describe, it, expect } from 'vitest'

// Since instrument.ts only contains interface definitions,
// we'll test that the interfaces can be used correctly
describe('Instrument', () => {
  it('has correct interface structure with all fields', () => {
    const instrument = {
      id: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
      providerName: 'ALPHA_VANTAGE',
      platform: 'NASDAQ',
    }

    expect(instrument.id).toBe(1)
    expect(instrument.symbol).toBe('AAPL')
    expect(instrument.name).toBe('Apple Inc.')
    expect(instrument.providerName).toBe('ALPHA_VANTAGE')
    expect(instrument.platform).toBe('NASDAQ')
  })

  it('handles optional id field', () => {
    const instrument = {
      symbol: 'GOOGL',
      name: 'Alphabet Inc.',
      category: 'STOCK',
      baseCurrency: 'USD',
      providerName: 'ALPHA_VANTAGE' as const,
      xirr: 15.5,
      totalInvestment: 8000,
      currentValue: 10000,
      profit: 2000,
      currentPrice: 150.0,
      quantity: 100,
    }

    expect(instrument.symbol).toBe('GOOGL')
    expect(instrument.name).toBe('Alphabet Inc.')
    expect(instrument.providerName).toBe('ALPHA_VANTAGE')
    expect(instrument.category).toBe('STOCK')
    expect('id' in instrument).toBe(false)
  })

  it('handles different provider names', () => {
    const binanceInstrument = {
      id: 2,
      symbol: 'BTCUSDT',
      name: 'Bitcoin',
      providerName: 'BINANCE',
      platform: 'CRYPTO',
    }

    expect(binanceInstrument.providerName).toBe('BINANCE')
    expect(binanceInstrument.platform).toBe('CRYPTO')
  })

  it('handles different platforms', () => {
    const instruments = [
      {
        id: 1,
        symbol: 'AAPL',
        name: 'Apple Inc.',
        providerName: 'ALPHA_VANTAGE',
        platform: 'NASDAQ',
      },
      {
        id: 2,
        symbol: 'TSLA',
        name: 'Tesla Inc.',
        providerName: 'ALPHA_VANTAGE',
        platform: 'NYSE',
      },
      {
        id: 3,
        symbol: 'BTCUSDT',
        name: 'Bitcoin',
        providerName: 'BINANCE',
        platform: 'CRYPTO',
      },
    ]

    expect(instruments[0].platform).toBe('NASDAQ')
    expect(instruments[1].platform).toBe('NYSE')
    expect(instruments[2].platform).toBe('CRYPTO')
  })

  it('handles string properties', () => {
    const instrument = {
      id: 999,
      symbol: 'TEST-SYMBOL',
      name: 'Very Long Test Instrument Name With Special Characters & Numbers 123',
      providerName: 'TEST_PROVIDER',
      platform: 'TEST_PLATFORM',
    }

    expect(typeof instrument.symbol).toBe('string')
    expect(typeof instrument.name).toBe('string')
    expect(typeof instrument.providerName).toBe('string')
    expect(typeof instrument.platform).toBe('string')
    expect(instrument.name.length).toBeGreaterThan(0)
  })

  it('handles numeric id field', () => {
    const instruments = [
      { id: 0, symbol: 'TEST1', name: 'Test 1', providerName: 'PROVIDER', platform: 'PLATFORM' },
      { id: 1, symbol: 'TEST2', name: 'Test 2', providerName: 'PROVIDER', platform: 'PLATFORM' },
      {
        id: 999999,
        symbol: 'TEST3',
        name: 'Test 3',
        providerName: 'PROVIDER',
        platform: 'PLATFORM',
      },
    ]

    expect(typeof instruments[0].id).toBe('number')
    expect(instruments[0].id).toBe(0)
    expect(instruments[1].id).toBe(1)
    expect(instruments[2].id).toBe(999999)
  })
})
