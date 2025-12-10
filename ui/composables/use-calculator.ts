import { computed, onMounted, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useLocalStorage, watchDebounced } from '@vueuse/core'
import { utilityService } from '../services/utility-service'
import { CalculationResult } from '../models/calculation-result'
import {
  calculateProjection,
  CalculatorInput,
  YearSummary,
} from '../services/portfolio-growth-calculator'

interface CalculatorForm {
  initialWorth: number
  monthlyInvestment: number
  yearlyGrowthRate: number
  annualReturnRate: number
  years: number
  taxRate: number
}

const getDefaultFormValues = (): CalculatorForm => ({
  initialWorth: 0,
  monthlyInvestment: 585,
  yearlyGrowthRate: 5,
  annualReturnRate: 7,
  years: 30,
  taxRate: 22,
})

export function useCalculator() {
  const form = useLocalStorage<CalculatorForm>('calculator-form', getDefaultFormValues())
  const hasUserModifiedAnnualReturn = ref(false)
  const initialAnnualReturnRate = ref<number | null>(null)

  const yearSummary = ref<YearSummary[]>([])
  const portfolioData = ref<number[]>([])

  const {
    data: calculationResult,
    isLoading,
    refetch,
  } = useQuery<CalculationResult>({
    queryKey: ['calculationResult'],
    queryFn: utilityService.getCalculationResult,
  })

  const calculate = () => {
    const avgReturn = hasUserModifiedAnnualReturn.value
      ? form.value.annualReturnRate
      : (calculationResult.value?.median ?? form.value.annualReturnRate)

    const input: CalculatorInput = {
      initialWorth: form.value.initialWorth,
      monthlyInvestment: form.value.monthlyInvestment,
      yearlyGrowthRate: form.value.yearlyGrowthRate,
      annualReturnRate: avgReturn,
      years: form.value.years,
      taxRate: form.value.taxRate,
    }

    const result = calculateProjection(input)
    yearSummary.value = result.yearSummaries
    portfolioData.value = result.portfolioData
  }

  watchDebounced(
    form,
    () => {
      calculate()
    },
    { debounce: 300, deep: true }
  )

  watch(
    () => form.value.annualReturnRate,
    () => {
      hasUserModifiedAnnualReturn.value = true
    }
  )

  watch(calculationResult, () => {
    if (calculationResult.value) {
      if (initialAnnualReturnRate.value === null) {
        initialAnnualReturnRate.value = calculationResult.value.median
      }
      if (form.value.initialWorth === 0) {
        form.value.initialWorth = calculationResult.value.total
      }
      if (form.value.annualReturnRate === 7 && !hasUserModifiedAnnualReturn.value) {
        form.value.annualReturnRate = calculationResult.value.median
      }
    }
  })

  onMounted(() => {
    calculate()
  })

  const resetCalculator = async () => {
    hasUserModifiedAnnualReturn.value = false

    try {
      const result = await refetch()
      const freshData = result.data

      Object.assign(form.value, {
        initialWorth: freshData?.total || 0,
        monthlyInvestment: 585,
        yearlyGrowthRate: 5,
        annualReturnRate: initialAnnualReturnRate.value || freshData?.median || 7,
        years: 30,
        taxRate: 22,
      })
    } catch (error) {
      console.error('Failed to fetch fresh data:', error)
      Object.assign(form.value, getDefaultFormValues())
    }
  }

  return {
    form,
    isLoading,
    yearSummary: computed(() => yearSummary.value),
    portfolioData: computed(() => portfolioData.value),
    calculationResult,
    resetCalculator,
  }
}
