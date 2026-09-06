import { describe, expect, it } from 'vitest'
import { etfPortfolioValues } from './etf-portfolio-value'
import type { EtfDetailDto } from '../models/generated/domain-models'
import { createInstrumentDto } from '../tests/fixtures'

const etf = (instrumentId: number, constituentSymbols: string[] = []) =>
  ({ instrumentId, constituentSymbols }) as EtfDetailDto
const instrument = (id: number, symbol: string, currentValue: number) =>
  createInstrumentDto({ id, symbol, currentValue })

describe('etfPortfolioValues', () => {
  it('reads a regular etf value by its own instrument id', () => {
    const values = etfPortfolioValues([etf(1)], [instrument(1, 'WEBN', 500)])

    expect(values.get(1)).toBe(500)
  })

  it('sums a synthetic fund value from its constituent symbols', () => {
    const values = etfPortfolioValues(
      [etf(20, ['BTCEUR', 'BNBEUR'])],
      [instrument(2, 'BTCEUR', 4500), instrument(13, 'BNBEUR', 86), instrument(20, 'TREZOR', 0)]
    )

    expect(values.get(20)).toBe(4586)
  })

  it('returns zero for an etf absent from the instruments', () => {
    expect(etfPortfolioValues([etf(7)], []).get(7)).toBe(0)
  })
})
