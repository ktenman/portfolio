import type { CartesianScaleOptions, Chart, FontSpec, Scale } from 'chart.js'
import { toFont } from 'chart.js/helpers'
import { addDays, daysBetween } from '../utils/iso-dates'
import type { ChartPeriod } from './chart-period'

export interface ChartFrame {
  width: number
  left: number
  right: number
  top: number
  bottom: number
  axisY: number
  legendY: number
  legendLeft: number
  legendRight: number
  xs: number[]
  dates: string[]
}

interface HitBox {
  left: number
  top: number
  width: number
  height: number
}

export function frameBounds({ dates }: ChartFrame): ChartPeriod {
  return { from: dates[0], to: dates[dates.length - 1] }
}

export function dateAtX({ xs, dates }: ChartFrame, x: number): string {
  const last = xs.length - 1
  if (x >= xs[last]) return dates[last]
  const at = Math.max(x, xs[0])
  const index = xs.findIndex(position => position > at) - 1
  const share = (at - xs[index]) / (xs[index + 1] - xs[index])
  return addDays(dates[index], Math.round(share * daysBetween(dates[index], dates[index + 1])))
}

export function xOfDate({ xs, dates }: ChartFrame, date: string): number {
  const last = dates.length - 1
  if (date <= dates[0]) return xs[0]
  if (date >= dates[last]) return xs[last]
  const index = dates.findIndex(sample => sample > date) - 1
  const share = daysBetween(dates[index], date) / daysBetween(dates[index], dates[index + 1])
  return xs[index] + share * (xs[index + 1] - xs[index])
}

const axisLabelY = (scale: Scale<CartesianScaleOptions>): number => {
  const { grid, ticks } = scale.options
  const tickLength = grid.drawTicks ? (grid.tickLength ?? 0) : 0
  const font = toFont(ticks.font as Partial<FontSpec>)
  return scale.top + tickLength + ticks.padding + font.lineHeight / 2
}

const legendRow = (chart: Chart) => {
  const boxes = (chart.legend as { legendHitBoxes?: HitBox[] } | undefined)?.legendHitBoxes ?? []
  const [first] = boxes
  if (!first) return { legendY: chart.height, legendLeft: 0, legendRight: 0 }
  const row = boxes.filter(box => box.top === first.top)
  return {
    legendY: first.top + first.height / 2,
    legendLeft: Math.min(...row.map(box => box.left)),
    legendRight: Math.max(...row.map(box => box.left + box.width)),
  }
}

export function readFrame(chart: Chart, dates: string[]): ChartFrame | null {
  const area = chart.chartArea
  if (!area) return null
  const scale = chart.scales.x as Scale<CartesianScaleOptions>
  return {
    width: chart.width,
    left: area.left,
    right: area.right,
    top: area.top,
    bottom: area.bottom,
    axisY: axisLabelY(scale),
    ...legendRow(chart),
    xs: dates.map((_, index) => scale.getPixelForValue(index)),
    dates,
  }
}
