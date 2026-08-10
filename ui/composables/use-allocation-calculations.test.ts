import { describe, it, expect } from 'vitest'
import { useAllocationCalculations, getRebalanceStatus } from './use-allocation-calculations'
import type { EtfDetailDto } from '../models/generated/domain-models'
import { Currency, RebalanceStatus } from '../models/generated/domain-models'
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
    rebalanceThresholds: {
      driftingThresholdRel: 10,
      rebalanceThresholdRel: 25,
      rebalanceThresholdAbs: 5,
    },
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

describe('getRebalanceStatus', () => {
  const thresholds = {
    driftingThresholdRel: 10,
    rebalanceThresholdRel: 25,
    rebalanceThresholdAbs: 5,
  }

  it('returns OK when relative drift below drifting threshold and absolute below rebalance', () => {
    expect(getRebalanceStatus(50, 50, thresholds)).toBe(RebalanceStatus.OK)
    expect(getRebalanceStatus(52, 50, thresholds)).toBe(RebalanceStatus.OK)
  })

  it('returns DRIFTING when relative drift between drifting and rebalance thresholds and absolute drift below threshold', () => {
    expect(getRebalanceStatus(12, 10, thresholds)).toBe(RebalanceStatus.DRIFTING)
    expect(getRebalanceStatus(8.5, 10, thresholds)).toBe(RebalanceStatus.DRIFTING)
  })

  it('returns REBALANCE when relative drift exceeds rebalance threshold even if absolute is small', () => {
    expect(getRebalanceStatus(13, 10, thresholds)).toBe(RebalanceStatus.REBALANCE)
    expect(getRebalanceStatus(7, 10, thresholds)).toBe(RebalanceStatus.REBALANCE)
  })

  it('returns REBALANCE when absolute drift exceeds threshold even if relative is small', () => {
    expect(getRebalanceStatus(85, 80, thresholds)).toBe(RebalanceStatus.REBALANCE)
    expect(getRebalanceStatus(56, 50, thresholds)).toBe(RebalanceStatus.REBALANCE)
    expect(getRebalanceStatus(44, 50, thresholds)).toBe(RebalanceStatus.REBALANCE)
  })

  it('returns REBALANCE when target is zero but current is greater than zero', () => {
    expect(getRebalanceStatus(5, 0, thresholds)).toBe(RebalanceStatus.REBALANCE)
  })

  it('returns OK when target and current are both zero', () => {
    expect(getRebalanceStatus(0, 0, thresholds)).toBe(RebalanceStatus.OK)
  })

  it('returns REBALANCE when current is zero but target is greater than zero', () => {
    expect(getRebalanceStatus(0, 50, thresholds)).toBe(RebalanceStatus.REBALANCE)
  })
})

describe('useAllocationCalculations - status and relDrift on RebalanceData', () => {
  const etfs: EtfDetailDto[] = [makeEtf(1, 100), makeEtf(2, 100)]

  it('attaches status REBALANCE and signed relDrift when allocation is over target', () => {
    const props = {
      allocations: [
        { instrumentId: 1, value: 50, currentValue: 700 },
        { instrumentId: 2, value: 50, currentValue: 300 },
      ],
      availableEtfs: etfs,
      totalInvestment: 0,
      currentHoldingsTotal: 1000,
      selectedPlatforms: ['LHV'],
      optimizeEnabled: false,
      buyOnlyEnabled: false,
      actionDisplayMode: 'units' as const,
      rebalanceThresholds: {
        driftingThresholdRel: 10,
        rebalanceThresholdRel: 25,
        rebalanceThresholdAbs: 5,
      },
    }
    const calc = useAllocationCalculations(props)

    const over = calc.getBaseRebalanceData(props.allocations[0])
    expect(over.status).toBe(RebalanceStatus.REBALANCE)
    expect(over.relDrift).toBeGreaterThan(0)
  })

  it('attaches status OK when allocation is within tolerance', () => {
    const props = {
      allocations: [
        { instrumentId: 1, value: 50, currentValue: 510 },
        { instrumentId: 2, value: 50, currentValue: 490 },
      ],
      availableEtfs: etfs,
      totalInvestment: 0,
      currentHoldingsTotal: 1000,
      selectedPlatforms: ['LHV'],
      optimizeEnabled: false,
      buyOnlyEnabled: false,
      actionDisplayMode: 'units' as const,
      rebalanceThresholds: {
        driftingThresholdRel: 10,
        rebalanceThresholdRel: 25,
        rebalanceThresholdAbs: 5,
      },
    }
    const calc = useAllocationCalculations(props)

    const data = calc.getBaseRebalanceData(props.allocations[0])
    expect(data.status).toBe(RebalanceStatus.OK)
  })
})
