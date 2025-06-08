import { describe, it, expect } from 'vitest'
import { useChartConfig, chartColors } from './use-chart-config'

describe('useChartConfig', () => {
  describe('chartColors', () => {
    it('has all required colors defined', () => {
      expect(chartColors.primary).toBe('#8884d8')
      expect(chartColors.secondary).toBe('#82ca9d')
      expect(chartColors.success).toBe('#28a745')
      expect(chartColors.danger).toBe('#dc3545')
      expect(chartColors.warning).toBe('#ffc658')
      expect(chartColors.info).toBe('#17a2b8')
    })
  })

  describe('getBaseChartOptions', () => {
    it('returns default chart options', () => {
      const { getBaseChartOptions } = useChartConfig()
      const options = getBaseChartOptions()

      expect(options.responsive).toBe(true)
      expect(options.maintainAspectRatio).toBe(false)
      expect(options.animation).toBe(false)
      expect(options.interaction?.mode).toBe('index')
      expect(options.interaction?.intersect).toBe(false)
      expect(options.plugins?.legend?.display).toBe(true)
      expect(options.plugins?.legend?.position).toBe('top')
      expect(options.plugins?.tooltip?.enabled).toBe(true)
      expect(options.scales?.x?.grid?.display).toBe(false)
      expect(options.scales?.x?.ticks?.maxTicksLimit).toBe(8)
      expect(options.scales?.y?.grid?.color).toBe('rgba(0, 0, 0, 0.05)')
      expect(options.scales?.y?.ticks?.maxTicksLimit).toBe(8)
    })

    it('merges overrides correctly', () => {
      const { getBaseChartOptions } = useChartConfig()
      const overrides = {
        responsive: false,
        animation: { duration: 1000 },
      }
      const options = getBaseChartOptions(overrides)

      expect(options.responsive).toBe(false)
      expect(options.animation).toEqual({ duration: 1000 })
      expect(options.maintainAspectRatio).toBe(false) // Should keep default
    })
  })

  describe('getLineChartOptions', () => {
    it('returns line chart options without title and axis label', () => {
      const { getLineChartOptions } = useChartConfig()
      const options = getLineChartOptions()

      expect(options.plugins?.title).toBeUndefined()
      expect(options.scales?.y?.title).toBeUndefined()
      expect(options.responsive).toBe(true) // Should inherit base options
    })

    it('returns line chart options with title and axis label', () => {
      const { getLineChartOptions } = useChartConfig()
      const options = getLineChartOptions('Chart Title', 'Y Axis Label')

      expect(options.plugins?.title?.display).toBe(true)
      expect(options.plugins?.title?.text).toBe('Chart Title')
      expect((options.plugins?.title?.font as any)?.size).toBe(16)
      expect(options.scales?.y?.title?.display).toBe(true)
      expect(options.scales?.y?.title?.text).toBe('Y Axis Label')
    })

    it('returns line chart options with only title', () => {
      const { getLineChartOptions } = useChartConfig()
      const options = getLineChartOptions('Chart Title')

      expect(options.plugins?.title?.display).toBe(true)
      expect(options.plugins?.title?.text).toBe('Chart Title')
      expect(options.scales?.y?.title).toBeUndefined()
    })
  })

  describe('getBarChartOptions', () => {
    it('returns bar chart options without title and axis label', () => {
      const { getBarChartOptions } = useChartConfig()
      const options = getBarChartOptions()

      expect(options.plugins?.title).toBeUndefined()
      expect(options.scales?.y?.title).toBeUndefined()
      expect(options.responsive).toBe(true) // Should inherit base options
    })

    it('returns bar chart options with title and axis label', () => {
      const { getBarChartOptions } = useChartConfig()
      const options = getBarChartOptions('Bar Chart Title', 'Y Axis Label')

      expect(options.plugins?.title?.display).toBe(true)
      expect(options.plugins?.title?.text).toBe('Bar Chart Title')
      expect((options.plugins?.title?.font as any)?.size).toBe(16)
      expect(options.scales?.y?.title?.display).toBe(true)
      expect(options.scales?.y?.title?.text).toBe('Y Axis Label')
    })
  })

  describe('getDualAxisChartOptions', () => {
    it('returns dual axis chart options', () => {
      const { getDualAxisChartOptions } = useChartConfig()
      const options = getDualAxisChartOptions('Left Axis', 'Right Axis')

      expect(options.scales?.y?.type).toBe('linear')
      expect(options.scales?.y?.display).toBe(true)
      expect(options.scales?.y?.position).toBe('left')
      expect(options.scales?.y?.title?.display).toBe(true)
      expect(options.scales?.y?.title?.text).toBe('Left Axis')
      expect(options.scales?.y?.ticks?.maxTicksLimit).toBe(8)

      expect(options.scales?.y1?.type).toBe('linear')
      expect(options.scales?.y1?.display).toBe(true)
      expect(options.scales?.y1?.position).toBe('right')
      expect(options.scales?.y1?.title?.display).toBe(true)
      expect(options.scales?.y1?.title?.text).toBe('Right Axis')
      expect(options.scales?.y1?.grid?.drawOnChartArea).toBe(false)
      expect(options.scales?.y1?.ticks?.maxTicksLimit).toBe(8)
    })
  })

  describe('sampleData', () => {
    it('returns original data when length is less than maxPoints', () => {
      const { sampleData } = useChartConfig()
      const data = [1, 2, 3, 4, 5]
      const result = sampleData(data, 10)

      expect(result).toEqual([1, 2, 3, 4, 5])
    })

    it('returns original data when length equals maxPoints', () => {
      const { sampleData } = useChartConfig()
      const data = [1, 2, 3, 4, 5]
      const result = sampleData(data, 5)

      expect(result).toEqual([1, 2, 3, 4, 5])
    })

    it('samples data when length exceeds maxPoints', () => {
      const { sampleData } = useChartConfig()
      const data = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
      const result = sampleData(data, 5)

      expect(result).toHaveLength(5)
      expect(result[0]).toBe(1) // Should include first element
      expect(result).toContain(3) // Should contain sampled elements
      expect(result).toContain(5)
      expect(result).toContain(7)
      expect(result).toContain(9)
    })

    it('handles edge case with single element', () => {
      const { sampleData } = useChartConfig()
      const data = [1]
      const result = sampleData(data, 5)

      expect(result).toEqual([1])
    })

    it('handles empty array', () => {
      const { sampleData } = useChartConfig()
      const data: number[] = []
      const result = sampleData(data, 5)

      expect(result).toEqual([])
    })

    it('samples correctly with large dataset', () => {
      const { sampleData } = useChartConfig()
      const data = Array.from({ length: 100 }, (_, i) => i + 1)
      const result = sampleData(data, 10)

      expect(result).toHaveLength(10)
      expect(result[0]).toBe(1)
      expect(result[result.length - 1]).toBeLessThanOrEqual(100)
    })
  })

  describe('return values', () => {
    it('returns all expected functions and values', () => {
      const {
        chartColors: returnedColors,
        getBaseChartOptions,
        getLineChartOptions,
        getBarChartOptions,
        getDualAxisChartOptions,
        sampleData,
      } = useChartConfig()

      expect(returnedColors).toBe(chartColors)
      expect(typeof getBaseChartOptions).toBe('function')
      expect(typeof getLineChartOptions).toBe('function')
      expect(typeof getBarChartOptions).toBe('function')
      expect(typeof getDualAxisChartOptions).toBe('function')
      expect(typeof sampleData).toBe('function')
    })
  })
})
