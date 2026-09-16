import { describe, it, expect } from 'vitest'
import type { Chart } from 'chart.js'
import type { RangeExtremes } from '../composables/use-portfolio-chart'
import { rangeExtremes } from './chart'

interface DrawnText {
  text: string
  x: number
  y: number
}

interface FakeChartOptions {
  visible?: boolean
  extremes?: RangeExtremes | null
}

const CHARACTER_WIDTH = 6

const fakeContext = (texts: DrawnText[]) => ({
  save: () => undefined,
  restore: () => undefined,
  beginPath: () => undefined,
  moveTo: () => undefined,
  lineTo: () => undefined,
  stroke: () => undefined,
  arc: () => undefined,
  fill: () => undefined,
  setLineDash: () => undefined,
  strokeText: () => undefined,
  measureText: (text: string) => ({ width: text.length * CHARACTER_WIDTH }),
  fillText: (text: string, x: number, y: number) => {
    texts.push({ text, x, y })
  },
})

const fakeChart = (
  texts: DrawnText[],
  { visible = true, extremes = { low: 0, high: 2 } }: FakeChartOptions
) =>
  ({
    ctx: fakeContext(texts),
    chartArea: { left: 40, right: 360, top: 28, bottom: 220 },
    data: {
      labels: ['29.12.25', '30.12.25', '31.12.25'],
      datasets: [
        {
          rangeExtremes: extremes,
          borderColor: 'oklch(0.55 0.09 74)',
          data: [102060.28, 150000, 178204.06],
        },
      ],
    },
    isDatasetVisible: () => visible,
    getDatasetMeta: () => ({
      data: [
        { x: 40, y: 200 },
        { x: 200, y: 150 },
        { x: 360, y: 100 },
      ],
    }),
  }) as unknown as Chart

const draw = (options: FakeChartOptions = {}) => {
  const texts: DrawnText[] = []
  rangeExtremes.afterDatasetsDraw?.(fakeChart(texts, options), {}, {}, false)
  return texts
}

describe('rangeExtremes', () => {
  it('should label the high and the low with their amount and date', () => {
    expect(draw().map(drawn => drawn.text)).toEqual([
      '€178,204.06',
      '31.12.25',
      '€102,060.28',
      '29.12.25',
    ])
  })

  it('should keep labels inside the chart area when the extremes sit on its edges', () => {
    expect(draw().map(drawn => drawn.x)).toEqual([233, 312, 40, 119])
  })

  it('should place the high label above its point and the low label below', () => {
    expect(draw().map(drawn => drawn.y)).toEqual([83, 83, 217, 217])
  })

  it('should draw nothing when the total value series is hidden', () => {
    expect(draw({ visible: false })).toEqual([])
  })

  it('should draw nothing for a series that carries no extremes', () => {
    expect(draw({ extremes: null })).toEqual([])
  })
})
