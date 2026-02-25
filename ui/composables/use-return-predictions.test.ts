import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useReturnPredictions } from './use-return-predictions'

const mockQueryReturn = {
  data: ref(null as any),
  isLoading: ref(false),
  error: ref(null as any),
}

const mockInvalidateQueries = vi.fn()

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn(() => mockQueryReturn),
  useQueryClient: vi.fn(() => ({ invalidateQueries: mockInvalidateQueries })),
}))

vi.mock('./use-auth-state', () => ({
  useAuthState: vi.fn(() => ({ isAuthenticated: ref(true) })),
}))

vi.mock('../services/portfolio-summary-service', () => ({
  portfolioSummaryService: { getPredictions: vi.fn() },
}))

describe('useReturnPredictions', () => {
  beforeEach(() => {
    mockQueryReturn.data.value = null
    mockQueryReturn.isLoading.value = false
    mockQueryReturn.error.value = null
    mockInvalidateQueries.mockClear()
  })

  it('should return empty predictions when data is null', () => {
    const { predictions, hasSufficientData } = useReturnPredictions()
    expect(predictions.value).toEqual([])
    expect(hasSufficientData.value).toBe(false)
  })

  it('should return predictions when data is available', async () => {
    mockQueryReturn.data.value = {
      currentValue: 50000,
      xirrAnnualReturn: 0.12,
      dailyVolatility: 0.008,
      dataPointCount: 120,
      monthlyInvestment: 500,
      predictions: [
        {
          horizon: '1M',
          horizonDays: 30,
          targetDate: '2026-03-19',
          expectedValue: 50400,
          optimisticValue: 52800,
          pessimisticValue: 48100,
          contributions: 500,
        },
      ],
    }
    await nextTick()
    const { predictions, hasSufficientData, dataPointCount, currentValue, monthlyInvestment } =
      useReturnPredictions()
    expect(predictions.value).toHaveLength(1)
    expect(hasSufficientData.value).toBe(true)
    expect(dataPointCount.value).toBe(120)
    expect(currentValue.value).toBe(50000)
    expect(monthlyInvestment.value).toBe(500)
  })

  it('should return insufficient data when predictions list is empty', async () => {
    mockQueryReturn.data.value = {
      currentValue: 50000,
      xirrAnnualReturn: 0.12,
      dailyVolatility: 0,
      dataPointCount: 10,
      predictions: [],
    }
    await nextTick()
    const { hasSufficientData, dataPointCount } = useReturnPredictions()
    expect(hasSufficientData.value).toBe(false)
    expect(dataPointCount.value).toBe(10)
  })

  it('should map error message correctly', async () => {
    mockQueryReturn.error.value = { message: 'Network error' }
    await nextTick()
    const { error } = useReturnPredictions()
    expect(error.value).toBe('Network error')
  })

  it('should invalidate queries when monthly contribution changes', async () => {
    const contribution = ref<number | undefined>(undefined)
    useReturnPredictions(contribution)
    contribution.value = 1000
    await nextTick()
    expect(mockInvalidateQueries).toHaveBeenCalledWith({
      queryKey: ['portfolio-summary', 'predictions'],
    })
  })
})
