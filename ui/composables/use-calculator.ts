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
  taxRate: number
}

interface YearSummary {
  year: number
  totalInvested: number
  totalWorth: number
  grossProfit: number
  taxAmount: number
  netProfit: number
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
    const currentPortfolioWorth = form.value.initialWorth
    const avgReturn = hasUserModifiedAnnualReturn.value
      ? form.value.annualReturnRate
      : (calculationResult.value?.average ?? form.value.annualReturnRate)

    const monthlyGrowthRate = form.value.yearlyGrowthRate / 100 / 12
    const monthlyReturnRate = Math.pow(1 + avgReturn / 100, 1 / 12) - 1

    const tempYearSummary: YearSummary[] = []
    const tempPortfolioData: number[] = []

    let totalWorth = currentPortfolioWorth
    let totalInvested = currentPortfolioWorth
    tempPortfolioData.push(totalWorth)

    let currentMonthlyInvestment = form.value.monthlyInvestment
    for (let year = 1; year <= form.value.years; year++) {
      let yearlyInvestmentAmount = 0

      for (let month = 1; month <= 12; month++) {
        yearlyInvestmentAmount += currentMonthlyInvestment
        totalWorth += currentMonthlyInvestment
        totalWorth *= 1 + monthlyReturnRate
      }

      totalInvested += yearlyInvestmentAmount
      currentMonthlyInvestment *= 1 + monthlyGrowthRate

      const grossProfit = totalWorth - totalInvested
      const taxAmount = grossProfit * (form.value.taxRate / 100)
      const netProfit = grossProfit - taxAmount
      const actualTotalWorth = totalInvested + netProfit

      tempPortfolioData.push(actualTotalWorth)

      tempYearSummary.push({
        year,
        totalInvested,
        totalWorth: actualTotalWorth,
        grossProfit,
        taxAmount,
        netProfit,
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

  watch(
    () => form.value.annualReturnRate,
    () => {
      hasUserModifiedAnnualReturn.value = true
    }
  )

  watch(calculationResult, () => {
    if (calculationResult.value) {
      // Auto-populate form with actual portfolio data if using defaults
      if (form.value.initialWorth === 0) {
        form.value.initialWorth = calculationResult.value.total
      }
      // Replace default 7% return with actual portfolio average only if user hasn't modified it
      if (form.value.annualReturnRate === 7 && !hasUserModifiedAnnualReturn.value) {
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

      hasUserModifiedAnnualReturn.value = false
      form.value = {
        initialWorth: freshData?.total || 0,
        monthlyInvestment: 585,
        yearlyGrowthRate: 5,
        annualReturnRate: freshData?.average || 7,
        years: 30,
        taxRate: 22,
      }
    } catch (error) {
      console.error('Failed to fetch fresh data:', error)
      hasUserModifiedAnnualReturn.value = false
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
