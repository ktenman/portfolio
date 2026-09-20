import { describe, it, expect } from 'vitest'
import type { Chart } from 'chart.js'
import type { RangeExtremes } from '../composables/use-portfolio-chart'
import { rangeExtremes, surfaceColor } from './chart'

interface DrawnText {
  text: string
  x: number
  y: number
}

interface FakeChartOptions {
  visible?: boolean
  extremes?: RangeExtremes | null
  hovered?: number
}

const CHARACTER_WIDTH = 6
const SERIES_COLOR = 'oklch(0.55 0.09 74)'

const fakeContext = (texts: DrawnText[], fills: string[]) => ({
  fillStyle: '',
  save: () => undefined,
  restore: () => undefined,
  beginPath: () => undefined,
  moveTo: () => undefined,
  lineTo: () => undefined,
  stroke: () => undefined,
  arc: () => undefined,
  fill() {
    fills.push(this.fillStyle)
  },
  setLineDash: () => undefined,
  strokeText: () => undefined,
  measureText: (text: string) => ({ width: text.length * CHARACTER_WIDTH }),
  fillText: (text: string, x: number, y: number) => {
    texts.push({ text, x, y })
  },
})

const fakeChart = (
  texts: DrawnText[],
  fills: string[],
  { visible = true, extremes = { low: 0, high: 2 }, hovered }: FakeChartOptions
) =>
  ({
    ctx: fakeContext(texts, fills),
    chartArea: { left: 40, right: 360, top: 28, bottom: 220 },
    data: {
      labels: ['29.12.25', '30.12.25', '31.12.25'],
      datasets: [
        {
          rangeExtremes: extremes,
          borderColor: SERIES_COLOR,
          data: [102060.28, 150000, 178204.06],
        },
      ],
    },
    isDatasetVisible: () => visible,
    tooltip: {
      getActiveElements: () => (hovered === undefined ? [] : [{ index: hovered }]),
    },
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
  const fills: string[] = []
  rangeExtremes.afterDatasetsDraw?.(fakeChart(texts, fills, options), {}, {}, false)
  return { texts, fills }
}

describe('rangeExtremes', () => {
  it('should label the high and the low with only their amount', () => {
    expect(draw().texts.map(drawn => drawn.text)).toEqual(['€178,204.06', '€102,060.28'])
  })

  it('should keep labels inside the chart area when the extremes sit on its edges', () => {
    expect(draw().texts.map(drawn => drawn.x)).toEqual([294, 40])
  })

  it('should place the high label above its point and the low label below', () => {
    expect(draw().texts.map(drawn => drawn.y)).toEqual([83, 217])
  })

  it('should leave both extreme dots hollow while nothing is hovered', () => {
    expect(draw().fills).toEqual([surfaceColor, surfaceColor])
  })

  it('should leave the hovered extreme dot to the charts own hover point', () => {
    expect(draw({ hovered: 2 }).fills).toEqual([surfaceColor])
  })

  it('should draw nothing when the total value series is hidden', () => {
    expect(draw({ visible: false }).texts).toEqual([])
  })

  it('should draw nothing for a series that carries no extremes', () => {
    expect(draw({ extremes: null }).texts).toEqual([])
  })
})
