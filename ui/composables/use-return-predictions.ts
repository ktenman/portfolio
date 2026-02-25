import { computed, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { portfolioSummaryService } from '../services/portfolio-summary-service'
import { useAuthState } from './use-auth-state'

export function useReturnPredictions(customMonthlyContribution?: Ref<number | undefined>) {
  const { isAuthenticated } = useAuthState()

  const contributionValue = computed(() => customMonthlyContribution?.value)

  const { data, isLoading, error } = useQuery({
    queryKey: ['portfolio-summary', 'predictions', contributionValue] as const,
    queryFn: () => portfolioSummaryService.getPredictions(contributionValue.value),
    enabled: isAuthenticated,
    staleTime: 3 * 60 * 1000,
  })

  const predictions = computed(() => data.value?.predictions ?? [])
  const hasSufficientData = computed(() => predictions.value.length > 0)
  const dataPointCount = computed(() => data.value?.dataPointCount ?? 0)
  const currentValue = computed(() => data.value?.currentValue ?? 0)
  const monthlyInvestment = computed(() => data.value?.monthlyInvestment ?? 0)

  return {
    predictions,
    hasSufficientData,
    dataPointCount,
    currentValue,
    monthlyInvestment,
    isLoading,
    error: computed(() => error.value?.message ?? null),
  }
}
