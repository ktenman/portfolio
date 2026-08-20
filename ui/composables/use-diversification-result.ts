import { ref, computed } from 'vue'
import type { Ref } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { diversificationService } from '../services/api'
import type { DiversificationCalculatorResponseDto } from '../models/generated/domain-models'
import type { AllocationInput } from '../components/diversification/types'

export const getErrorMessage = (e: unknown): string => {
  if (e instanceof Error) {
    if (e.message.includes('Network Error') || e.message.includes('fetch')) {
      return 'Unable to connect to the server. Please check your internet connection and try again.'
    }
    if (e.message.includes('timeout')) {
      return 'The request timed out. Please try again.'
    }
    if (e.message.includes('500') || e.message.includes('Internal Server Error')) {
      return 'A server error occurred. Please try again later.'
    }
    return e.message
  }
  return 'An unexpected error occurred. Please try again.'
}

const toBreakdown = <T extends { percentage: number }>(
  items: T[] | undefined,
  getName: (item: T) => string
) =>
  items?.map(item => ({
    key: getName(item),
    name: getName(item),
    percentage: item.percentage,
  })) ?? []

export function useDiversificationResult(allocations: Ref<AllocationInput[]>) {
  const result = ref<DiversificationCalculatorResponseDto | null>(null)
  const error = ref('')
  const isCalculating = ref(false)

  const calculateDiversification = async () => {
    const validAllocations = allocations.value.filter(a => a.instrumentId > 0 && a.value > 0)
    if (validAllocations.length < 1) {
      result.value = null
      return
    }
    isCalculating.value = true
    error.value = ''
    try {
      const requestAllocations = validAllocations.map(a => ({
        instrumentId: a.instrumentId,
        percentage: a.value,
      }))
      result.value = await diversificationService.calculate(requestAllocations)
    } catch (e) {
      error.value = getErrorMessage(e)
      result.value = null
    } finally {
      isCalculating.value = false
    }
  }

  const debouncedCalculate = useDebounceFn(calculateDiversification, 500)

  const holdingsBreakdown = computed(() => toBreakdown(result.value?.holdings, h => h.name))
  const sectorsBreakdown = computed(() => toBreakdown(result.value?.sectors, s => s.sector))
  const countriesBreakdown = computed(() =>
    toBreakdown(result.value?.countries, c => c.countryName)
  )

  return {
    result,
    error,
    isCalculating,
    debouncedCalculate,
    holdingsBreakdown,
    sectorsBreakdown,
    countriesBreakdown,
  }
}
