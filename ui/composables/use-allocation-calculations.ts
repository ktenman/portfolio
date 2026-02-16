import { computed } from 'vue'
import type { EtfDetailDto } from '../models/generated/domain-models'
import type { AllocationInput, ActionDisplayMode } from '../components/diversification/types'
import {
  calculateTargetValue,
  calculateInvestmentAmount,
  calculateUnitsFromAmount,
  calculateBudgetConstrainedRebalance,
  optimizeRebalanceUnits,
  optimizeInvestmentAllocation,
  formatEuroAmount,
} from '../utils/diversification-calculations'

export interface RebalanceData {
  currentValue: number
  currentPercent: number
  targetValue: number
  difference: number
  isBuy: boolean
  units: number
  unused: number
  price: number | null
}

interface AllocationProps {
  readonly allocations: AllocationInput[]
  readonly availableEtfs: EtfDetailDto[]
  readonly totalInvestment: number
  readonly currentHoldingsTotal: number
  readonly selectedPlatform: string | null
  readonly optimizeEnabled: boolean
  readonly actionDisplayMode: ActionDisplayMode
  readonly inputMode: 'percentage' | 'amount'
}

export function useAllocationCalculations(props: AllocationProps) {
  const findEtf = (instrumentId: number) =>
    props.availableEtfs.find(e => e.instrumentId === instrumentId)
  const getEtfName = (instrumentId: number) => findEtf(instrumentId)?.name || ''
  const getEtfTer = (instrumentId: number) => findEtf(instrumentId)?.ter ?? null
  const getEtfReturn = (instrumentId: number) => findEtf(instrumentId)?.annualReturn ?? null
  const getEtfPrice = (instrumentId: number) => findEtf(instrumentId)?.currentPrice ?? null
  const getEtfSymbol = (instrumentId: number) => findEtf(instrumentId)?.symbol || ''

  const showInvestmentColumns = computed(
    () => props.inputMode === 'percentage' && props.totalInvestment > 0
  )
  const showRebalanceColumns = computed(() => !!props.selectedPlatform)
  const showRebalanceActionColumn = computed(
    () =>
      showRebalanceColumns.value && (props.totalInvestment > 0 || props.currentHoldingsTotal > 0)
  )

  const getBaseRebalanceData = (allocation: AllocationInput): RebalanceData => {
    const price = getEtfPrice(allocation.instrumentId)
    const currentValue = allocation.currentValue ?? 0
    const currentPercent =
      props.currentHoldingsTotal > 0 ? (currentValue / props.currentHoldingsTotal) * 100 : 0
    const targetValue = calculateTargetValue(
      props.currentHoldingsTotal,
      props.totalInvestment,
      allocation.value
    )
    const difference = targetValue - currentValue
    const isBuy = difference >= 0
    const absoluteDifference = Math.abs(difference)
    const units = calculateUnitsFromAmount(absoluteDifference, price ?? 0)
    const unused = absoluteDifference - units * (price ?? 0)
    return { currentValue, currentPercent, targetValue, difference, isBuy, units, unused, price }
  }

  const budgetAwareRebalance = computed(() => {
    if (!showRebalanceColumns.value || props.totalInvestment <= 0) return null
    const validAllocations = props.allocations.filter(a => a.instrumentId > 0 && a.value > 0)
    if (validAllocations.length === 0) return null
    const entries = validAllocations.map(a => {
      const price = getEtfPrice(a.instrumentId) ?? 0
      const currentValue = a.currentValue ?? 0
      const targetValue = calculateTargetValue(
        props.currentHoldingsTotal,
        props.totalInvestment,
        a.value
      )
      const difference = targetValue - currentValue
      return { id: a.instrumentId, price, difference, isBuy: difference >= 0 }
    })
    return calculateBudgetConstrainedRebalance(
      entries,
      props.totalInvestment,
      props.optimizeEnabled
    )
  })

  const optimizedRebalanceResult = computed(() => {
    const emptyResult = {
      allocations: new Map<number, { units: number; isBuy: boolean }>(),
      totalRemaining: 0,
    }
    if (!showRebalanceColumns.value || !props.optimizeEnabled) return emptyResult
    if (budgetAwareRebalance.value) return emptyResult
    const validAllocations = props.allocations.filter(a => a.instrumentId > 0 && a.value > 0)
    if (validAllocations.length === 0) return emptyResult
    const entries = validAllocations.map(a => ({
      ...getBaseRebalanceData(a),
      id: a.instrumentId,
    }))
    return optimizeRebalanceUnits(entries)
  })

  const optimizedRebalance = computed(() => optimizedRebalanceResult.value.allocations)

  const getRebalanceData = (allocation: AllocationInput): RebalanceData => {
    const base = getBaseRebalanceData(allocation)
    if (budgetAwareRebalance.value?.allocations.has(allocation.instrumentId)) {
      const data = budgetAwareRebalance.value.allocations.get(allocation.instrumentId)!
      return { ...base, units: data.units, isBuy: data.isBuy, unused: 0 }
    }
    if (!props.optimizeEnabled || !optimizedRebalance.value.has(allocation.instrumentId)) {
      return base
    }
    const optimized = optimizedRebalance.value.get(allocation.instrumentId)!
    const actualAmount = optimized.units * (base.price ?? 0)
    const unused = Math.abs(base.difference) - actualAmount
    return { ...base, units: optimized.units, isBuy: optimized.isBuy, unused: Math.max(0, unused) }
  }

  const calcAfterValue = (allocation: AllocationInput, data: RebalanceData): number => {
    const currentValue = allocation.currentValue ?? 0
    const tradeValue = data.units * (data.price ?? 0)
    return data.isBuy ? currentValue + tradeValue : currentValue - tradeValue
  }

  const totalAfterValue = computed(() =>
    props.allocations.reduce((sum, a) => sum + calcAfterValue(a, getRebalanceData(a)), 0)
  )

  const totalAfterValueForSort = computed(() =>
    props.allocations.reduce((sum, a) => sum + calcAfterValue(a, getBaseRebalanceData(a)), 0)
  )

  const getAfterPercent = (allocation: AllocationInput): number => {
    if (props.actionDisplayMode === 'amount') return allocation.value
    if (totalAfterValue.value <= 0) return 0
    const afterValue = calcAfterValue(allocation, getRebalanceData(allocation))
    return (afterValue / totalAfterValue.value) * 100
  }

  const getAfterPercentForSort = (allocation: AllocationInput): number => {
    if (props.actionDisplayMode === 'amount') return allocation.value
    if (totalAfterValueForSort.value <= 0) return 0
    const afterValue = calcAfterValue(allocation, getBaseRebalanceData(allocation))
    return (afterValue / totalAfterValueForSort.value) * 100
  }

  const calculateBaseInvestmentData = (percentage: number, price: number | null) => {
    if (!percentage || !price || price <= 0 || props.totalInvestment <= 0) {
      return { allocated: 0, units: 0, unused: 0 }
    }
    const allocated = calculateInvestmentAmount(props.totalInvestment, percentage)
    const units = calculateUnitsFromAmount(allocated, price)
    const unused = allocated - units * price
    return { allocated, units, unused }
  }

  const optimizedAllocation = computed(() => {
    if (!showInvestmentColumns.value || !props.optimizeEnabled) return new Map<number, number>()
    const validAllocations = props.allocations.filter(a => a.instrumentId > 0 && a.value > 0)
    if (validAllocations.length === 0) return new Map<number, number>()
    const entries = validAllocations.map(a => ({
      id: a.instrumentId,
      price: getEtfPrice(a.instrumentId) ?? 0,
      percentage: a.value,
    }))
    return optimizeInvestmentAllocation(entries, props.totalInvestment)
  })

  const getUnits = (instrumentId: number, percentage: number, price: number | null): number => {
    if (props.optimizeEnabled && optimizedAllocation.value.has(instrumentId)) {
      return optimizedAllocation.value.get(instrumentId) ?? 0
    }
    return calculateBaseInvestmentData(percentage, price).units
  }

  const getUnused = (instrumentId: number, percentage: number, price: number | null): number => {
    const units = getUnits(instrumentId, percentage, price)
    if (!price || units === 0) return 0
    const allocated = calculateInvestmentAmount(props.totalInvestment, percentage)
    return allocated - units * price
  }

  const getRebalanceAmount = (allocation: AllocationInput): number => {
    const data = getRebalanceData(allocation)
    return data.units * (data.price ?? 0)
  }

  const formatActionValue = (allocation: AllocationInput): string => {
    if (props.actionDisplayMode === 'amount') {
      return formatEuroAmount(getRebalanceAmount(allocation))
    }
    return getRebalanceData(allocation).units.toString()
  }

  const formatAction = (instrumentId: number, percentage: number, price: number | null): string => {
    if (props.actionDisplayMode === 'amount') {
      return formatEuroAmount(calculateInvestmentAmount(props.totalInvestment, percentage))
    }
    const units = getUnits(instrumentId, percentage, price)
    if (units === 0) return '-'
    return units.toString()
  }

  const formatUnused = (instrumentId: number, percentage: number, price: number | null): string => {
    if (props.actionDisplayMode === 'amount') return '-'
    const units = getUnits(instrumentId, percentage, price)
    if (units === 0) return '-'
    return formatEuroAmount(getUnused(instrumentId, percentage, price))
  }

  const totalUnused = computed(() => {
    if (!showInvestmentColumns.value && !showRebalanceActionColumn.value) return 0
    if (props.actionDisplayMode === 'amount') return 0
    if (budgetAwareRebalance.value) return budgetAwareRebalance.value.totalRemaining
    if (showRebalanceColumns.value && props.optimizeEnabled) {
      return optimizedRebalanceResult.value.totalRemaining
    }
    if (showRebalanceColumns.value) {
      return props.allocations.reduce((sum, allocation) => {
        const data = getRebalanceData(allocation)
        return sum + (data.units > 0 ? data.unused : 0)
      }, 0)
    }
    return props.allocations.reduce((sum, allocation) => {
      const price = getEtfPrice(allocation.instrumentId)
      const unused = getUnused(allocation.instrumentId, allocation.value, price)
      return sum + unused
    }, 0)
  })

  return {
    getEtfName,
    getEtfPrice,
    getEtfTer,
    getEtfReturn,
    getEtfSymbol,
    showInvestmentColumns,
    showRebalanceColumns,
    showRebalanceActionColumn,
    getBaseRebalanceData,
    getRebalanceData,
    getAfterPercent,
    getAfterPercentForSort,
    getUnits,
    getUnused,
    getRebalanceAmount,
    formatActionValue,
    formatAction,
    formatUnused,
    totalUnused,
  }
}
