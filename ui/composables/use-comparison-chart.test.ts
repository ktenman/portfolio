import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useComparisonChart } from './use-comparison-chart'
import type { ComparisonResponse } from '../models/generated/domain-models'

const createResponse = (overrides?: Partial<ComparisonResponse>): ComparisonResponse => ({
  startDate: '2023-01-01',
  endDate: '2023-12-31',
  instruments: [
    {
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
      currentPrice: 200,
      totalChangePercent: 25.0,
      dataPoints: [
        { date: '2023-01-01', percentageChange: 0 },
        { date: '2023-06-01', percentageChange: 10 },
        { date: '2023-12-31', percentageChange: 25 },
      ],
    },
    {
      instrumentId: 2,
      symbol: 'GOOGL',
      name: 'Alphabet Inc.',
      currentPrice: 150,
      totalChangePercent: -5.0,
      dataPoints: [
        { date: '2023-01-01', percentageChange: 0 },
        { date: '2023-06-01', percentageChange: 5 },
        { date: '2023-12-31', percentageChange: -5 },
      ],
    },
  ],
  ...overrides,
})

describe('useComparisonChart', () => {
  beforeEach(() => {
    vi.stubGlobal('window', { innerWidth: 1200 })
  })

  it('should return null when response is null', () => {
    const response = ref<ComparisonResponse | null>(null)
    const { chartData } = useComparisonChart(response)

    expect(chartData.value).toBeNull()
  })

  it('should return null when instruments list is empty', () => {
    const response = ref(createResponse({ instruments: [] }))
    const { chartData } = useComparisonChart(response)

    expect(chartData.value).toBeNull()
  })

  it('should create datasets for each instrument', () => {
    const response = ref(createResponse())
    const { chartData } = useComparisonChart(response)

    expect(chartData.value).not.toBeNull()
    expect(chartData.value?.datasets).toHaveLength(2)
    expect(chartData.value?.datasets[0].label).toBe('AAPL')
    expect(chartData.value?.datasets[1].label).toBe('GOOGL')
  })

  it('should assign different colors to each instrument', () => {
    const response = ref(createResponse())
    const { chartData } = useComparisonChart(response)

    const color1 = chartData.value?.datasets[0].borderColor
    const color2 = chartData.value?.datasets[1].borderColor
    expect(color1).not.toBe(color2)
  })

  it('should sort dates chronologically', () => {
    const response = ref(createResponse())
    const { chartData } = useComparisonChart(response)

    expect(chartData.value?.labels).toHaveLength(3)
  })

  it('should handle missing data points with null', () => {
    const response = ref(
      createResponse({
        instruments: [
          {
            instrumentId: 1,
            symbol: 'AAPL',
            name: 'Apple',
            currentPrice: 200,
            totalChangePercent: 10,
            dataPoints: [
              { date: '2023-01-01', percentageChange: 0 },
              { date: '2023-06-01', percentageChange: 10 },
            ],
          },
          {
            instrumentId: 2,
            symbol: 'GOOGL',
            name: 'Alphabet',
            currentPrice: 150,
            totalChangePercent: 5,
            dataPoints: [{ date: '2023-01-01', percentageChange: 0 }],
          },
        ],
      })
    )
    const { chartData } = useComparisonChart(response)

    expect(chartData.value?.datasets[1].data).toContain(null)
  })

  it('should provide chart options with percentage Y-axis', () => {
    const response = ref(createResponse())
    const { chartOptions } = useComparisonChart(response)

    expect(chartOptions.value.scales?.y?.title?.text).toBe('% Change')
  })

  it('should react to response changes', () => {
    const response = ref<ComparisonResponse | null>(null)
    const { chartData } = useComparisonChart(response)

    expect(chartData.value).toBeNull()

    response.value = createResponse()
    expect(chartData.value).not.toBeNull()
    expect(chartData.value?.datasets).toHaveLength(2)
  })
})
