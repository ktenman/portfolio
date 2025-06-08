import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import LineChart from './line-chart.vue'

// Mock Chart.js - properly handle hoisting
vi.mock('chart.js/auto', () => {
  return {
    default: vi.fn(() => ({
      destroy: vi.fn(),
      update: vi.fn(),
    })),
  }
})

// Mock the formatters composable
vi.mock('../../composables/use-formatters', () => ({
  useFormatters: () => ({
    formatCurrency: vi.fn((value: number) => `€${value.toFixed(2)}`),
  }),
}))

describe('LineChart', () => {
  let mockContext: any

  beforeEach(() => {
    vi.clearAllMocks()

    // Mock canvas and context
    mockContext = {
      fillRect: vi.fn(),
      clearRect: vi.fn(),
      getImageData: vi.fn(),
      putImageData: vi.fn(),
      createImageData: vi.fn(),
      setTransform: vi.fn(),
      drawImage: vi.fn(),
      save: vi.fn(),
      fillText: vi.fn(),
      restore: vi.fn(),
      beginPath: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      closePath: vi.fn(),
      stroke: vi.fn(),
      translate: vi.fn(),
      scale: vi.fn(),
      rotate: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
      measureText: vi.fn(() => ({ width: 0 })),
      transform: vi.fn(),
      rect: vi.fn(),
      clip: vi.fn(),
    }

    // Mock DOM canvas element
    Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', {
      value: vi.fn(() => mockContext),
      writable: true,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('component initialization', () => {
    it('renders with required props', () => {
      const wrapper = mount(LineChart, {
        props: {
          data: [10, 20, 30, 40, 50],
        },
      })

      expect(wrapper.find('canvas').exists()).toBe(true)
    })

    it('applies default props correctly', () => {
      const wrapper = mount(LineChart, {
        props: {
          data: [10, 20, 30],
        },
      })

      expect(wrapper.props()).toMatchObject({
        data: [10, 20, 30],
        title: 'Line Chart',
        xAxisLabel: 'X Axis',
        yAxisLabel: 'Y Axis',
        borderColor: 'rgba(75, 192, 192, 1)',
        backgroundColor: 'rgba(75, 192, 192, 0.2)',
      })
    })

    it('accepts custom props', () => {
      const customProps = {
        data: [100, 200, 300],
        title: 'Custom Chart',
        xAxisLabel: 'Time',
        yAxisLabel: 'Value',
        borderColor: 'red',
        backgroundColor: 'blue',
      }

      const wrapper = mount(LineChart, { props: customProps })

      expect(wrapper.props()).toMatchObject(customProps)
    })
  })

  describe('chart creation', () => {
    it('creates chart instance on mount', async () => {
      const Chart = (await import('chart.js/auto')).default

      mount(LineChart, {
        props: {
          data: [10, 20, 30],
        },
      })

      expect(Chart).toHaveBeenCalledWith(
        mockContext,
        expect.objectContaining({
          type: 'line',
          data: expect.objectContaining({
            labels: [1, 2, 3],
            datasets: expect.arrayContaining([
              expect.objectContaining({
                label: 'Y Axis',
                data: [10, 20, 30],
                borderColor: 'rgba(75, 192, 192, 1)',
                backgroundColor: 'rgba(75, 192, 192, 0.2)',
                borderWidth: 2,
                fill: false,
              }),
            ]),
          }),
          options: expect.objectContaining({
            responsive: true,
            scales: expect.objectContaining({
              x: expect.objectContaining({
                title: { display: true, text: 'X Axis' },
                grid: { display: false },
              }),
              y: expect.objectContaining({
                title: { display: true, text: 'Y Axis' },
                ticks: expect.objectContaining({
                  callback: expect.any(Function),
                }),
              }),
            }),
            plugins: expect.objectContaining({
              title: { display: true, text: 'Line Chart', font: { size: 16 } },
              legend: { display: false },
            }),
          }),
        })
      )
    })

    it('handles empty data array', async () => {
      const Chart = (await import('chart.js/auto')).default

      mount(LineChart, {
        props: {
          data: [],
        },
      })

      expect(Chart).toHaveBeenCalledWith(
        mockContext,
        expect.objectContaining({
          data: expect.objectContaining({
            labels: [],
            datasets: expect.arrayContaining([
              expect.objectContaining({
                data: [],
              }),
            ]),
          }),
        })
      )
    })

    it('generates correct labels for data array', async () => {
      const Chart = (await import('chart.js/auto')).default

      mount(LineChart, {
        props: {
          data: [1, 2, 3, 4, 5, 6],
        },
      })

      expect(Chart).toHaveBeenCalledWith(
        mockContext,
        expect.objectContaining({
          data: expect.objectContaining({
            labels: [1, 2, 3, 4, 5, 6],
          }),
        })
      )
    })
  })

  describe('currency formatting', () => {
    it('applies currency formatting when y-axis label contains €', async () => {
      const Chart = (await import('chart.js/auto')).default

      mount(LineChart, {
        props: {
          data: [1000, 2000, 3000],
          yAxisLabel: 'Price €',
        },
      })

      const chartCall = vi.mocked(Chart).mock.calls[0]
      const yAxisCallback = chartCall?.[1]?.options?.scales?.y?.ticks?.callback

      if (yAxisCallback) {
        expect(yAxisCallback.call({} as any, 1000, 0, [])).toBe('€1000.00')
        expect(yAxisCallback.call({} as any, 2500.5, 0, [])).toBe('€2500.50')
      }
    })

    it('does not apply currency formatting when y-axis label does not contain €', async () => {
      const Chart = (await import('chart.js/auto')).default

      mount(LineChart, {
        props: {
          data: [100, 200, 300],
          yAxisLabel: 'Count',
        },
      })

      const chartCall = vi.mocked(Chart).mock.calls[0]
      const yAxisCallback = chartCall?.[1]?.options?.scales?.y?.ticks?.callback

      if (yAxisCallback) {
        expect(yAxisCallback.call({} as any, 100, 0, [])).toBe(100)
        expect(yAxisCallback.call({} as any, 250.5, 0, [])).toBe(250.5)
      }
    })
  })

  describe('edge cases', () => {
    it('handles null canvas context gracefully', () => {
      // Mock getContext to return null
      Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', {
        value: vi.fn(() => null),
        writable: true,
      })

      expect(() => {
        mount(LineChart, {
          props: {
            data: [10, 20, 30],
          },
        })
      }).not.toThrow()
    })

    it('handles large datasets', async () => {
      const Chart = (await import('chart.js/auto')).default
      const largeData = Array.from({ length: 100 }, (_, i) => i)

      mount(LineChart, {
        props: {
          data: largeData,
        },
      })

      expect(Chart).toHaveBeenCalledWith(
        mockContext,
        expect.objectContaining({
          data: expect.objectContaining({
            labels: Array.from({ length: 100 }, (_, i) => i + 1),
            datasets: expect.arrayContaining([
              expect.objectContaining({
                data: largeData,
              }),
            ]),
          }),
        })
      )
    })

    it('handles negative values', async () => {
      const Chart = (await import('chart.js/auto')).default

      mount(LineChart, {
        props: {
          data: [-10, -5, 0, 5, 10],
        },
      })

      expect(Chart).toHaveBeenCalledWith(
        mockContext,
        expect.objectContaining({
          data: expect.objectContaining({
            datasets: expect.arrayContaining([
              expect.objectContaining({
                data: [-10, -5, 0, 5, 10],
              }),
            ]),
          }),
        })
      )
    })
  })
})
