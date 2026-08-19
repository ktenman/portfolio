import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useInstrumentTotals, type UseInstrumentTotalsReturn } from './use-instrument-totals'
import type { InstrumentDto } from '../models/generated/domain-models'

const createInstrument = (overrides: Partial<InstrumentDto> = {}): InstrumentDto => ({
  id: 1,
  symbol: 'AAPL',
  name: 'Apple Inc.',
  category: 'STOCK',
  baseCurrency: 'USD',
  currentPrice: 150,
  quantity: 10,
  currentValue: 1500,
  totalInvestment: 1000,
  profit: 500,
  realizedProfit: 0,
  unrealizedProfit: 500,
  priceChangeAmount: 50,
  priceChangePercent: 3.45,
  xirr: 0.15,
  providerName: 'FT',
  platforms: ['TRADING212'],
  ter: null,
  xirrAnnualReturn: null,
  firstTransactionDate: '2024-01-01',
  fundCurrency: null,
  ...overrides,
})

const totalOf = (key: keyof UseInstrumentTotalsReturn, holdings: Partial<InstrumentDto>[]) =>
  useInstrumentTotals(ref(holdings.map(createInstrument)))[key].value

describe('useInstrumentTotals', () => {
  it.each([
    [
      'totalInvested',
      [{ totalInvestment: 1000 }, { totalInvestment: 2000 }, { totalInvestment: 3000 }],
      6000,
    ],
    ['totalInvested', [], 0],
    [
      'totalInvested',
      [{ totalInvestment: 1000 }, { totalInvestment: undefined }, { totalInvestment: 2000 }],
      3000,
    ],
    ['totalValue', [{ currentValue: 1500 }, { currentValue: 2500 }, { currentValue: 3500 }], 7500],
    ['totalValue', [], 0],
    ['totalValue', [{ currentValue: 1000 }, { currentValue: undefined }], 1000],
    ['totalProfit', [{ profit: 100 }, { profit: 200 }, { profit: 300 }], 600],
    ['totalProfit', [{ profit: 500 }, { profit: -200 }, { profit: -100 }], 200],
    ['totalProfit', [], 0],
    ['totalUnrealizedProfit', [{ unrealizedProfit: 150 }, { unrealizedProfit: 250 }], 400],
    ['totalUnrealizedProfit', [{ unrealizedProfit: 300 }, { unrealizedProfit: -100 }], 200],
    ['totalUnrealizedProfit', [], 0],
    [
      'totalChangeAmount',
      [{ priceChangeAmount: 50 }, { priceChangeAmount: 75 }, { priceChangeAmount: 25 }],
      150,
    ],
    ['totalChangeAmount', [{ priceChangeAmount: 100 }, { priceChangeAmount: -50 }], 50],
    ['totalChangeAmount', [], 0],
    ['totalChangePercent', [{ currentValue: 1100, priceChangeAmount: 100 }], 10],
    ['totalChangePercent', [{ currentValue: 100, priceChangeAmount: 100 }], 0],
    ['totalChangePercent', [{ currentValue: 900, priceChangeAmount: -100 }], -10],
    ['totalChangePercent', [], 0],
    [
      'totalTer',
      [
        { currentValue: 10000, ter: 0.07 },
        { currentValue: 10000, ter: 0.22 },
      ],
      0.145,
    ],
    [
      'totalTer',
      [
        { currentValue: 30000, ter: 0.07 },
        { currentValue: 10000, ter: 0.22 },
      ],
      0.1075,
    ],
    [
      'totalTer',
      [
        { currentValue: 5000, ter: 0.2 },
        { currentValue: 5000, ter: null },
      ],
      0.1,
    ],
    ['totalTer', [{ currentValue: 0, ter: 0.15 }], 0],
    ['totalTer', [], 0],
    [
      'totalAnnualReturn',
      [
        { currentValue: 10000, xirrAnnualReturn: 0.1 },
        { currentValue: 10000, xirrAnnualReturn: 0.2 },
      ],
      0.15,
    ],
    [
      'totalAnnualReturn',
      [
        { currentValue: 30000, xirrAnnualReturn: 0.1 },
        { currentValue: 10000, xirrAnnualReturn: 0.3 },
      ],
      0.15,
    ],
    [
      'totalAnnualReturn',
      [
        { currentValue: 5000, xirrAnnualReturn: 0.2 },
        { currentValue: 5000, xirrAnnualReturn: null },
      ],
      0.2,
    ],
    [
      'totalAnnualReturn',
      [
        { currentValue: 5000, xirrAnnualReturn: null },
        { currentValue: 5000, xirrAnnualReturn: null },
      ],
      null,
    ],
    ['totalAnnualReturn', [{ currentValue: 0, xirrAnnualReturn: 0.15 }], null],
    ['totalAnnualReturn', [], null],
  ] as [keyof UseInstrumentTotalsReturn, Partial<InstrumentDto>[], number | null][])(
    '%s of %j → %s',
    (key, holdings, expected) => {
      const total = totalOf(key, holdings)
      if (expected === null) return expect(total).toBeNull()
      expect(total).toBeCloseTo(expected, 4)
    }
  )

  it('should recompute when the instrument list is replaced', () => {
    const instruments = ref([createInstrument({ totalInvestment: 1000 })])
    const { totalInvested } = useInstrumentTotals(instruments)

    instruments.value = [
      createInstrument({ totalInvestment: 1000 }),
      createInstrument({ totalInvestment: 2000 }),
    ]

    expect(totalInvested.value).toBe(3000)
  })

  it('should recompute when an instrument is added', () => {
    const instruments = ref([createInstrument({ currentValue: 500 })])
    const { totalValue } = useInstrumentTotals(instruments)

    instruments.value.push(createInstrument({ currentValue: 300 }))

    expect(totalValue.value).toBe(800)
  })
})
