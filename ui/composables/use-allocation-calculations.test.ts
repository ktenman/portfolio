import { describe, it, expect } from 'vitest'
import { useAllocationCalculations } from './use-allocation-calculations'
import type { EtfDetailDto } from '../models/generated/domain-models'
import { Currency } from '../models/generated/domain-models'
import type { AllocationInput } from '../components/diversification/types'

const makeEtf = (id: number, price: number): EtfDetailDto => ({
  instrumentId: id,
  symbol: `ETF${id}`,
  name: `ETF ${id}`,
  allocation: 0,
  ter: 0,
  annualReturn: 0,
  currentPrice: price,
  fundCurrency: Currency.EUR,
})

describe('useAllocationCalculations - buy-only mode', () => {
  const etfs: EtfDetailDto[] = [makeEtf(1, 100), makeEtf(2, 100)]

  const makeProps = (overrides: {
    allocations: AllocationInput[]
    totalInvestment: number
    currentHoldingsTotal: number
    selectedPlatforms: string[]
    optimizeEnabled?: boolean
    buyOnlyEnabled: boolean
  }) => ({
    allocations: overrides.allocations,
    availableEtfs: etfs,
    totalInvestment: overrides.totalInvestment,
    currentHoldingsTotal: overrides.currentHoldingsTotal,
    selectedPlatforms: overrides.selectedPlatforms,
    optimizeEnabled: overrides.optimizeEnabled ?? false,
    actionDisplayMode: 'units' as const,
    buyOnlyEnabled: overrides.buyOnlyEnabled,
  })

  it('reports Sell for over-target allocation when buy-only is off', () => {
    const props = makeProps({
      allocations: [
        { instrumentId: 1, value: 50, currentValue: 900 },
        { instrumentId: 2, value: 50, currentValue: 100 },
      ],
      totalInvestment: 0,
      currentHoldingsTotal: 1000,
      selectedPlatforms: ['LHV'],
      buyOnlyEnabled: false,
    })
    const calc = useAllocationCalculations(props)

    const over = calc.getBaseRebalanceData(props.allocations[0])
    expect(over.isBuy).toBe(false)
    expect(over.difference).toBeLessThan(0)
    expect(over.units).toBeGreaterThan(0)
  })

  it('zeroes out Sell actions when buy-only is on', () => {
    const props = makeProps({
      allocations: [
        { instrumentId: 1, value: 50, currentValue: 900 },
        { instrumentId: 2, value: 50, currentValue: 100 },
      ],
      totalInvestment: 0,
      currentHoldingsTotal: 1000,
      selectedPlatforms: ['LHV'],
      buyOnlyEnabled: true,
    })
    const calc = useAllocationCalculations(props)

    const over = calc.getBaseRebalanceData(props.allocations[0])
    expect(over.isBuy).toBe(true)
    expect(over.difference).toBe(0)
    expect(over.units).toBe(0)
    expect(calc.hasRebalanceAction(props.allocations[0])).toBe(false)
  })

  it('still reports Buy for under-target allocation when buy-only is on', () => {
    const props = makeProps({
      allocations: [
        { instrumentId: 1, value: 50, currentValue: 900 },
        { instrumentId: 2, value: 50, currentValue: 100 },
      ],
      totalInvestment: 400,
      currentHoldingsTotal: 1000,
      selectedPlatforms: ['LHV'],
      buyOnlyEnabled: true,
    })
    const calc = useAllocationCalculations(props)

    const under = calc.getBaseRebalanceData(props.allocations[1])
    expect(under.isBuy).toBe(true)
    expect(under.difference).toBeGreaterThan(0)
    expect(calc.hasRebalanceAction(props.allocations[1])).toBe(true)
  })

  it('suppresses Buy actions when buy-only is on and no new investment', () => {
    const props = makeProps({
      allocations: [
        { instrumentId: 1, value: 50, currentValue: 900 },
        { instrumentId: 2, value: 50, currentValue: 100 },
      ],
      totalInvestment: 0,
      currentHoldingsTotal: 1000,
      selectedPlatforms: ['LHV'],
      buyOnlyEnabled: true,
    })
    const calc = useAllocationCalculations(props)

    const under = calc.getBaseRebalanceData(props.allocations[1])
    expect(under.units).toBe(0)
    expect(calc.hasRebalanceAction(props.allocations[1])).toBe(false)
  })

  it('suppresses Sell actions in budget-aware rebalance when buy-only is on', () => {
    const props = makeProps({
      allocations: [
        { instrumentId: 1, value: 50, currentValue: 900 },
        { instrumentId: 2, value: 50, currentValue: 100 },
      ],
      totalInvestment: 500,
      currentHoldingsTotal: 1000,
      selectedPlatforms: ['LHV'],
      buyOnlyEnabled: true,
    })
    const calc = useAllocationCalculations(props)

    const over = calc.getRebalanceData(props.allocations[0])
    expect(over.isBuy).toBe(true)
    expect(over.units).toBe(0)
    expect(calc.hasRebalanceAction(props.allocations[0])).toBe(false)

    const under = calc.getRebalanceData(props.allocations[1])
    expect(under.isBuy).toBe(true)
    expect(under.units).toBeGreaterThan(0)
  })
})
