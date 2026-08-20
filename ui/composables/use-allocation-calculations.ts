import { computed } from 'vue'
import type { AllocationInput, AllocationProps } from '../components/diversification/types'
import { useRebalanceCalculations } from './use-rebalance-calculations'
import {
  calculateInvestmentAmount,
  calculateUnitsFromAmount,
  optimizeInvestmentAllocation,
  formatEuroAmount,
} from '../utils/diversification-calculations'

export function useAllocationCalculations(props: AllocationProps) {
  const etfMap = computed(() => new Map(props.availableEtfs.map(e => [e.instrumentId, e])))
  const findEtf = (instrumentId: number) => etfMap.value.get(instrumentId)
  const getEtfName = (instrumentId: number) => findEtf(instrumentId)?.name || ''
  const getEtfTer = (instrumentId: number) => findEtf(instrumentId)?.ter ?? null
  const getEtfReturn = (instrumentId: number) => findEtf(instrumentId)?.annualReturn ?? null
  const getEtfPrice = (instrumentId: number) => findEtf(instrumentId)?.currentPrice ?? null
  const getEtfSymbol = (instrumentId: number) => findEtf(instrumentId)?.symbol || ''
  const getEtfFundCurrency = (instrumentId: number): string | null =>
    findEtf(instrumentId)?.fundCurrency ?? null

  const showInvestmentColumns = computed(() => props.totalInvestment > 0)

  const {
    showRebalanceColumns,
    rebalanceUnusedTotal,
    getBaseRebalanceData,
    getRebalanceData,
    getRebalanceFractionalAmount,
    hasRebalanceAction,
    getAfterPercent,
    getAfterPercentForSort,
  } = useRebalanceCalculations(props, getEtfPrice)

  const showRebalanceActionColumn = computed(
    () =>
      showRebalanceColumns.value && (props.totalInvestment > 0 || props.currentHoldingsTotal > 0)
  )

  const showActionColumns = computed(
    () => showInvestmentColumns.value || showRebalanceActionColumn.value
  )

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

  const getUnits = ({ instrumentId, value: percentage }: AllocationInput): number => {
    if (props.optimizeEnabled && optimizedAllocation.value.has(instrumentId)) {
      return optimizedAllocation.value.get(instrumentId) ?? 0
    }
    const price = getEtfPrice(instrumentId)
    if (!percentage || !price || price <= 0 || props.totalInvestment <= 0) return 0
    return calculateUnitsFromAmount(
      calculateInvestmentAmount(props.totalInvestment, percentage),
      price
    )
  }

  const getUnused = (allocation: AllocationInput): number => {
    const units = getUnits(allocation)
    const price = getEtfPrice(allocation.instrumentId)
    if (!price || units === 0) return 0
    const allocated = calculateInvestmentAmount(props.totalInvestment, allocation.value)
    return allocated - units * price
  }

  const formatActionValue = (allocation: AllocationInput): string => {
    if (props.actionDisplayMode === 'amount') {
      return formatEuroAmount(getRebalanceFractionalAmount(allocation))
    }
    return getRebalanceData(allocation).units.toString()
  }

  const formatAction = (allocation: AllocationInput): string => {
    if (props.actionDisplayMode === 'amount') {
      return formatEuroAmount(calculateInvestmentAmount(props.totalInvestment, allocation.value))
    }
    const units = getUnits(allocation)
    if (units === 0) return '-'
    return units.toString()
  }

  const formatUnused = (allocation: AllocationInput): string => {
    if (props.actionDisplayMode === 'amount') return '-'
    if (getUnits(allocation) === 0) return '-'
    return formatEuroAmount(getUnused(allocation))
  }

  const totalUnused = computed(() => {
    if (!showActionColumns.value) return 0
    if (props.actionDisplayMode === 'amount') return 0
    if (showRebalanceColumns.value) return rebalanceUnusedTotal.value
    return props.allocations.reduce((sum, allocation) => sum + getUnused(allocation), 0)
  })

  const getComputedAmount = (allocation: AllocationInput): number => {
    if (!showRebalanceColumns.value)
      return calculateInvestmentAmount(props.totalInvestment, allocation.value)
    if (props.actionDisplayMode === 'amount') return getRebalanceFractionalAmount(allocation)
    const data = getRebalanceData(allocation)
    return data.units * (data.price ?? 0)
  }

  const getActionSortValue = (allocation: AllocationInput): number => {
    const base = getBaseRebalanceData(allocation)
    if (showRebalanceColumns.value) {
      if (props.actionDisplayMode === 'amount') {
        const amount = getRebalanceFractionalAmount(allocation)
        return base.isBuy ? amount : -amount
      }
      return base.difference
    }
    if (props.actionDisplayMode === 'amount')
      return calculateInvestmentAmount(props.totalInvestment, allocation.value)
    return base.units
  }

  return {
    getEtfName,
    getEtfPrice,
    getEtfTer,
    getEtfReturn,
    getEtfSymbol,
    getEtfFundCurrency,
    showRebalanceColumns,
    showRebalanceActionColumn,
    showActionColumns,
    getBaseRebalanceData,
    getRebalanceData,
    getAfterPercent,
    getAfterPercentForSort,
    getUnits,
    getUnused,
    hasRebalanceAction,
    formatActionValue,
    formatAction,
    formatUnused,
    totalUnused,
    getComputedAmount,
    getActionSortValue,
  }
}
