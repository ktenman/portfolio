import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick, ref } from 'vue'
import { useCalculator } from './use-calculator'
import type { CalculationResult } from '../models/calculation-result'

vi.mock('../services/utility-service')

let mockData = ref<CalculationResult | null>(null)
let mockRefetch: any

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn(() => ({
    data: mockData,
    isLoading: ref(false),
    refetch: mockRefetch,
  })),
  QueryClient: vi.fn().mockImplementation(() => ({
    setQueryData: vi.fn(),
    getQueryData: vi.fn(),
    invalidateQueries: vi.fn(),
  })),
  VueQueryPlugin: {
    install: vi.fn(),
  },
}))

vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual('@vueuse/core')
  const vue = (await vi.importActual('vue')) as any
  return {
    ...actual,
    useLocalStorage: vi.fn((_key: string, defaultValue: any) => {
      return vue.ref(defaultValue)
    }),
    watchDebounced: (source: any, cb: any, options: any) => {
      return vue.watch(source, cb, { deep: options?.deep })
    },
  }
})

describe('useCalculator', () => {
  let calculator: ReturnType<typeof useCalculator>

  beforeEach(() => {
    vi.clearAllMocks()
    mockData.value = null
    mockRefetch = vi.fn().mockResolvedValue({
      data: {
        xirrs: [],
        median: 0,
        average: 7,
        total: 0,
      },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const setupCalculator = async () => {
    const { renderWithProviders } = await import('../tests/test-utils')
    const TestComponent = {
      setup() {
        calculator = useCalculator()
        return { calculator }
      },
      template: '<div></div>',
    }

    renderWithProviders(TestComponent)
    await nextTick()
  }

  it('should initialize with default form values', async () => {
    await setupCalculator()

    expect(calculator.form.value).toEqual({
      initialWorth: 0,
      monthlyInvestment: 585,
      yearlyGrowthRate: 5,
      annualReturnRate: 7,
      years: 30,
    })
  })

  it('should update form values when calculation result changes', async () => {
    await setupCalculator()

    expect(calculator.form.value.initialWorth).toBe(0)
    expect(calculator.form.value.annualReturnRate).toBe(7)

    mockData.value = {
      xirrs: [],
      median: 11.5,
      average: 12.5,
      total: 50000,
    } as any

    await nextTick()

    expect(calculator.form.value.initialWorth).toBe(50000)
    expect(calculator.form.value.annualReturnRate).toBe(12.5)
  })

  it('should calculate year summaries correctly', async () => {
    await setupCalculator()

    calculator.form.value = {
      initialWorth: 10000,
      monthlyInvestment: 100,
      yearlyGrowthRate: 0,
      annualReturnRate: 12,
      years: 2,
    }

    await nextTick()

    expect(calculator.yearSummary.value).toHaveLength(2)

    const yearOne = calculator.yearSummary.value[0]
    expect(yearOne.year).toBe(1)
    expect(yearOne.totalWorth).toBeGreaterThan(11200)
    expect(yearOne.yearGrowth).toBeGreaterThan(1200)
  })

  it('should calculate compound interest correctly', async () => {
    await setupCalculator()

    calculator.form.value = {
      initialWorth: 10000,
      monthlyInvestment: 500,
      yearlyGrowthRate: 0,
      annualReturnRate: 12,
      years: 1,
    }

    await nextTick()

    const yearOne = calculator.yearSummary.value[0]
    const monthlyRate = Math.pow(1.12, 1 / 12) - 1
    let total = 10000

    for (let i = 0; i < 12; i++) {
      total += 500
      total *= 1 + monthlyRate
    }

    expect(yearOne.totalWorth).toBeCloseTo(total, 0)
  })

  it('should handle yearly growth rate for monthly investments', async () => {
    await setupCalculator()

    calculator.form.value = {
      initialWorth: 0,
      monthlyInvestment: 100,
      yearlyGrowthRate: 10,
      annualReturnRate: 0,
      years: 2,
    }

    await nextTick()

    const yearOne = calculator.yearSummary.value[0]
    const yearTwo = calculator.yearSummary.value[1]

    expect(yearOne.totalWorth).toBe(1200)
    expect(yearTwo.totalWorth).toBeGreaterThan(yearOne.totalWorth)
  })

  it('should reset calculator with fresh data', async () => {
    mockRefetch = vi.fn().mockResolvedValue({
      data: {
        xirrs: [],
        median: 15,
        average: 18,
        total: 75000,
      },
    })

    await setupCalculator()
    await calculator.resetCalculator()

    expect(calculator.form.value).toEqual({
      initialWorth: 75000,
      monthlyInvestment: 585,
      yearlyGrowthRate: 5,
      annualReturnRate: 18,
      years: 30,
    })
  })

  it('should handle errors in reset and use default values', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    mockRefetch = vi.fn().mockRejectedValue(new Error('API Error'))

    await setupCalculator()
    await calculator.resetCalculator()

    expect(consoleSpy).toHaveBeenCalledWith('Failed to fetch fresh data:', expect.any(Error))
    expect(calculator.form.value).toEqual({
      initialWorth: 0,
      monthlyInvestment: 585,
      yearlyGrowthRate: 5,
      annualReturnRate: 7,
      years: 30,
    })

    consoleSpy.mockRestore()
  })

  it('should calculate monthly earnings based on annual return rate', async () => {
    await setupCalculator()

    calculator.form.value = {
      initialWorth: 10000,
      monthlyInvestment: 0,
      yearlyGrowthRate: 0,
      annualReturnRate: 12,
      years: 1,
    }

    await nextTick()

    const yearOne = calculator.yearSummary.value[0]
    const avgReturn =
      calculator.calculationResult.value?.average || calculator.form.value.annualReturnRate
    expect(yearOne.earningsPerMonth).toBeCloseTo((yearOne.totalWorth * avgReturn) / 1200, 2)
  })

  it('should handle zero values gracefully', async () => {
    await setupCalculator()

    calculator.form.value = {
      initialWorth: 0,
      monthlyInvestment: 0,
      yearlyGrowthRate: 0,
      annualReturnRate: 0,
      years: 5,
    }

    await nextTick()

    expect(calculator.yearSummary.value).toHaveLength(5)
    calculator.yearSummary.value.forEach(year => {
      expect(year.totalWorth).toBe(0)
      expect(year.yearGrowth).toBe(0)
      expect(year.earningsPerMonth).toBe(0)
    })
  })

  it('should maintain portfolio data array with initial value', async () => {
    await setupCalculator()

    calculator.form.value = {
      initialWorth: 5000,
      monthlyInvestment: 0,
      yearlyGrowthRate: 0,
      annualReturnRate: 0,
      years: 3,
    }

    await nextTick()

    expect(calculator.portfolioData.value[0]).toBe(5000)
    expect(calculator.portfolioData.value[1]).toBe(5000)
    expect(calculator.portfolioData.value[2]).toBe(5000)
    expect(calculator.portfolioData.value[3]).toBe(5000)
  })

  it('should use user-modified annual return rate instead of calculated average', async () => {
    await setupCalculator()

    calculator.form.value.annualReturnRate = 50
    await nextTick()

    mockData.value = {
      xirrs: [],
      median: 11.5,
      average: 12.5,
      total: 10000,
    } as any

    await nextTick()

    expect(calculator.form.value.annualReturnRate).toBe(50)

    const yearOne = calculator.yearSummary.value[0]
    expect(yearOne.year).toBe(1)

    const expectedMonthlyRate = Math.pow(1.5, 1 / 12) - 1
    let expectedTotal = 10000

    for (let i = 0; i < 12; i++) {
      expectedTotal += 585
      expectedTotal *= 1 + expectedMonthlyRate
    }

    expect(yearOne.totalWorth).toBeCloseTo(expectedTotal, 0)
    expect(yearOne.earningsPerMonth).toBeCloseTo((yearOne.totalWorth * 50) / 1200, 2)
  })

  it('should not overwrite user-modified annual return rate when calculation result updates', async () => {
    await setupCalculator()

    calculator.form.value.annualReturnRate = 25

    await nextTick()

    mockData.value = {
      xirrs: [],
      median: 11.5,
      average: 15.5,
      total: 60000,
    } as any

    await nextTick()

    expect(calculator.form.value.annualReturnRate).toBe(25)
    expect(calculator.form.value.initialWorth).toBe(60000)
  })

  it('should reset user-modified flag when resetCalculator is called', async () => {
    mockRefetch = vi.fn().mockResolvedValue({
      data: {
        xirrs: [],
        median: 15,
        average: 18,
        total: 75000,
      },
    })

    await setupCalculator()

    calculator.form.value.annualReturnRate = 35

    await nextTick()

    await calculator.resetCalculator()

    expect(calculator.form.value.annualReturnRate).toBe(18)

    mockData.value = {
      xirrs: [],
      median: 20,
      average: 22,
      total: 80000,
    } as any

    await nextTick()

    expect(calculator.form.value.annualReturnRate).toBe(18)
  })
})
