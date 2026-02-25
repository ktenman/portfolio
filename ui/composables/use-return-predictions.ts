import { computed, ref, watch, type Ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { portfolioSummaryService } from '../services/portfolio-summary-service'
import { useAuthState } from './use-auth-state'

export function useReturnPredictions(monthlyContribution?: Ref<number | undefined>) {
  const { isAuthenticated } = useAuthState()
  const queryClient = useQueryClient()
  const activeContribution = ref(monthlyContribution?.value)

  if (monthlyContribution) {
    watch(monthlyContribution, value => {
      activeContribution.value = value
      queryClient.invalidateQueries({ queryKey: ['portfolio-summary', 'predictions'] })
    })
  }

  const { data, isLoading, error } = useQuery({
    queryKey: ['portfolio-summary', 'predictions'],
    queryFn: () => portfolioSummaryService.getPredictions(activeContribution.value),
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
