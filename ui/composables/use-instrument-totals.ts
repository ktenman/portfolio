import { computed, type Ref, type ComputedRef } from 'vue'
import type { InstrumentDto } from '../models/generated/domain-models'

export interface UseInstrumentTotalsReturn {
  totalInvested: ComputedRef<number>
  totalValue: ComputedRef<number>
  totalProfit: ComputedRef<number>
  totalUnrealizedProfit: ComputedRef<number>
  totalChangeAmount: ComputedRef<number>
  totalChangePercent: ComputedRef<number>
  totalTer: ComputedRef<number>
  totalAnnualReturn: ComputedRef<number | null>
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

  const totalTer = computed(() => {
    if (totalValue.value === 0) return 0
    return instruments.value.reduce((sum, instrument) => {
      const weight = (instrument.currentValue || 0) / totalValue.value
      const ter = instrument.ter ?? 0
      return sum + ter * weight
    }, 0)
  })

  const totalAnnualReturn = computed(() => {
    if (totalValue.value === 0) return null
    const instrumentsWithAnnualReturn = instruments.value.filter(
      i => i.xirrAnnualReturn !== null && i.xirrAnnualReturn !== undefined
    )
    if (instrumentsWithAnnualReturn.length === 0) return null
    const totalWithAnnualReturn = instrumentsWithAnnualReturn.reduce(
      (sum, i) => sum + (i.currentValue || 0),
      0
    )
    if (totalWithAnnualReturn === 0) return null
    return instrumentsWithAnnualReturn.reduce((sum, instrument) => {
      const weight = (instrument.currentValue || 0) / totalWithAnnualReturn
      return sum + (instrument.xirrAnnualReturn ?? 0) * weight
    }, 0)
  })

  return {
    totalInvested,
    totalValue,
    totalProfit,
    totalUnrealizedProfit,
    totalChangeAmount,
    totalChangePercent,
    totalTer,
    totalAnnualReturn,
  }
}
