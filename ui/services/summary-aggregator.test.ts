import { describe, it, expect } from 'vitest'
import {
  mergeHistoricalWithCurrent,
  mergeSummariesByDate,
  sortSummariesByDateAsc,
  sortSummariesByDateDesc,
  findSummaryByDate,
  flattenPages,
} from './summary-aggregator'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'
import { createCalendarRow } from '../tests/fixtures'

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

  describe('mergeSummariesByDate', () => {
    it('merges layers by date with later layers winning', () => {
      const max = [
        createCalendarRow('2025-12-02', 2),
        createCalendarRow('2025-12-01', 1),
        createCalendarRow('2025-11-30', 0),
      ]
      const recent = [createCalendarRow('2025-12-02', 20)]
      const current = [createCalendarRow('2025-12-03', 30)]
      const result = mergeSummariesByDate(max, recent, current)
      expect([result.map(row => row.date), result.map(row => row.totalProfit)]).toEqual([
        ['2025-11-30', '2025-12-01', '2025-12-02', '2025-12-03'],
        [0, 1, 20, 30],
      ])
    })

    it('keeps MAX rows inside the recent range that the recent layer lacks', () => {
      const gap = createCalendarRow('2025-11-30', 0)
      const max = [createCalendarRow('2025-11-29', 0), gap, createCalendarRow('2025-12-01', 0)]
      const recent = [createCalendarRow('2025-11-29', 10), createCalendarRow('2025-12-01', 10)]
      expect(mergeSummariesByDate(max, recent)).toContain(gap)
    })

    it('returns the source rows unchanged', () => {
      const later = createCalendarRow('2025-12-02', 2)
      const earlier = createCalendarRow('2025-12-01', 1)
      const layer = [later, earlier]
      const result = mergeSummariesByDate(layer)
      expect(result[0]).toBe(earlier)
      expect(result[1]).toBe(later)
      expect(layer[0]).toBe(later)
      expect(layer[1]).toBe(earlier)
    })

    it('keeps the later duplicate within one layer', () =>
      expect(
        mergeSummariesByDate([
          createCalendarRow('2025-12-01', 1),
          createCalendarRow('2025-12-01', 2),
        ]).map(row => row.totalProfit)
      ).toEqual([2]))
  })
})
