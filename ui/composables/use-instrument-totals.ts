import { computed, type Ref, type ComputedRef } from 'vue'
import type { InstrumentDto } from '../models/generated/domain-models'

export interface UseInstrumentTotalsReturn {
  totalInvested: ComputedRef<number>
  totalValue: ComputedRef<number>
  totalProfit: ComputedRef<number>
  totalUnrealizedProfit: ComputedRef<number>
  totalChangeAmount: ComputedRef<number>
  totalChangePercent: ComputedRef<number>
}

export function useInstrumentTotals(instruments: Ref<InstrumentDto[]>): UseInstrumentTotalsReturn {
  const totalInvested = computed(() => {
    return instruments.value.reduce((sum, instrument) => {
      return sum + (instrument.totalInvestment || 0)
    }, 0)
  })

  const totalValue = computed(() => {
    return instruments.value.reduce((sum, instrument) => {
      return sum + (instrument.currentValue || 0)
    }, 0)
  })

  const totalProfit = computed(() => {
    return instruments.value.reduce((sum, instrument) => {
      return sum + (instrument.profit || 0)
    }, 0)
  })

  const totalUnrealizedProfit = computed(() => {
    return instruments.value.reduce((sum, instrument) => {
      return sum + (instrument.unrealizedProfit || 0)
    }, 0)
  })

  const totalChangeAmount = computed(() => {
    return instruments.value.reduce((sum, instrument) => {
      return sum + (instrument.priceChangeAmount || 0)
    }, 0)
  })

  const totalChangePercent = computed(() => {
    const previousTotalValue = totalValue.value - totalChangeAmount.value
    if (previousTotalValue === 0) return 0
    return (totalChangeAmount.value / previousTotalValue) * 100
  })

  return {
    totalInvested,
    totalValue,
    totalProfit,
    totalUnrealizedProfit,
    totalChangeAmount,
    totalChangePercent,
  }
}
