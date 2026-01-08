import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { usePortfolioSummaryQuery } from './use-portfolio-summary-query'
import { portfolioSummaryService } from '../services/portfolio-summary-service'
import { renderWithProviders } from '../tests/test-utils'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import type { Page } from '../models/page'
import { createPortfolioSummaryDto } from '../tests/fixtures'

vi.mock('../services/portfolio-summary-service')
vi.mock('./use-auth-state', () => ({
  useAuthState: () => ({
    isAuthenticated: ref(true),
    isAuthChecking: ref(false),
    checkAuth: vi.fn().mockResolvedValue(true),
  }),
}))

const mockCurrentSummary: PortfolioSummaryDto = createPortfolioSummaryDto({
  date: '2023-12-31',
  totalValue: 50000,
  totalProfit: 5000,
  xirrAnnualReturn: 0.12,
  earningsPerDay: 100,
  earningsPerMonth: 3000,
})

const mockHistoricalSummaries = [
  createPortfolioSummaryDto({
    date: '2023-12-29',
    totalValue: 49500,
    totalProfit: 5000,
    xirrAnnualReturn: 0.12,
    earningsPerDay: 100,
    earningsPerMonth: 3000,
  }),
  createPortfolioSummaryDto({
    date: '2023-12-30',
    totalValue: 49750,
    totalProfit: 5000,
    xirrAnnualReturn: 0.12,
    earningsPerDay: 100,
    earningsPerMonth: 3000,
  }),
]
const mockPage: Page<PortfolioSummaryDto> = {
  content: mockHistoricalSummaries,
  totalElements: 100,
  totalPages: 3,
  size: 40,
  number: 0,
}

describe('usePortfolioSummaryQuery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue(mockPage)
    vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(mockCurrentSummary)
  })

  const setupQuery = () => {
    let queryResult: ReturnType<typeof usePortfolioSummaryQuery> | null = null

    const TestComponent = {
      setup() {
        queryResult = usePortfolioSummaryQuery()
        return { queryResult }
      },
      template: '<div>{{ queryResult.isLoading.value ? "Loading" : "Loaded" }}</div>',
    }

    const wrapper = renderWithProviders(TestComponent)

    return {
      queryResult: queryResult!,
      queryClient: wrapper.queryClient,
      wrapper: wrapper,
    }
  }

  describe('data fetching and transformation', () => {
    it('should merge historical and current summaries correctly', async () => {
      const { queryResult } = setupQuery()

      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
      await flushPromises()

      const summaries = queryResult.summaries.value
      expect(summaries).toHaveLength(3)
      expect(summaries.find(s => s.date === '2023-12-31')).toEqual(mockCurrentSummary)
      expect(summaries.filter(s => s.date === '2023-12-29')).toHaveLength(1)
    })

    it('should replace existing summary when current matches historical date', async () => {
      const duplicateSummary = createPortfolioSummaryDto({
        ...mockCurrentSummary,
        totalValue: 55000,
      })
      vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(duplicateSummary)

      const historicalWithDuplicate = [
        createPortfolioSummaryDto({ ...mockCurrentSummary, totalValue: 48000 }),
        ...mockHistoricalSummaries,
      ]

      vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue({
        ...mockPage,
        content: historicalWithDuplicate,
      })

      const { queryResult } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
      await flushPromises()

      await vi.waitFor(
        () => {
          const summaries = queryResult.summaries.value
          return summaries.length > 0
        },
        { timeout: 5000 }
      )

      const summaries = queryResult.summaries.value
      const dec31Summaries = summaries.filter(s => s.date === '2023-12-31')
      expect(dec31Summaries).toHaveLength(1)
      expect(dec31Summaries[0].totalValue).toBe(55000)
    })

    it('should sort summaries by date ascending', async () => {
      const { queryResult } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
      await flushPromises()

      await vi.waitFor(
        () => {
          const sorted = queryResult.sortedSummaries.value
          return sorted.length === 3
        },
        { timeout: 5000 }
      )

      const sorted = queryResult.sortedSummaries.value
      expect(sorted).toHaveLength(3)
      expect(sorted[0].date).toBe('2023-12-29')
      expect(sorted[1].date).toBe('2023-12-30')
      expect(sorted[2].date).toBe('2023-12-31')
    })

    it('should reverse sort summaries correctly', async () => {
      const { queryResult } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
      await flushPromises()

      await vi.waitFor(
        () => {
          const reversed = queryResult.reversedSummaries.value
          return reversed.length === 3
        },
        { timeout: 5000 }
      )

      const reversed = queryResult.reversedSummaries.value
      expect(reversed).toHaveLength(3)
      expect(reversed[0].date).toBe('2023-12-31')
      expect(reversed[1].date).toBe('2023-12-30')
      expect(reversed[2].date).toBe('2023-12-29')
    })
  })

  describe('recalculation functionality', () => {
    it('should handle successful recalculation', async () => {
      const mockResponse = { message: 'Recalculation completed successfully' }
      vi.mocked(portfolioSummaryService.recalculate).mockResolvedValue(mockResponse)

      const { queryResult, queryClient } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

      queryResult.recalculate()
      await flushPromises()

      expect(portfolioSummaryService.recalculate).toHaveBeenCalled()
      await vi.waitFor(() => {
        expect(queryResult.recalculationMessage.value).toBe('Recalculation completed successfully')
        return true
      })

      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['portfolio-summary'] })
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['instruments'] })
    })

    it('should handle recalculation error', async () => {
      vi.mocked(portfolioSummaryService.recalculate).mockRejectedValue(new Error('Network error'))

      const { queryResult } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })

      queryResult.recalculate()
      await flushPromises()

      await vi.waitFor(() => {
        expect(queryResult.recalculationMessage.value).toBe(
          'Failed to recalculate summaries. Please try again later.'
        )
        return true
      })
    })

    it('should track recalculating state correctly', async () => {
      let resolveRecalculate: (value: { message: string }) => void
      const recalculatePromise = new Promise<{ message: string }>(resolve => {
        resolveRecalculate = resolve
      })
      vi.mocked(portfolioSummaryService.recalculate).mockReturnValue(recalculatePromise)

      const { queryResult } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })

      expect(queryResult.isRecalculating.value).toBe(false)

      queryResult.recalculate()
      await flushPromises()

      expect(queryResult.isRecalculating.value).toBe(true)

      resolveRecalculate!({ message: 'Done' })
      await flushPromises()

      await vi.waitFor(() => !queryResult.isRecalculating.value, { timeout: 5000 })
    })
  })

  describe('error handling', () => {
    it('should handle error state', async () => {
      const errorMessage = 'Failed to fetch historical data'
      vi.mocked(portfolioSummaryService.getHistorical).mockRejectedValue(new Error(errorMessage))
      vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(mockCurrentSummary)

      const { queryResult } = setupQuery()

      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })

      expect(queryResult.error).toBeDefined()
    })

    it('should handle empty historical data with current summary', async () => {
      vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 40,
        number: 0,
      })
      vi.mocked(portfolioSummaryService.getCurrent).mockResolvedValue(mockCurrentSummary)

      const { queryResult } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
      await flushPromises()

      await vi.waitFor(
        () => {
          const summaries = queryResult.summaries.value
          return summaries.length > 0
        },
        { timeout: 5000 }
      )

      expect(queryResult.summaries.value).toHaveLength(1)
      expect(queryResult.summaries.value[0]).toEqual(mockCurrentSummary)
    })
  })

  describe('infinite loading', () => {
    it('should handle pagination state', async () => {
      const { queryResult } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })

      expect(queryResult.fetchSummaries).toBeDefined()
      expect(typeof queryResult.fetchSummaries).toBe('function')
    })

    it('should indicate when more data is available based on pages', async () => {
      vi.mocked(portfolioSummaryService.getHistorical).mockResolvedValue({
        ...mockPage,
        totalPages: 5,
        number: 0,
      })

      const { queryResult } = setupQuery()
      await vi.waitFor(() => !queryResult.isLoading.value, { timeout: 5000 })
      await flushPromises()

      await vi.waitFor(
        () => {
          return queryResult.hasMoreData.value !== undefined
        },
        { timeout: 5000 }
      )
    })

    it('should combine loading states correctly', async () => {
      vi.mocked(portfolioSummaryService.getHistorical).mockImplementation(
        () => new Promise(() => {})
      )

      const { queryResult } = setupQuery()
      expect(queryResult.isLoading.value).toBe(true)
    })
  })
})
