import { describe, it, expect } from 'vitest'
import {
  mergeHistoricalWithCurrent,
  sortSummariesByDateAsc,
  sortSummariesByDateDesc,
  findSummaryByDate,
  flattenPages,
} from './summary-aggregator'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'

const createSummary = (date: string, totalValue: number = 1000): PortfolioSummaryDto => ({
  date,
  totalValue,
  xirrAnnualReturn: 0.1,
  realizedProfit: 100,
  unrealizedProfit: 50,
  totalProfit: 150,
  earningsPerDay: 10,
  earningsPerMonth: 300,
  totalProfitChange24h: 5,
})

describe('summary-aggregator', () => {
  describe('mergeHistoricalWithCurrent', () => {
    it('should return historical summaries when current is null', () => {
      const historical = [createSummary('2024-01-01'), createSummary('2024-01-02')]
      const result = mergeHistoricalWithCurrent(historical, null)
      expect(result).toEqual(historical)
    })

    it('should return historical summaries when current is undefined', () => {
      const historical = [createSummary('2024-01-01')]
      const result = mergeHistoricalWithCurrent(historical, undefined)
      expect(result).toEqual(historical)
    })

    it('should replace existing summary with same date', () => {
      const historical = [createSummary('2024-01-01', 1000), createSummary('2024-01-02', 2000)]
      const current = createSummary('2024-01-02', 2500)

      const result = mergeHistoricalWithCurrent(historical, current)

      expect(result).toHaveLength(2)
      expect(result[1].totalValue).toBe(2500)
    })

    it('should append current summary when date not found', () => {
      const historical = [createSummary('2024-01-01')]
      const current = createSummary('2024-01-02')

      const result = mergeHistoricalWithCurrent(historical, current)

      expect(result).toHaveLength(2)
      expect(result[1].date).toBe('2024-01-02')
    })

    it('should not mutate original array', () => {
      const historical = [createSummary('2024-01-01')]
      const current = createSummary('2024-01-02')

      mergeHistoricalWithCurrent(historical, current)

      expect(historical).toHaveLength(1)
    })

    it('should handle empty historical array', () => {
      const current = createSummary('2024-01-01')
      const result = mergeHistoricalWithCurrent([], current)

      expect(result).toHaveLength(1)
      expect(result[0]).toEqual(current)
    })
  })

  describe('sortSummariesByDateAsc', () => {
    it('should sort summaries by date ascending', () => {
      const summaries = [
        createSummary('2024-01-03'),
        createSummary('2024-01-01'),
        createSummary('2024-01-02'),
      ]

      const result = sortSummariesByDateAsc(summaries)

      expect(result[0].date).toBe('2024-01-01')
      expect(result[1].date).toBe('2024-01-02')
      expect(result[2].date).toBe('2024-01-03')
    })

    it('should not mutate original array', () => {
      const summaries = [createSummary('2024-01-02'), createSummary('2024-01-01')]
      sortSummariesByDateAsc(summaries)
      expect(summaries[0].date).toBe('2024-01-02')
    })

    it('should handle empty array', () => {
      expect(sortSummariesByDateAsc([])).toEqual([])
    })

    it('should handle single element', () => {
      const summaries = [createSummary('2024-01-01')]
      expect(sortSummariesByDateAsc(summaries)).toEqual(summaries)
    })
  })

  describe('sortSummariesByDateDesc', () => {
    it('should sort summaries by date descending', () => {
      const summaries = [
        createSummary('2024-01-01'),
        createSummary('2024-01-03'),
        createSummary('2024-01-02'),
      ]

      const result = sortSummariesByDateDesc(summaries)

      expect(result[0].date).toBe('2024-01-03')
      expect(result[1].date).toBe('2024-01-02')
      expect(result[2].date).toBe('2024-01-01')
    })
  })

  describe('findSummaryByDate', () => {
    it('should find summary by date', () => {
      const summaries = [createSummary('2024-01-01', 1000), createSummary('2024-01-02', 2000)]

      const result = findSummaryByDate(summaries, '2024-01-02')

      expect(result?.totalValue).toBe(2000)
    })

    it('should return undefined when date not found', () => {
      const summaries = [createSummary('2024-01-01')]
      const result = findSummaryByDate(summaries, '2024-01-02')
      expect(result).toBeUndefined()
    })

    it('should return undefined for empty array', () => {
      const result = findSummaryByDate([], '2024-01-01')
      expect(result).toBeUndefined()
    })
  })

  describe('flattenPages', () => {
    it('should flatten pages into single array', () => {
      const pages = [
        { content: [createSummary('2024-01-01'), createSummary('2024-01-02')] },
        { content: [createSummary('2024-01-03')] },
      ]

      const result = flattenPages(pages)

      expect(result).toHaveLength(3)
      expect(result[0].date).toBe('2024-01-01')
      expect(result[2].date).toBe('2024-01-03')
    })

    it('should return empty array for undefined pages', () => {
      const result = flattenPages(undefined)
      expect(result).toEqual([])
    })

    it('should handle empty pages array', () => {
      const result = flattenPages([])
      expect(result).toEqual([])
    })

    it('should handle pages with empty content', () => {
      const pages = [
        { content: [] as PortfolioSummaryDto[] },
        { content: [createSummary('2024-01-01')] },
      ]
      const result = flattenPages(pages)
      expect(result).toHaveLength(1)
    })
  })
})
