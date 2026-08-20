import { computed } from 'vue'
import type { AllocationInput, AllocationProps } from '../components/diversification/types'
import {
  calculateTargetValue,
  calculateUnitsFromAmount,
  calculateBudgetConstrainedRebalance,
  optimizeRebalanceUnits,
} from '../utils/diversification-calculations'

interface RebalanceData {
  currentValue: number
  currentPercent: number
  targetValue: number
  difference: number
  isBuy: boolean
  units: number
  unused: number
  price: number | null
}

export function useRebalanceCalculations(
  props: AllocationProps,
  getEtfPrice: (instrumentId: number) => number | null
) {
  const showRebalanceColumns = computed(() => props.selectedPlatforms.length > 0)

  const applyBuyOnlyClamp = (rawDifference: number): number => {
    if (props.buyOnlyEnabled && (rawDifference < 0 || props.totalInvestment <= 0)) return 0
    return rawDifference
  }

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
    const difference = applyBuyOnlyClamp(targetValue - currentValue)
    const isBuy = difference >= 0
    const absoluteDifference = Math.abs(difference)
    const units = calculateUnitsFromAmount(absoluteDifference, price ?? 0)
    const unused = absoluteDifference - units * (price ?? 0)
    return { currentValue, currentPercent, targetValue, difference, isBuy, units, unused, price }
  }

  const rebalanceEntries = computed(() =>
    props.allocations
      .filter(a => a.instrumentId > 0 && a.value > 0)
      .map(a => ({ ...getBaseRebalanceData(a), id: a.instrumentId }))
  )

  const budgetAwareRebalance = computed(() => {
    if (!showRebalanceColumns.value || props.totalInvestment <= 0) return null
    const entries = rebalanceEntries.value.map(entry => ({ ...entry, price: entry.price ?? 0 }))
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
    return optimizeRebalanceUnits(rebalanceEntries.value)
  })

  const optimizedRebalance = computed(() => optimizedRebalanceResult.value.allocations)

  const fractionalRebalanceAmounts = computed<Map<number, number>>(() => {
    const result = new Map<number, number>()
    if (!showRebalanceColumns.value) return result
    const entries = rebalanceEntries.value
    const totalBuyNeeded = entries
      .filter(e => e.isBuy && e.difference > 0)
      .reduce((sum, e) => sum + e.difference, 0)
    const sellAmount = entries
      .filter(e => !e.isBuy)
      .reduce((sum, e) => sum + Math.abs(e.difference), 0)
    const availableBudget = props.totalInvestment + sellAmount
    const constrained = totalBuyNeeded > availableBudget && totalBuyNeeded > 0
    for (const entry of entries) {
      if (entry.isBuy) {
        const amount = constrained
          ? (entry.difference / totalBuyNeeded) * availableBudget
          : entry.difference
        result.set(entry.id, amount)
      } else {
        result.set(entry.id, Math.abs(entry.difference))
      }
    }
    return result
  })

  const getRebalanceFractionalAmount = (allocation: AllocationInput): number =>
    fractionalRebalanceAmounts.value.get(allocation.instrumentId) ?? 0

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

  const hasRebalanceAction = (allocation: AllocationInput): boolean => {
    if (props.actionDisplayMode === 'amount') {
      return getRebalanceFractionalAmount(allocation) > 0.01
    }
    return getRebalanceData(allocation).units > 0
  }

  const rebalanceUnusedTotal = computed(() => {
    if (budgetAwareRebalance.value) return budgetAwareRebalance.value.totalRemaining
    if (props.optimizeEnabled) return optimizedRebalanceResult.value.totalRemaining
    return props.allocations.reduce((sum, allocation) => {
      const data = getRebalanceData(allocation)
      return sum + (data.units > 0 ? data.unused : 0)
    }, 0)
  })

  const calcAfterValue = (allocation: AllocationInput, data: RebalanceData): number => {
    const currentVal = allocation.currentValue ?? 0
    const tradeAmount =
      props.actionDisplayMode === 'amount'
        ? getRebalanceFractionalAmount(allocation)
        : data.units * (data.price ?? 0)
    return data.isBuy ? currentVal + tradeAmount : currentVal - tradeAmount
  }

  const totalAfterValue = computed(() =>
    props.allocations.reduce((sum, a) => sum + calcAfterValue(a, getRebalanceData(a)), 0)
  )

  const totalAfterValueForSort = computed(() =>
    props.allocations.reduce((sum, a) => sum + calcAfterValue(a, getBaseRebalanceData(a)), 0)
  )

  const afterPercentOf = (
    allocation: AllocationInput,
    data: RebalanceData,
    total: number
  ): number => (total <= 0 ? 0 : (calcAfterValue(allocation, data) / total) * 100)

  const getAfterPercent = (allocation: AllocationInput): number =>
    afterPercentOf(allocation, getRebalanceData(allocation), totalAfterValue.value)

  const getAfterPercentForSort = (allocation: AllocationInput): number =>
    afterPercentOf(allocation, getBaseRebalanceData(allocation), totalAfterValueForSort.value)

  return {
    showRebalanceColumns,
    rebalanceUnusedTotal,
    getBaseRebalanceData,
    getRebalanceData,
    getRebalanceFractionalAmount,
    hasRebalanceAction,
    getAfterPercent,
    getAfterPercentForSort,
  }
}
