import { computed, onMounted, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useLocalStorage, watchDebounced } from '@vueuse/core'
import { utilityService } from '../services/utility-service'
import { CalculationResult } from '../models/calculation-result'

interface CalculatorForm {
  initialWorth: number
  monthlyInvestment: number
  yearlyGrowthRate: number
  annualReturnRate: number
  years: number
}

interface YearSummary {
  year: number
  totalWorth: number
  yearGrowth: number
  earningsPerMonth: number
}

const getDefaultFormValues = (): CalculatorForm => ({
  initialWorth: 0,
  monthlyInvestment: 585,
  yearlyGrowthRate: 5,
  annualReturnRate: 7,
  years: 30,
})

export function useCalculator() {
  const form = useLocalStorage<CalculatorForm>('calculator-form', getDefaultFormValues())

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
    const currentPortfolioWorth = calculationResult.value?.total || form.value.initialWorth
    const avgReturn = calculationResult.value?.average || form.value.annualReturnRate

    // Convert annual percentages to monthly rates for compound interest calculations
    const monthlyGrowthRate = form.value.yearlyGrowthRate / 100 / 12
    const monthlyReturnRate = avgReturn / 100 / 12

    const tempYearSummary: YearSummary[] = []
    const tempPortfolioData: number[] = []

    let totalWorth = currentPortfolioWorth
    tempPortfolioData.push(totalWorth)

    for (let year = 1; year <= form.value.years; year++) {
      const yearStartWorth = totalWorth
      let currentMonthlyInvestment = form.value.monthlyInvestment

      // NOTE: Monthly compound interest calculation
      // Each month: add investment first, then apply growth
      for (let month = 1; month <= 12; month++) {
        totalWorth += currentMonthlyInvestment
        totalWorth *= 1 + monthlyReturnRate
      }

      currentMonthlyInvestment *= 1 + monthlyGrowthRate
      tempPortfolioData.push(totalWorth)

      const yearGrowth = totalWorth - yearStartWorth
      // Convert annual return rate (percentage) to monthly earnings
      // Formula: (totalWorth * annualRate%) / (12 months * 100)
      const earningsPerMonth = (totalWorth * avgReturn) / 1200

      tempYearSummary.push({
        year,
        totalWorth,
        yearGrowth,
        earningsPerMonth,
      })
    }

    yearSummary.value = tempYearSummary
    portfolioData.value = tempPortfolioData
  }

  watchDebounced(
    form,
    () => {
      calculate()
    },
    { debounce: 300, deep: true }
  )

  watch(calculationResult, () => {
    if (calculationResult.value) {
      // Auto-populate form with actual portfolio data if using defaults
      if (form.value.initialWorth === 0) {
        form.value.initialWorth = calculationResult.value.total
      }
      // Replace default 7% return with actual portfolio average
      if (form.value.annualReturnRate === 7) {
        form.value.annualReturnRate = calculationResult.value.average
      }
    }
  })

  onMounted(() => {
    calculate()
  })

  const resetCalculator = async () => {
    try {
      const result = await refetch()
      const freshData = result.data

      form.value = {
        initialWorth: freshData?.total || 0,
        monthlyInvestment: 585,
        yearlyGrowthRate: 5,
        annualReturnRate: freshData?.average || 7,
        years: 30,
      }
    } catch (error) {
      console.error('Failed to fetch fresh data:', error)
      form.value = getDefaultFormValues()
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
