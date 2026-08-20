import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useInstrumentTotals, type UseInstrumentTotalsReturn } from './use-instrument-totals'
import type { InstrumentDto } from '../models/generated/domain-models'
import { createInstrumentDto } from '../tests/fixtures'

const totalOf = (
  key: keyof UseInstrumentTotalsReturn,
  holdings: readonly Partial<InstrumentDto>[]
) => useInstrumentTotals(ref(holdings.map(h => createInstrumentDto(h))))[key].value

describe('useInstrumentTotals', () => {
  it.each([
    [
      'totalInvested',
      [{ totalInvestment: 1000 }, { totalInvestment: 2000 }, { totalInvestment: 3000 }],
      6000,
    ],
    ['totalInvested', [], 0],
    ['totalValue', [{ currentValue: 1500 }, { currentValue: 2500 }, { currentValue: 3500 }], 7500],
    ['totalValue', [], 0],
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
  ] as const)('%s adds %j up to %s', (key, holdings, expected) => {
    expect(totalOf(key, holdings)).toBe(expected)
  })

  it('should count a holding with no totalInvestment as zero', () => {
    expect(
      totalOf('totalInvested', [
        { totalInvestment: 1000 },
        { totalInvestment: undefined },
        { totalInvestment: 2000 },
      ])
    ).toBe(3000)
  })

  it('should count a holding with no currentValue as zero', () => {
    expect(totalOf('totalValue', [{ currentValue: 1000 }, { currentValue: undefined }])).toBe(1000)
  })

  it.each([
    ['totalChangePercent', [{ currentValue: 1000, priceChangeAmount: 100 }], 10],
    ['totalChangePercent', [{ currentValue: 100, priceChangeAmount: 100 }], 100],
    ['totalChangePercent', [{ currentValue: 1000, priceChangeAmount: -100 }], -10],
    ['totalChangePercent', [{ currentValue: 0, priceChangeAmount: 100 }], 0],
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
  ] as const)('%s weights %j by current value to %s', (key, holdings, expected) => {
    expect(totalOf(key, holdings)).toBeCloseTo(expected, 4)
  })

  it.each([
    [
      [
        { currentValue: 5000, xirrAnnualReturn: null },
        { currentValue: 5000, xirrAnnualReturn: null },
      ],
    ],
    [[{ currentValue: 0, xirrAnnualReturn: 0.15 }]],
    [[]],
  ] as const)('totalAnnualReturn reports null for %j', holdings => {
    expect(totalOf('totalAnnualReturn', holdings)).toBeNull()
  })

  it('should recompute when the instrument list is replaced', () => {
    const instruments = ref([createInstrumentDto({ totalInvestment: 1000 })])
    const { totalInvested } = useInstrumentTotals(instruments)

    instruments.value = [
      createInstrumentDto({ totalInvestment: 1000 }),
      createInstrumentDto({ totalInvestment: 2000 }),
    ]

    expect(totalInvested.value).toBe(3000)
  })

  it('should recompute when an instrument is added', () => {
    const instruments = ref([createInstrumentDto({ currentValue: 500 })])
    const { totalValue } = useInstrumentTotals(instruments)

    instruments.value.push(createInstrumentDto({ currentValue: 300 }))

    expect(totalValue.value).toBe(800)
  })
})
