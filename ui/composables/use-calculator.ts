import { onMounted, ref, watch } from 'vue'
import { utilityService } from '../services'
import { CalculationResult } from '../models/calculation-result'
import { useLocalStorage } from './use-local-storage'

interface CalculatorForm extends Record<string, unknown> {
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

export function useCalculator() {
  const defaultForm: CalculatorForm = {
    initialWorth: 2000,
    monthlyInvestment: 585,
    yearlyGrowthRate: 5,
    annualReturnRate: 21.672,
    years: 28,
  }

  const STORAGE_KEY = 'investment-calculator-form'

  const {
    form,
    isUpdatingForm,
    loadFromLocalStorage,
    saveToLocalStorage,
    handleInput,
    resetForm,
    updateFormField,
  } = useLocalStorage(STORAGE_KEY, defaultForm)

  const isLoading = ref(true)
  const yearSummary = ref<YearSummary[]>([])
  const portfolioData = ref<number[]>([])
  const calculationResult = ref<CalculationResult | null>(null)

  const calculate = async () => {
    isLoading.value = true

    if (isUpdatingForm.value) return

    try {
      const result = await getResult()

      await updateFormField('annualReturnRate', Number(result.average.toFixed(3)))
      await updateFormField('initialWorth', Number(result.total.toFixed(2)))

      const { initialWorth, monthlyInvestment, yearlyGrowthRate, annualReturnRate, years } =
        form as CalculatorForm
      const values = []
      let totalWorth = initialWorth
      let currentMonthlyInvestment = monthlyInvestment

      yearSummary.value = []

      for (let year = 1; year <= years; year++) {
        const yearStartWorth = totalWorth
        for (let month = 1; month <= 12; month++) {
          totalWorth += currentMonthlyInvestment
          totalWorth *= 1 + annualReturnRate / 1200
        }
        currentMonthlyInvestment *= 1 + yearlyGrowthRate / 100
        values.push(totalWorth)

        yearSummary.value.push({
          year,
          totalWorth,
          yearGrowth: totalWorth - yearStartWorth,
          earningsPerMonth: (totalWorth * annualReturnRate) / 1200,
        })
      }

      portfolioData.value = values
      calculationResult.value = result
    } finally {
      isLoading.value = false
    }
  }

  const getResult = async (): Promise<CalculationResult> => {
    try {
      return await utilityService.getCalculationResult()
    } finally {
      isLoading.value = false
    }
  }

  const resetCalculator = () => {
    if (
      confirm(
        'Are you sure you want to reset the calculator? This will clear all your current values.'
      )
    ) {
      resetForm()
      calculate()
    }
  }

  let debounceTimer: number | null = null
  watch(
    form,
    () => {
      if (isUpdatingForm.value) return

      if (debounceTimer !== null) {
        clearTimeout(debounceTimer)
      }

      debounceTimer = setTimeout(() => {
        saveToLocalStorage()
        calculate()
        debounceTimer = null
      }, 1000) as unknown as number
    },
    { deep: true }
  )

  onMounted(() => {
    loadFromLocalStorage()
    calculate()
  })

  return {
    form,
    isLoading,
    yearSummary,
    portfolioData,
    calculationResult,
    handleInput,
    resetCalculator,
    calculate,
  }
}
