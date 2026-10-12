import { describe, expect, it } from 'vitest'
import { etfPortfolioValues } from './etf-portfolio-value'
import type { EtfDetailDto } from '../models/generated/domain-models'
import { createInstrumentDto } from '../tests/fixtures'

const etf = (instrumentId: number) => ({ instrumentId }) as EtfDetailDto

describe('etfPortfolioValues', () => {
  it('reads a regular etf value by its own instrument id', () => {
    const values = etfPortfolioValues([etf(1)], [createInstrumentDto({ id: 1, currentValue: 500 })])

    expect(values.get(1)).toBe(500)
  })

  it('returns zero for an etf absent from the instruments', () => {
    expect(etfPortfolioValues([etf(7)], []).get(7)).toBe(0)
  })
})
